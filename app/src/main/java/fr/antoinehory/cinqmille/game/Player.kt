package fr.antoinehory.cinqmille.game

/**
 * Represents a player in the Cinq Mille game.
 *
 * @property id The unique identifier for the player.
 * @property totalScore The player's current total score, accumulated across all turns. Mutable.
 * @property hasOpened True if the player has met the initial score requirement to "open" their score, false otherwise. Mutable.
 * @property lastKnownTurnScore Stores the score achieved by the player in their most recent completed turn.
 *                              This is particularly useful if this turn resulted in a win or to track turn performance. Mutable.
 */
data class Player(
    val id: Int,
    var totalScore: Int = 0,
    var hasOpened: Boolean = false,
    var lastKnownTurnScore: Int = 0
)
