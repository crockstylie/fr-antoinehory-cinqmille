package fr.antoinehory.cinqmille.ui.game

/**
 * Represents the UI-specific state for a single player.
 * This is a subset of the domain model `Player` (from fr.antoinehory.cinqmille.game.Player), 
 * tailored for display purposes.
 *
 * @property id The unique identifier of the player.
 * @property totalScore The total accumulated score of the player across all turns.
 * @property hasOpened True if the player has successfully met the opening score requirement, false otherwise.
 * @property isCurrentPlayer True if this player is the one whose turn it currently is, false otherwise.
 */
data class PlayerUiState(
    val id: Int,
    val totalScore: Int,
    val hasOpened: Boolean,
    val isCurrentPlayer: Boolean
)
