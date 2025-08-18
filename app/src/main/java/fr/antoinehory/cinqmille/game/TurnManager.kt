package fr.antoinehory.cinqmille.game

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
    private var latestRoll: DiceRoll = emptyList() // The most recent physical roll's outcome
    private var hasRolledThisSegment: Boolean = false // Tracks if player has rolled in the current scoring segment

    // Stores the dice that remain after a selection, for the next potential roll.
    // If empty, a full set (initialDiceCount) should be rolled.
    private var diceForNextRollAttempt: DiceRoll = emptyList()

    /**
     * Starts or continues a player's turn by rolling the dice.
     * If dice were previously selected and scored, this rolls the remaining dice (`diceForNextRollAttempt`).
     * If all dice were scored, or at the start of a turn segment, rolls a full set (`initialDiceCount`).
     *
     * @return A [TurnEvent.Rolled] event with the dice outcome and scoring possibility,
     *         or [TurnEvent.InvalidAction] if a roll is not currently allowed.
     */
    fun rollDice(): TurnEvent {
        // Basic check, might need more advanced logic from GameRules if there are complex conditions for re-rolling.
        if (hasRolledThisSegment && diceForNextRollAttempt.isEmpty() && currentTurnScore > 0) {
            // This implies player scored with all dice and *must* roll a full set.
            // If they scored with some dice, diceForNextRollAttempt would not be empty.
            // If hasRolledThisSegment is true but diceForNextRollAttempt is not empty, they can roll remaining.
        }

        val diceToRollCount = if (diceForNextRollAttempt.isNotEmpty()) {
            diceForNextRollAttempt.size
        } else {
            initialDiceCount // Start of turn, or all dice scored, roll full set
        }

        latestRoll = diceRoller.roll(diceToRollCount)
        hasRolledThisSegment = true
        // After rolling, the dice available for the next attempt are, by default, the ones just rolled.
        // This will be updated if the player makes a scoring selection.
        diceForNextRollAttempt = latestRoll

        val canScore = gameRules.canScoreFromRoll(latestRoll)
        if (!canScore) {
            currentTurnScore = 0 // Bust for the turn
            resetTurnStateAfterBustOrBank() // Reset dice for next player's turn
            return TurnEvent.Busted() // finalTurnScore defaults to 0
        }
        return TurnEvent.Rolled(latestRoll, true)
    }

    /**
     * Processes a player's selection of dice from the `latestRoll`.
     * Calculates the score for the selection and determines if the player can continue.
     *
     * @param selectedIndices A list of 0-based indices representing the dice selected from the `latestRoll`.
     * @return A [TurnEvent.Scored] if the selection is valid and scores points.
     *         A [TurnEvent.Busted] if the selection is invalid or scores zero.
     *         An [TurnEvent.InvalidAction] if no roll has been made or selection is otherwise disallowed.
     */
    fun selectDice(selectedIndices: List<Int>): TurnEvent {
        if (latestRoll.isEmpty() || !hasRolledThisSegment) {
            return TurnEvent.InvalidAction("Aucun dé n'a été lancé pour faire une sélection.")
        }
        if (selectedIndices.any { it < 0 || it >= latestRoll.size }) {
            return TurnEvent.InvalidAction("La sélection contient des indices de dés invalides.")
        }
        if (selectedIndices.isEmpty()) {
            return TurnEvent.InvalidAction("Aucun dé n'a été sélectionné.")
        }

        // Ensure distinct indices and map to dice values
        val distinctSelectedIndices = selectedIndices.distinct()
        val diceSelectedValues = distinctSelectedIndices.map { latestRoll[it] }.sorted()

        val scoreFromSelection = gameRules.calculateScore(diceSelectedValues)

        if (scoreFromSelection == 0) {
            currentTurnScore = 0 // Bust for the turn
            resetTurnStateAfterBustOrBank()
            return TurnEvent.Busted()
        }

        currentTurnScore += scoreFromSelection
        hasRolledThisSegment = false // A scoring selection completes this rolling segment. Player must roll again or bank.

        // Determine remaining dice in hand
        val remainingDiceInHandValues = latestRoll.filterIndexed { index, _ -> !distinctSelectedIndices.contains(index) }

        val canPlayerRollAgain: Boolean
        if (remainingDiceInHandValues.isEmpty()) {
            // All dice were part of the scoring selection (or no dice remained from the roll).
            // Player can roll a full set of initialDiceCount.
            diceForNextRollAttempt = emptyList() // Signal to roll a full set
            canPlayerRollAgain = true
        } else {
            // Some dice remain. Player can roll these.
            diceForNextRollAttempt = remainingDiceInHandValues
            canPlayerRollAgain = true // Assuming player can always roll remaining dice if they scored with others
        }

        // If player has not opened, check if this score allows opening
        if (!canPlayerOpen && currentTurnScore >= gameRules.openingScoreThreshold) {
            canPlayerOpen = true // Player is now open for this game
        }

        return TurnEvent.Scored(
            scoreFromSelection = scoreFromSelection,
            newTurnTotalScore = currentTurnScore,
            diceSelected = diceSelectedValues,
            remainingDiceInHand = remainingDiceInHandValues,
            canRollAgain = canPlayerRollAgain
        )
    }

    /**
     * Ends the current turn, banking the accumulated `currentTurnScore`.
     * The score is only banked if the player has met the opening requirement (if applicable).
     *
     * @return A [TurnEvent.TurnEndedBanked] with the final score for the turn,
     *         or [TurnEvent.InvalidAction] if banking is not allowed.
     */
    fun bankScore(): TurnEvent {
        if (!hasRolledThisSegment && currentTurnScore == 0 && latestRoll.isNotEmpty()) {
            // This means player rolled, could not select anything scoring (or didn't select), and tried to bank.
        }

        if (currentTurnScore == 0 && latestRoll.isNotEmpty()){
            // If player rolled, latestRoll is not empty. If currentTurnScore is 0, they busted on selection or didn't score.
        }


        if (!canPlayerOpen && currentTurnScore < gameRules.openingScoreThreshold) {
            currentTurnScore = 0 // Score for the turn is lost
            resetTurnStateAfterBustOrBank()
            return TurnEvent.TurnEndedBanked(0) // Banked 0 as failed to open.
        }

        if (!canPlayerOpen && currentTurnScore >= gameRules.openingScoreThreshold) {
            canPlayerOpen = true
        }

        if (!canPlayerOpen) {
            return TurnEvent.InvalidAction("Le score d'ouverture (${gameRules.openingScoreThreshold}) n'est pas atteint. Vous devez marquer au moins ${gameRules.openingScoreThreshold} en un tour pour ouvrir.")
        }

        val scoreToBank = currentTurnScore
        resetTurnStateAfterBustOrBank() // Prepare for next player's turn or game end
        return TurnEvent.TurnEndedBanked(scoreToBank)
    }

    /**
     * Gets the current accumulated score for this turn.
     */
    fun getCurrentTurnScore(): Int = currentTurnScore

    /**
     * Resets the turn-specific state after a bust or bank, preparing for the next turn/player.
     */
    private fun resetTurnStateAfterBustOrBank() {
        currentTurnScore = 0
        latestRoll = emptyList()
        hasRolledThisSegment = false
        diceForNextRollAttempt = emptyList()
    }
}
