package fr.antoinehory.cinqmille.ui.game

/**
 * Represents the complete state of the game screen's UI at a given moment.
 * This data class is used by the `GameViewModel` to expose observable state
 * to the Composable UI layer.
 *
 * @property players A list of [PlayerUiState] representing the state of each player in the game.
 * @property currentPlayerId The ID of the player whose turn it currently is. Null if the game is not active.
 * @property currentMessage A message to display to the user (e.g., instructions, game events).
 * @property currentDiceRoll The list of dice values from the most recent roll. An empty list if no roll has occurred.
 * @property accumulatedTurnScore The score accumulated by the current player from validated dice selections within the current turn.
 * @property previewSelectionScore The potential score from the dice currently visually selected by the player,
 *                                 but not yet validated. Used for live score preview.
 * @property isRollButtonEnabled True if the "Roll Dice" button should be enabled, false otherwise.
 * @property isBankButtonEnabled True if the "Bank Score" button should be enabled, false otherwise.
 * @property selectedDiceIndices DEPRECATED. Use [selectedDiceVisual] for UI interaction.
 * @property selectedDiceVisual A list of booleans indicating the visual selection state of each die.
 *                              This list should have the same size as [currentDiceRoll] or [INITIAL_DICE_COUNT].
 * @property scoredDiceMask A list of booleans indicating which dice have already scored in the current turn segment
 *                          and cannot be selected again.
 *                          This list should have the same size as [currentDiceRoll] or [INITIAL_DICE_COUNT].
 */
data class GameUiState(
    val players: List<PlayerUiState> = emptyList(),
    val currentPlayerId: Int? = null,
    val currentMessage: String = "Bienvenue au Cinq Mille ! Choisissez le nombre de joueurs pour commencer.",
    val currentDiceRoll: List<Int> = emptyList(),
    val accumulatedTurnScore: Int = 0,
    val previewSelectionScore: Int = 0,
    val isRollButtonEnabled: Boolean = false,
    val isBankButtonEnabled: Boolean = false,
    @Deprecated(
        message = "Use selectedDiceVisual for UI selection logic. This will be populated by the ViewModel based on validated selections or used to send final selections to game logic.",
        replaceWith = ReplaceWith("selectedDiceVisual")
    )
    val selectedDiceIndices: List<Int> = emptyList(),
    val selectedDiceVisual: List<Boolean> = List(INITIAL_DICE_COUNT) { false },
    val scoredDiceMask: List<Boolean> = List(INITIAL_DICE_COUNT) { false }
) {
    companion object {
        /**
         * The default number of dice to display or account for in UI lists.
         * This should be consistent with how dice are handled in the game logic and UI.
         * For Cinq Mille, this is typically 5.
         */
        const val INITIAL_DICE_COUNT = 5
    }
}