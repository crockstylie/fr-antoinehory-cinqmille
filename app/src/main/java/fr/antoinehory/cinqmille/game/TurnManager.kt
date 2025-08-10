package fr.antoinehory.cinqmille.game

// Représente les différents événements ou résultats possibles lors d'un tour de jeu.
sealed class TurnEvent {
    // Joueur a lancé les dés. 'dice' est le résultat, 'canPlayerMakeAnyScore' indique si ce lancer offre des points.
    data class Rolled(val dice: DiceRoll, val canPlayerMakeAnyScore: Boolean) : TurnEvent()
    // Joueur a marqué des points avec sa sélection.
    data class Scored(
        val scoreFromSelection: Int,          // Points de la sélection actuelle
        val newTurnTotalScore: Int,           // Score total accumulé dans ce tour
        val diceForNextPotentialRoll: Int,    // Nombre de dés pour le prochain lancer si le joueur continue
        val canRollAgain: Boolean             // Indique si le joueur a la possibilité de relancer
    ) : TurnEvent()
    // Le joueur a "busté" (aucun point marqué sur un lancer ou sélection non marquante).
    data class Busted(val finalTurnScore: Int = 0) : TurnEvent() // Le score d'un bust est toujours 0 pour le tour.
    // Le joueur a décidé d'arrêter et de garder son score.
    data class TurnEndedBanked(val finalTurnScore: Int) : TurnEvent()
    // Une action invalide a été tentée.
    data class InvalidAction(val message: String) : TurnEvent()
}

class TurnManager(
    private val scoreCalculator: ScoreCalculator,
    private val diceRoller: DiceRoller // DiceRoller injecté
) {

    var currentTurnScore: Int = 0
        private set
    var diceToRollCount: Int = MAX_DICE
        private set
    var latestRoll: DiceRoll = emptyList()
        private set

    // Indique si un tour est activement en cours (après le premier lancer et avant de banker/buster).
    private var turnInProgress: Boolean = false

    companion object {
        const val MAX_DICE = 6 // Nombre standard de dés pour le Cinq Mille.
    }

    /**
     * Commence un nouveau tour pour un joueur ou continue un tour existant en lançant les dés.
     * Si c'est le début d'un nouveau tour, réinitialise le score du tour et le nombre de dés.
     */
    fun startOrContinueRoll(): TurnEvent {
        if (!turnInProgress) { // C'est le premier lancer du tour pour ce joueur
            currentTurnScore = 0
            diceToRollCount = MAX_DICE
            turnInProgress = true
        }

        latestRoll = diceRoller.roll(diceToRollCount) // Utilise diceRoller.roll()

        // Si, pour une raison quelconque, le lancer est vide alors que des dés étaient attendus
        if (latestRoll.isEmpty() && diceToRollCount > 0) {
            turnInProgress = false // Termine le tour
            return TurnEvent.Busted() // Considérez cela comme un bust
        }

        val canScoreFromThisRoll = scoreCalculator.canScore(latestRoll)

        if (!canScoreFromThisRoll) {
            currentTurnScore = 0 // Le joueur bust, perd tout score accumulé dans ce tour.
            turnInProgress = false
            return TurnEvent.Busted()
        }

        return TurnEvent.Rolled(latestRoll, true)
    }

    /**
     * Traite la sélection de dés faite par le joueur après un lancer.
     * @param selectedIndices Les indices (0-basés) des dés que le joueur souhaite conserver depuis `latestRoll`.
     */
    fun processPlayerSelection(selectedIndices: List<Int>): TurnEvent {
        if (!turnInProgress) return TurnEvent.InvalidAction("Aucun tour en cours.")
        if (latestRoll.isEmpty()) return TurnEvent.InvalidAction("Aucun dé n'a été lancé pour cette sélection.")

        val keptDice = selectDiceFromRoll(latestRoll, selectedIndices)

        if (keptDice.isEmpty() && selectedIndices.isNotEmpty()) {
            return TurnEvent.InvalidAction("Indices de dés invalides, aucun dé conservé.")
        }
        
        if (keptDice.isEmpty()) {
            return TurnEvent.InvalidAction("Le joueur doit sélectionner des dés qui marquent des points.")
        }

        val scoreFromSelection = scoreCalculator.calculateScore(keptDice)

        if (scoreFromSelection == 0) {
            currentTurnScore = 0
            turnInProgress = false
            return TurnEvent.Busted()
        } else {
            currentTurnScore += scoreFromSelection
            
            val remainingDiceInHand = getRemainingDice(latestRoll, selectedIndices)
            
            diceToRollCount = if (remainingDiceInHand.isEmpty() && keptDice.isNotEmpty()) { // Added keptDice.isNotEmpty() for clarity: all dice scored
                MAX_DICE
            } else {
                remainingDiceInHand.size
            }
            
            return TurnEvent.Scored(scoreFromSelection, currentTurnScore, diceToRollCount, true)
        }
    }

    /**
     * Le joueur décide d'arrêter son tour et de conserver le score accumulé.
     */
    fun playerBanksScore(): TurnEvent {
        if (!turnInProgress) {
            return TurnEvent.InvalidAction("Impossible de banker: aucun tour en cours ou déjà terminé.")
        }
        val finalScoreForTurn = currentTurnScore
        turnInProgress = false 
        return TurnEvent.TurnEndedBanked(finalScoreForTurn)
    }
}
