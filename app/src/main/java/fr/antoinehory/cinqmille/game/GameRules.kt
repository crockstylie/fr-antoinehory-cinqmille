package fr.antoinehory.cinqmille.game

/**
 * Encapsulates specific game rules for the Cinq Mille game.
 * This class primarily defines thresholds and conditions that affect game flow.
 *
 * @property openingScoreThreshold The minimum score a player must achieve in a single turn
 *                                 to "open" their score, allowing them to start accumulating
 *                                 points in the game. Defaults to 500.
 */
class GameRules(
    val openingScoreThreshold: Int = 500
) {

    /**
     * Determines if a player is allowed to roll again after having scored in their current turn.
     * A player can roll again if:
     * 1. There are dice remaining that were not part of the scoring combination.
     * 2. All dice from the previous roll were used to score (a "full hand" score),
     *    which allows the player to roll all dice again.
     *
     * @param diceAvailableForNextRoll The dice that were not used for scoring in the last evaluated roll
     *                                 and are thus available for the next roll.
     * @param allDiceFromPreviousRollScored True if all dice from the most recent roll were used
     *                                      to score points, false otherwise.
     * @return True if the player can roll again, false otherwise.
     */
    fun canRollAfterScoring(diceAvailableForNextRoll: DiceRoll, allDiceFromPreviousRollScored: Boolean): Boolean {
        return diceAvailableForNextRoll.isNotEmpty() || allDiceFromPreviousRollScored
    }
}
