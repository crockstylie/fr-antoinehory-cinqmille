package fr.antoinehory.cinqmille.ui.game

// PlayerUiState is now in its own file.
// If GameUiState.kt previously imported fr.antoinehory.cinqmille.game.Player specifically for PlayerUiState's KDoc,
// that import might no longer be directly needed here unless used elsewhere in this file.

/**
 * Represents the complete state of the game screen's UI at a given moment.
 * This data class is used by the `GameViewModel` to expose observable state
 * to the Composable UI layer.
 *
 * @property players A list of [PlayerUiState] representing the state of each player in the game.
 * @property currentPlayerId The ID of the player whose turn it currently is. Null if the game is not active.
 * @property currentMessage A message to display to the user (e.g., instructions, game events).
 * @property currentDiceRoll The list of dice values from the most recent roll. An empty list if no roll has occurred.
 * @property currentTurnScore The score accumulated by the current player within the current turn.
 * @property isRollButtonEnabled True if the "Roll Dice" button should be enabled, false otherwise.
 * @property isBankButtonEnabled True if the "Bank Score" button should be enabled, false otherwise.
 * @property selectedDiceIndices DEPRECATED for UI interaction. This list was previously intended for direct UI manipulation.
 *                             It will now be primarily driven by the `GameViewModel` after processing `selectedDiceVisual`.
 *                             It might still be used to send the *final* validated selection to the game logic.
 * @property selectedDiceVisual A list of booleans indicating the visual selection state of each die in [currentDiceRoll].
 *                              `true` if the die at the corresponding index is visually selected by the user, `false` otherwise.
 *                              This list should have the same size as [currentDiceRoll].
 *                              Used for immediate UI feedback.
 */
data class GameUiState(
    val players: List<PlayerUiState> = emptyList(), // PlayerUiState will be imported from its new file
    val currentPlayerId: Int? = null,
    val currentMessage: String = "Bienvenue au Cinq Mille ! Choisissez le nombre de joueurs pour commencer.",
    val currentDiceRoll: List<Int> = emptyList(),
    val currentTurnScore: Int = 0,
    val isRollButtonEnabled: Boolean = false,
    val isBankButtonEnabled: Boolean = false,
    @Deprecated(
        message = "Use selectedDiceVisual for UI selection logic. This will be populated by the ViewModel based on validated selections or used to send final selections to game logic.",
        replaceWith = ReplaceWith("selectedDiceVisual") // Suggests what to look at for UI state
    )
    val selectedDiceIndices: List<Int> = emptyList(),
    val selectedDiceVisual: List<Boolean> = emptyList(), // Should be initialized to List(currentDiceRoll.size) { false } when dice are rolled
    // TODO: Ajouter d'autres états UI si nécessaire (par ex. pour l'animation des dés)
)

// PlayerUiState data class has been moved to PlayerUiState.kt
