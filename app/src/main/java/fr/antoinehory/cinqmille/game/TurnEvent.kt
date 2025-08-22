package fr.antoinehory.cinqmille.game

/**
 * Represents events that occur within a single player's turn, managed by [TurnManager].
 * These events detail the outcomes of player actions like rolling or selecting dice.
 */
sealed class TurnEvent {
    /**
     * Indicates that dice have been rolled (typically an initial roll or a roll after all dice scored).
     * @param dice The [DiceRoll] (list of all dice values, e.g., 5 or 6 dice) from the roll.
     * @param canPlayerMakeAnyScore True if the rolled dice can potentially score, false otherwise.
     */
    data class Rolled(val dice: DiceRoll, val canPlayerMakeAnyScore: Boolean) : TurnEvent()

    /**
     * Indicates that the player has made a valid selection of dice that scores points,
     * AND the remaining dice (if any and if rollable) have been automatically re-rolled.
     * This event provides the state AFTER that combined action.
     *
     * @param newTurnTotalScore The total accumulated score for the current turn *after* this selection and auto-roll.
     * @param diceStateAfterAction The complete [DiceRoll] to be displayed (e.g., 5 or 6 dice).
     *                             This includes dice that were part of the scoring selection (kept)
     *                             and the new values of dice that were re-rolled.
     * @param scoredDiceMask A list of booleans, same size as [diceStateAfterAction].
     *                       `true` at an index means the die at that position in [diceStateAfterAction]
     *                       was part of the scoring selection and was "kept".
     *                       `false` means it's a newly rolled die from this action or a die that wasn't part of the score.
     * @param canRollAgain True if the player has the option to make a new selection from [diceStateAfterAction]
     *                     and continue the turn. False if no more scoring dice can be selected or all dice scored.
     */
    data class Scored(
        val newTurnTotalScore: Int,
        val diceStateAfterAction: DiceRoll,
        val scoredDiceMask: List<Boolean>, // Mask for the dice in diceStateAfterAction
        val canRollAgain: Boolean
    ) : TurnEvent()

    /**
     * Indicates that the player's action (roll or selection) resulted in a "bust".
     * The player's current turn score is typically reset to 0 for the turn.
     * @param diceAtBust The [DiceRoll] (values of the dice) that caused the bust and should be displayed.
     * @param finalTurnScore The score of the turn when it busted. Per game rules, this is 0 for the turn.
     *                       Defaults to 0.
     */
    data class Busted(
        val diceAtBust: DiceRoll,
        val finalTurnScore: Int = 0 // The score of a bust is always 0 for the turn.
    ) : TurnEvent()

    /**
     * Indicates that the player has successfully banked their score for the turn.
     * The turn ends.
     * @param finalTurnScore The total score banked by the player in this turn.
     */
    data class TurnEndedBanked(
        val finalTurnScore: Int
    ) : TurnEvent()

    /**
     * Indicates that an action attempted by the player during their turn was invalid.
     * @param message A description of why the action was invalid.
     */
    data class InvalidAction(val message: String) : TurnEvent()
}
