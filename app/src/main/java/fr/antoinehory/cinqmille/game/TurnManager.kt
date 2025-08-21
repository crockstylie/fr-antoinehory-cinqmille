package fr.antoinehory.cinqmille.game

import fr.antoinehory.cinqmille.game.ScoreCalculator

// Les fonctions selectDiceFromRoll et getRemainingDice sont maintenant dans DiceRollUtils.kt
// Si DiceRollUtils.kt est dans le même package (fr.antoinehory.cinqmille.game),
// les appels directs devraient fonctionner sans import explicite des fonctions.
// Sinon, des imports comme :
// import fr.antoinehory.cinqmille.game.selectDiceFromRoll
// import fr.antoinehory.cinqmille.game.getRemainingDice
// seraient nécessaires. L'IDE devrait les gérer.

/**
 * Manages the state and logic for a single player's turn in the Cinq Mille game.
 * It handles dice rolling, selection, scoring, and determines when a turn ends
 * due to busting, banking, or achieving a winning condition.
 *
 * @property diceRoller The [DiceRoller] instance used to simulate dice rolls.
 * @property gameRules The [GameRules] defining scoring and game progression logic.
 * @property canPlayerOpen A flag indicating if the player has met the opening score requirement.
 *                         Used to determine if scores can be banked or if special opening rules apply.
 * @property initialDiceCount The number of dice to roll at the start of a turn or when all dice have scored.
 */
class TurnManager(
    private val diceRoller: DiceRoller,
    private val gameRules: GameRules = GameRules(),
    private var canPlayerOpen: Boolean,
    private val initialDiceCount: Int = 5
) {
    private var currentTurnScore: Int = 0
    var latestRoll: DiceRoll = emptyList()
        private set

    private var diceForNextRollAttempt: DiceRoll = emptyList()

    var hasRolledThisSegment: Boolean = false
        private set

    /**
     * Executes a dice roll.
     * The number of dice rolled is determined by `diceForNextRollAttempt` or `initialDiceCount`.
     *
     * @return A [TurnEvent.Rolled] or [TurnEvent.Busted].
     */
    fun rollDice(): TurnEvent {
        val diceToRollCount = if (diceForNextRollAttempt.isNotEmpty()) {
            diceForNextRollAttempt.size
        } else {
            initialDiceCount
        }

        if (diceToRollCount <= 0) {
            // Should ideally not happen if initialDiceCount > 0
            // Consider returning an InvalidAction or specific error event
        }

        val currentPhysicalRoll = diceRoller.roll(diceToRollCount)
        latestRoll = currentPhysicalRoll
        hasRolledThisSegment = true

        val canScore = ScoreCalculator.canScore(latestRoll)
        if (!canScore) {
            currentTurnScore = 0
            resetTurnStateAfterBustOrBank()
            return TurnEvent.Busted()
        }
        return TurnEvent.Rolled(latestRoll, true)
    }

    /**
     * Processes a player's selection of dice from the `latestRoll`.
     * Calculates score and updates dice for the next roll attempt using utility functions
     * from `DiceRollUtils.kt`.
     *
     * @param selectedIndices A list of 0-based indices for dice selected from `latestRoll`.
     * @return A [TurnEvent.Scored], [TurnEvent.Busted], or [TurnEvent.InvalidAction].
     */
    fun selectDice(selectedIndices: List<Int>): TurnEvent {
        if (!hasRolledThisSegment || latestRoll.isEmpty()) {
            return TurnEvent.InvalidAction("Aucun dé n'a été lancé pour faire une sélection, ou la sélection a déjà été traitée.")
        }
        if (selectedIndices.any { it < 0 || it >= latestRoll.size }) {
            return TurnEvent.InvalidAction("La sélection contient des indices de dés invalides.")
        }
        if (selectedIndices.isEmpty()) {
            return TurnEvent.InvalidAction("Aucun dé n'a été sélectionné.")
        }

        val distinctSelectedIndices = selectedIndices.distinct()

        // Utilisation des fonctions de DiceRollUtils.kt
        val diceSelectedValues = selectDiceFromRoll(latestRoll, distinctSelectedIndices).sorted()
        // Note: ScoreCalculator.calculateScore s'attend à une List<Int>, ce que selectDiceFromRoll retourne (via DiceRoll typealias).

        val scoreFromSelection = ScoreCalculator.calculateScore(diceSelectedValues)

        if (scoreFromSelection == 0) {
            currentTurnScore = 0
            resetTurnStateAfterBustOrBank()
            return TurnEvent.Busted()
        }

        currentTurnScore += scoreFromSelection

        // Utilisation des fonctions de DiceRollUtils.kt
        // Pour diceForNextRollAttempt, nous avons besoin des dés *restants* du *dernier lancer physique (latestRoll)*
        // après que les *diceSelectedValues* (qui viennent de distinctSelectedIndices par rapport à latestRoll) aient été mis de côté.
        // getRemainingDice prend les indices des dés gardés.
        // Nous voulons les dés qui n'ont PAS été sélectionnés à partir du *latestRoll*.
        // Les distinctSelectedIndices sont les indices des dés sélectionnés à partir de latestRoll.
        // Donc, les dés restants sont ceux de latestRoll qui ne sont pas dans distinctSelectedIndices.
        val remainingDiceInHandValues = getRemainingDice(latestRoll, distinctSelectedIndices)

        diceForNextRollAttempt = if (remainingDiceInHandValues.isEmpty() && scoreFromSelection > 0) {
            // Tous les dés ont marqué, le joueur relancera initialDiceCount
            emptyList()
        } else {
            remainingDiceInHandValues
        }

        hasRolledThisSegment = false

        if (!canPlayerOpen && currentTurnScore >= gameRules.openingScoreThreshold) {
            canPlayerOpen = true
        }

        val allDiceInLatestRollWereScored = diceForNextRollAttempt.isEmpty() // Si diceForNextRollAttempt est vide ici, cela signifie que tous les dés ont été utilisés pour marquer
        val canActuallyRollAgain = gameRules.canRollAfterScoring(
            diceAvailableForNextRoll = diceForNextRollAttempt,
            allDiceFromPreviousRollScored = allDiceInLatestRollWereScored
        )

        return TurnEvent.Scored(
            scoreFromSelection = scoreFromSelection,
            newTurnTotalScore = currentTurnScore,
            diceSelected = diceSelectedValues,
            remainingDiceInHand = diceForNextRollAttempt, // C'est diceForNextRollAttempt qui est pertinent pour le prochain lancer
            canRollAgain = canActuallyRollAgain
        )
    }

    /**
     * Attempts to bank the current turn's score.
     *
     * @return A [TurnEvent.TurnEndedBanked].
     */
    fun bankScore(): TurnEvent {
        if (!canPlayerOpen && currentTurnScore < gameRules.openingScoreThreshold) {
            currentTurnScore = 0
            resetTurnStateAfterBustOrBank()
            return TurnEvent.TurnEndedBanked(0)
        }

        if (!canPlayerOpen && currentTurnScore >= gameRules.openingScoreThreshold) {
            canPlayerOpen = true
        }

        val scoreToBank = currentTurnScore
        resetTurnStateAfterBustOrBank()
        return TurnEvent.TurnEndedBanked(scoreToBank)
    }

    /**
     * Gets the current accumulated score for this turn.
     * @return The current turn score.
     */
    fun getCurrentTurnScore(): Int = currentTurnScore

    /**
     * Resets the turn-specific state after a bust or a bank.
     * Clears current turn score, latest roll, roll segment flag, and dice for next roll.
     */
    private fun resetTurnStateAfterBustOrBank() {
        currentTurnScore = 0
        latestRoll = emptyList()
        hasRolledThisSegment = false
        diceForNextRollAttempt = emptyList()
    }
}
