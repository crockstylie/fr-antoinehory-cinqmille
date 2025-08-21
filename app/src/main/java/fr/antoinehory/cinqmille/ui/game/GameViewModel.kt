package fr.antoinehory.cinqmille.ui.game

// Removed: import androidx.compose.foundation.layout.size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.antoinehory.cinqmille.game.DefaultDiceRoller
import fr.antoinehory.cinqmille.game.GameManager
import fr.antoinehory.cinqmille.game.GameEvent
import fr.antoinehory.cinqmille.game.Player
import fr.antoinehory.cinqmille.game.TurnEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Make sure GameUiState and PlayerUiState are defined, for example in a separate GameUiState.kt file
// or at the end of this file, like so:
/*
data class GameUiState(
    val players: List<PlayerUiState> = emptyList(),
    val currentPlayerId: Int? = null,
    val currentMessage: String = "Bienvenue !",
    val isRollButtonEnabled: Boolean = false,
    val isBankButtonEnabled: Boolean = false,
    val currentDiceRoll: List<Int> = emptyList(),
    val currentTurnScore: Int = 0,
    val selectedDiceVisual: List<Boolean> = List(INITIAL_DICE_COUNT) { false },
    val scoredDiceMask: List<Boolean> = List(INITIAL_DICE_COUNT) { false } // Ensure this field exists
) {
    companion object {
        const val INITIAL_DICE_COUNT = 5 // Match GameScreen.INITIAL_DICE_DISPLAY_COUNT
    }
}

data class PlayerUiState(
    val id: Int,
    val totalScore: Int,
    val hasOpened: Boolean,
    val isCurrentPlayer: Boolean
)
*/

/**
 * ViewModel for the game screen, responsible for managing the UI state
 * and interacting with the [GameManager] to handle game logic.
 *
 * @property gameManager The instance of [GameManager] that handles the core game logic.
 */
class GameViewModel(private val gameManager: GameManager) : ViewModel() {

    /**
     * Secondary constructor for providing a default [GameManager] instance.
     * Useful for previews or when no specific [GameManager] is injected.
     * Assumes [DefaultDiceRoller] is a valid, importable class.
     */
    constructor() : this(GameManager(DefaultDiceRoller()))

    private val _uiState = MutableStateFlow(GameUiState())
    /**
     * The UI state for the game screen, observed by the composable functions.
     */
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /**
     * Stores the indices of the dice that were selected in the last attempt to score points.
     * This is used to correctly update the `scoredDiceMask` when a [TurnEvent.Scored] is received.
     * It is set before calling [GameManager.handleRollAction] with a selection and cleared afterwards.
     */
    private var lastSelectionAttemptIndices: List<Int>? = null

    /**
     * Starts a new game with the specified number of players.
     * It communicates with the [GameManager] to initialize the game
     * and updates the UI state based on the outcome.
     *
     * @param numberOfPlayers The number of players for the new game.
     */
    fun startGame(numberOfPlayers: Int) {
        viewModelScope.launch {
            val gameEvent = gameManager.startGame(numberOfPlayers)
            updateUiStateFromGameEvent(gameEvent)
        }
    }

    /**
     * Handles the dice roll action triggered by the user.
     * If dice are currently selected visually (and not yet scored), these selections are passed to the [GameManager].
     * Otherwise, a standard roll action (rolling all available dice for the current segment) is performed.
     * The UI state is updated based on the [GameEvent] received from the [GameManager].
     */
    fun rollDice() {
        viewModelScope.launch {
            if (_uiState.value.isRollButtonEnabled) {
                val currentRoll = _uiState.value.currentDiceRoll
                val currentScoredMask = _uiState.value.scoredDiceMask // Assumes GameUiState has this field
                val currentVisualSelection = _uiState.value.selectedDiceVisual

                val effectiveSelectedIndices: List<Int>? = if (currentRoll.isNotEmpty()) {
                    currentVisualSelection
                        .mapIndexedNotNull { index, isSelected ->
                            // Ensure currentScoredMask is List<Boolean>
                            if (isSelected && index < currentScoredMask.size && !currentScoredMask[index]) {
                                index
                            } else {
                                null
                            }
                        }
                        .takeIf { it.isNotEmpty() }
                } else {
                    null
                }
                lastSelectionAttemptIndices = effectiveSelectedIndices

                val gameEvent = gameManager.handleRollAction(effectiveSelectedIndices)
                updateUiStateFromGameEvent(gameEvent)
            }
        }
    }

    /**
     * Handles the action to bank the current turn's score, as triggered by the user.
     * It communicates with the [GameManager] to process the banking action
     * and updates the UI state based on the outcome.
     */
    fun bankScore() {
        viewModelScope.launch {
            if (_uiState.value.isBankButtonEnabled) {
                lastSelectionAttemptIndices = null
                val gameEvent = gameManager.currentTurnBankScore()
                updateUiStateFromGameEvent(gameEvent)
            }
        }
    }

    /**
     * Toggles the visual selection state of a die at the given index.
     * Selection is only allowed if the die is part of the current roll and not already marked as scored
     * (as indicated by the `scoredDiceMask`).
     * This directly updates the `selectedDiceVisual` in the [GameUiState].
     *
     * @param index The 0-based index of the die in the `currentDiceRoll` to toggle.
     */
    fun toggleDieSelection(index: Int) {
        val currentState = _uiState.value
        val currentVisualSelection = currentState.selectedDiceVisual
        val currentDice = currentState.currentDiceRoll
        val currentScoredMask = currentState.scoredDiceMask // Assumes GameUiState has this field

        // Ensure currentScoredMask is List<Boolean> and index is valid
        if (index >= 0 && index < currentDice.size &&
            index < currentVisualSelection.size &&
            index < currentScoredMask.size && !currentScoredMask[index]
        ) {
            val newVisualSelection = currentVisualSelection.toMutableList()
            newVisualSelection[index] = !newVisualSelection[index]
            _uiState.update { it.copy(selectedDiceVisual = newVisualSelection) }
        }
    }

    /**
     * Updates the [GameUiState] based on the received [GameEvent] from the [GameManager].
     * This function is central to translating game logic outcomes into UI changes.
     * It handles various game events such as game start, turn updates, player scoring, busting, and winning.
     *
     * @param gameEvent The [GameEvent] to process.
     */
    private fun updateUiStateFromGameEvent(gameEvent: GameEvent) {
        val currentState = _uiState.value
        // Use GameUiState.INITIAL_DICE_COUNT
        val initialDiceDisplayCount = GameUiState.INITIAL_DICE_COUNT

        when (gameEvent) {
            is GameEvent.GameStarted -> {
                _uiState.update {
                    it.copy(
                        players = mapPlayersToUiState(gameEvent.players, gameEvent.firstPlayer.id),
                        currentPlayerId = gameEvent.firstPlayer.id,
                        currentMessage = "Joueur ${gameEvent.firstPlayer.id}, à vous de commencer !",
                        isRollButtonEnabled = true,
                        isBankButtonEnabled = false,
                        currentDiceRoll = emptyList(),
                        currentTurnScore = 0,
                        selectedDiceVisual = List(initialDiceDisplayCount) { false },
                        scoredDiceMask = List(initialDiceDisplayCount) { false } // Assumes GameUiState has scoredDiceMask
                    )
                }
            }
            is GameEvent.PlayerTurnStarted -> {
                _uiState.update {
                    it.copy(
                        currentPlayerId = gameEvent.player.id,
                        currentMessage = "Au tour du Joueur ${gameEvent.player.id}. Lancez les dés !",
                        currentDiceRoll = emptyList(),
                        currentTurnScore = 0,
                        isRollButtonEnabled = true,
                        isBankButtonEnabled = false,
                        selectedDiceVisual = List(initialDiceDisplayCount) { false },
                        scoredDiceMask = List(initialDiceDisplayCount) { false }, // Assumes GameUiState has scoredDiceMask
                        players = mapPlayersToUiState(gameManager.allPlayers, gameEvent.player.id)
                    )
                }
            }
            is GameEvent.CurrentTurnUpdated -> {
                when (val turnEvent = gameEvent.turnEvent) {
                    is TurnEvent.Rolled -> {
                        _uiState.update {
                            it.copy(
                                currentDiceRoll = turnEvent.dice,
                                currentMessage = if (turnEvent.canPlayerMakeAnyScore) "Sélectionnez vos dés." else "Busté ! Tour terminé.",
                                isRollButtonEnabled = turnEvent.canPlayerMakeAnyScore,
                                isBankButtonEnabled = false,
                                selectedDiceVisual = List(turnEvent.dice.size) { false }, // .size is correct for List
                                scoredDiceMask = List(turnEvent.dice.size) { false } // .size is correct for List
                            )
                        }
                    }
                    is TurnEvent.Scored -> {
                        // currentDiceRoll from currentState is the roll *from which* the selection was made.
                        val newScoredMask = currentState.scoredDiceMask.toMutableList()
                        lastSelectionAttemptIndices?.forEach { indexScored ->
                            if (indexScored >= 0 && indexScored < currentState.currentDiceRoll.size && indexScored < newScoredMask.size) {
                                newScoredMask[indexScored] = true
                            }
                        }

                        _uiState.update {
                            it.copy(
                                currentTurnScore = turnEvent.newTurnTotalScore,
                                currentMessage = "Score ce tour : ${turnEvent.newTurnTotalScore}. Relancez ou banquez.",
                                isRollButtonEnabled = turnEvent.canRollAgain,
                                isBankButtonEnabled = true,
                                selectedDiceVisual = List(it.currentDiceRoll.size) { false }, // Reset visual for next action
                                scoredDiceMask = newScoredMask
                            )
                        }
                    }
                    is TurnEvent.Busted -> {
                        _uiState.update {
                            it.copy(
                                currentMessage = "Busté ! Votre tour est terminé.",
                                isRollButtonEnabled = false,
                                isBankButtonEnabled = false,
                                selectedDiceVisual = List(it.currentDiceRoll.size) { false }, // Clear selection on bust
                                scoredDiceMask = List(it.currentDiceRoll.size) { false }    // Clear mask on bust
                            )
                        }
                    }
                    is TurnEvent.TurnEndedBanked -> {
                        _uiState.update {
                            it.copy(
                                currentMessage = "Score banqué. Préparez le prochain joueur.",
                                isRollButtonEnabled = false,
                                isBankButtonEnabled = false,
                                selectedDiceVisual = List(initialDiceDisplayCount) { false },
                                scoredDiceMask = List(initialDiceDisplayCount) { false }
                            )
                        }
                    }
                    is TurnEvent.InvalidAction -> {
                        _uiState.update {
                            it.copy(currentMessage = "Action invalide: ${turnEvent.message}")
                        }
                    }
                }
                lastSelectionAttemptIndices = null
            }
            is GameEvent.PlayerScored -> {
                val nextPlayerId = getNextPlayerId(currentState.currentPlayerId, gameManager.allPlayers)
                _uiState.update {
                    it.copy(
                        players = mapPlayersToUiState(gameManager.allPlayers, nextPlayerId),
                        currentPlayerId = nextPlayerId,
                        currentMessage = "Joueur ${gameEvent.player.id} a marqué ${gameEvent.scoreThisTurn}. Total: ${gameEvent.newTotalScore}. Au tour du Joueur $nextPlayerId.",
                        currentDiceRoll = emptyList(),
                        currentTurnScore = 0,
                        isRollButtonEnabled = true,
                        isBankButtonEnabled = false,
                        selectedDiceVisual = List(initialDiceDisplayCount) { false },
                        scoredDiceMask = List(initialDiceDisplayCount) { false }
                    )
                }
            }
            is GameEvent.PlayerOpenedAndScored -> {
                val nextPlayerId = getNextPlayerId(currentState.currentPlayerId, gameManager.allPlayers)
                _uiState.update {
                    it.copy(
                        players = mapPlayersToUiState(gameManager.allPlayers, nextPlayerId),
                        currentPlayerId = nextPlayerId,
                        currentMessage = "Joueur ${gameEvent.player.id} a ouvert avec ${gameEvent.scoreThisTurn}! Total: ${gameEvent.newTotalScore}. Au tour du Joueur $nextPlayerId.",
                        currentDiceRoll = emptyList(),
                        currentTurnScore = 0,
                        isRollButtonEnabled = true,
                        isBankButtonEnabled = false,
                        selectedDiceVisual = List(initialDiceDisplayCount) { false },
                        scoredDiceMask = List(initialDiceDisplayCount) { false }
                    )
                }
            }
            is GameEvent.PlayerFailedToOpen -> {
                val nextPlayerId = getNextPlayerId(currentState.currentPlayerId, gameManager.allPlayers)
                _uiState.update {
                    it.copy(
                        players = mapPlayersToUiState(gameManager.allPlayers, nextPlayerId),
                        currentPlayerId = nextPlayerId,
                        currentMessage = "Joueur ${gameEvent.player.id} n'a pas pu ouvrir (score ${gameEvent.scoreAttemptedThisTurn}). Au tour du Joueur $nextPlayerId.",
                        currentDiceRoll = emptyList(),
                        currentTurnScore = 0,
                        isRollButtonEnabled = true,
                        isBankButtonEnabled = false,
                        selectedDiceVisual = List(initialDiceDisplayCount) { false },
                        scoredDiceMask = List(initialDiceDisplayCount) { false }
                    )
                }
            }
            is GameEvent.PlayerBusted -> {
                val nextPlayerId = getNextPlayerId(currentState.currentPlayerId, gameManager.allPlayers)
                _uiState.update {
                    it.copy(
                        players = mapPlayersToUiState(gameManager.allPlayers, nextPlayerId),
                        currentPlayerId = nextPlayerId,
                        currentMessage = "Joueur ${gameEvent.player.id} a busté ! Au tour du Joueur $nextPlayerId.",
                        currentDiceRoll = emptyList(),
                        currentTurnScore = 0,
                        isRollButtonEnabled = true,
                        isBankButtonEnabled = false,
                        selectedDiceVisual = List(initialDiceDisplayCount) { false },
                        scoredDiceMask = List(initialDiceDisplayCount) { false }
                    )
                }
            }
            is GameEvent.PlayerWon -> {
                _uiState.update {
                    it.copy(
                        players = mapPlayersToUiState(gameManager.allPlayers, null),
                        currentPlayerId = null,
                        currentMessage = "Joueur ${gameEvent.winner.id} a gagné avec ${gameEvent.finalScore} points ! Partie terminée.",
                        isRollButtonEnabled = false,
                        isBankButtonEnabled = false,
                        currentDiceRoll = emptyList(),
                        selectedDiceVisual = emptyList(),
                        scoredDiceMask = emptyList()
                    )
                }
            }
            is GameEvent.InvalidGameAction -> {
                _uiState.update {
                    it.copy(currentMessage = gameEvent.message)
                }
            }
        }
    }

    /**
     * Maps a list of [Player] domain models to a list of [PlayerUiState] objects
     * suitable for display in the UI.
     *
     * @param playersList The list of [Player] objects from the game logic.
     * @param currentPlayingId The ID of the player whose turn it currently is, or null if no player is active.
     * @return A list of [PlayerUiState] objects.
     */
    private fun mapPlayersToUiState(playersList: List<Player>, currentPlayingId: Int?): List<PlayerUiState> {
        return playersList.map { player ->
            PlayerUiState(
                id = player.id,
                totalScore = player.totalScore,
                hasOpened = player.hasOpened,
                isCurrentPlayer = player.id == currentPlayingId
            )
        }
    }

    /**
     * Determines the ID of the next player.
     *
     * @param currentId The ID of the current player.
     * @param playersList The list of all players in the game.
     * @return The ID of the next player, or null if it cannot be determined (e.g., game over, no players).
     */
    private fun getNextPlayerId(currentId: Int?, playersList: List<Player>): Int? {
        if (currentId == null) return null
        if (playersList.isEmpty()) return null
        val currentIndex = playersList.indexOfFirst { it.id == currentId }
        if (currentIndex == -1) return null
        return playersList[(currentIndex + 1) % playersList.size].id
    }
}

/**
 * Factory for creating [GameViewModel] instances.
 * This is used by the system to instantiate the ViewModel, especially when constructor arguments are needed.
 *
 * @property gameManager The [GameManager] instance to be injected into the [GameViewModel].
 */
@Suppress("UNCHECKED_CAST")
class GameViewModelFactory(private val gameManager: GameManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            return GameViewModel(gameManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
