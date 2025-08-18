package fr.antoinehory.cinqmille.game // Assure-toi que c'est le package de ton fichier TurnEvent.kt

// Il est possible que DiceRoll soit défini dans GameTurn.kt ou TurnManager.kt
// S'il n'est pas accessible, il faudra peut-être l'importer ou le déplacer aussi.
// Normalement, s'il est dans le même package, pas de souci.
// typealias DiceRoll = List<Int> // Au cas où, pour rappel

/**
 * Represents events that occur within a single player's turn, managed by [TurnManager].
 * These events detail the outcomes of player actions like rolling or selecting dice.
 */
sealed class TurnEvent {
    /**
     * Indicates that dice have been rolled.
     * @param dice The [DiceRoll] (list of dice values) from the roll.
     * @param canPlayerMakeAnyScore True if the rolled dice can potentially score, false otherwise.
     */
    data class Rolled(val dice: DiceRoll, val canPlayerMakeAnyScore: Boolean) : TurnEvent()

    /**
     * Indicates that the player has made a valid selection of dice that scores points.
     * @param scoreFromSelection The score obtained from this specific selection.
     * @param newTurnTotalScore The total accumulated score for the current turn *after* this selection.
     * @param diceForNextPotentialRoll The number of dice available for the player to roll again.
     * @param canRollAgain True if the player has the option to roll again, false otherwise.
     */
    data class Scored(
        val scoreFromSelection: Int,
        val newTurnTotalScore: Int,
        val diceForNextPotentialRoll: Int,
        val canRollAgain: Boolean
    ) : TurnEvent()

    /**
     * Indicates that the player's action (roll or selection) resulted in a "bust".
     * The player's current turn score is typically reset.
     * @param finalTurnScore The score of the turn when it busted. Per game rules, this is 0 for the turn.
     *                       The default value is 0.
     */
    data class Busted(val finalTurnScore: Int = 0) : TurnEvent() // Commentaire original: Le score d'un bust est toujours 0 pour le tour.

    /**
     * Indicates that the player has successfully banked their score for the turn.
     * The turn ends.
     * @param finalTurnScore The total score banked by the player in this turn.
     */
    data class TurnEndedBanked(val finalTurnScore: Int) : TurnEvent()

    /**
     * Indicates that an action attempted by the player during their turn was invalid.
     * @param message A description of why the action was invalid.
     */
    data class InvalidAction(val message: String) : TurnEvent()
}
