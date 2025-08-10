package fr.antoinehory.cinqmille.ui.game

import fr.antoinehory.cinqmille.game.DiceRoll
import fr.antoinehory.cinqmille.game.Player

/**
 * Represents the complete state of the game screen's UI at a given moment.
 * This data class is used by the [GameViewModel] to expose observable state
 * to the Composable UI layer.
 *
 * @property currentDiceRoll The list of dice values from the most recent roll.
 * @property currentTurnScore The score accumulated by the current player within the current turn.
 * @property players A list of [PlayerUiState] representing the state of each player in the game.
 * @property currentPlayerId The ID of the player whose turn it currently is. Null if the game is not active.
 * @property currentMessage A message to display to the user (e.g., instructions, game events).
 * @property isRollButtonEnabled True if the "Roll Dice" button should be enabled, false otherwise.
 * @property isBankButtonEnabled True if the "Bank Score" button should be enabled, false otherwise.
 * @property selectedDiceIndices A list of indices for dice that the user has currently selected from the [currentDiceRoll].
 */
data class GameUiState(
    val currentDiceRoll: DiceRoll = emptyList(),
    val currentTurnScore: Int = 0,
    val players: List<PlayerUiState> = emptyList(),
    val currentPlayerId: Int? = null,
    val currentMessage: String = "Bienvenue au Cinq Mille !",
    val isRollButtonEnabled: Boolean = true,
    val isBankButtonEnabled: Boolean = false,
    val selectedDiceIndices: List<Int> = emptyList()
    // TODO: Ajouter d'autres états UI si nécessaire (par ex. pour l'animation des dés)
)

/**
 * Represents the UI-specific state for a single player.
 * This is a subset of the [Player] domain model, tailored for display purposes.
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
