package fr.antoinehory.cinqmille.ui.game

// import android.util.Log // Commented out as it was causing test issues
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.antoinehory.cinqmille.game.DefaultDiceRoller
import fr.antoinehory.cinqmille.game.GameEvent
import fr.antoinehory.cinqmille.game.GameManager
import fr.antoinehory.cinqmille.game.Player
import fr.antoinehory.cinqmille.game.TurnEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the game screen.
 *
 * This ViewModel acts as a bridge between the game logic encapsulated in [GameManager]
 * and the UI layer ([GameScreen]). It holds and updates the [GameUiState] which
 * the UI observes to render the game. It also translates UI actions into calls
 * to the [GameManager] and processes [GameEvent]s received from it.
 *
 * @property gameManager The instance of [GameManager] that handles the core game logic.
 */
class GameViewModel(private val gameManager: GameManager = GameManager(DefaultDiceRoller())) : ViewModel() {

    /**
     * The number of dice to display in the UI, typically fetched from [GameUiState.INITIAL_DICE_COUNT].
     */
    private val initialDiceDisplayCount = GameUiState.INITIAL_DICE_COUNT

    /**
     * The private mutable [StateFlow] for the [GameUiState].
     * Internal updates to the UI state are made through this flow.
     */
    private val _uiState = MutableStateFlow(GameUiState())
    /**
     * The public, immutable [StateFlow] representing the current UI state of the game.
     * The UI layer observes this flow for updates.
     */
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /**
     * Stores the indices of the dice that the player is attempting to select or has selected.
     * This is used by [toggleDieSelection] to build the selection before sending it to the game logic
     * or updating the preview score. It's nullable to represent no current selection attempt.
     */
    private var lastSelectionAttemptIndices: MutableList<Int>? = null

    /**
     * Caches the previous UI state before an update. This can be useful for comparing
     * state changes or reverting to a prior state if needed, though its primary use here
     * is within [processGameEvent] to access state before it's updated by the event.
     */
    private var previousUiState: GameUiState = _uiState.value // Initialize with current

    /**
     * Starts a new game with the specified number of players.
     * It calls the [GameManager] to initialize the game and then processes the
     * [GameEvent.GameStarted] event to update the UI state.
     *
     * @param numberOfPlayers The number of players for the new game.
     */
    fun startGame(numberOfPlayers: Int) {
        val gameStartEvent = gameManager.startGame(numberOfPlayers)
        processGameEvent(gameStartEvent)
    }

    /**
     * Handles the player's action to roll the dice.
     * This can be an initial roll, a roll with all available dice, or a roll after selecting
     * and scoring some dice. It calls the [GameManager] to perform the roll action
     * and processes the resulting [GameEvent] to update the UI.
     *
     * If there was a pending visual selection ([lastSelectionAttemptIndices]), it's passed
     * to the [GameManager]. Otherwise, it's a general roll.
     */
    fun rollDice() {
        val event = gameManager.handleRollAction(lastSelectionAttemptIndices?.toList())
        processGameEvent(event)
        lastSelectionAttemptIndices = null // Clear selection after roll action
        updatePreviewScoreOnAction() // Reset preview score
    }

    /**
     * Toggles the selection state of a die at the given [index].
     * It updates the visual selection state in [GameUiState.selectedDiceVisual] and
     * then calls [updatePreviewScore] to reflect the potential score of this new selection.
     *
     * @param index The 0-based index of the die to toggle.
     */
    fun toggleDieSelection(index: Int) {
        _uiState.update { currentState ->
            val newSelectedDiceVisual = currentState.selectedDiceVisual.toMutableList()
            // Can only select if the die is not already part of a scored segment in this turn
            if (index < newSelectedDiceVisual.size && !currentState.scoredDiceMask.getOrElse(index) { true }) {
                newSelectedDiceVisual[index] = !newSelectedDiceVisual[index]
            }

            // Update lastSelectionAttemptIndices based on the new visual selection
            // This is a side-effect on a ViewModel property.
            this.lastSelectionAttemptIndices = newSelectedDiceVisual
                .mapIndexedNotNull { idx, selected -> if (selected) idx else null }
                .toMutableList()

            // Calculate preview score based on the new selection visual
            val diceForPreview = mutableListOf<Int>()
            newSelectedDiceVisual.forEachIndexed { currentDieIndex, isSelected ->
                if (isSelected &&
                    currentDieIndex < currentState.currentDiceRoll.size &&
                    !currentState.scoredDiceMask.getOrElse(currentDieIndex) { false }
                ) {
                    diceForPreview.add(currentState.currentDiceRoll[currentDieIndex])
                }
            }
            val newPreviewScore = if (diceForPreview.isNotEmpty()) {
                gameManager.calculatePreviewScore(diceForPreview)
            } else {
                0
            }

            currentState.copy(
                selectedDiceVisual = newSelectedDiceVisual.toList(), // Make it immutable for the state
                previewSelectionScore = newPreviewScore
            )
        }
    }

    /**
     * Handles the player's action to bank their current turn score.
     * It calls the [GameManager] to perform the bank action, potentially with a final
     * selection of dice ([lastSelectionAttemptIndices]) if the player selected dice
     * just before banking. The resulting [GameEvent] is processed to update the UI.
     */
    fun bankScore() {
        val event = gameManager.currentTurnBankScore(lastSelectionAttemptIndices?.toList())
        processGameEvent(event)
        lastSelectionAttemptIndices = null // Clear selection after bank action
        updatePreviewScoreOnAction() // Reset preview score
    }

    /**
     * Updates the [GameUiState.previewSelectionScore] to 0, typically after an action like roll or bank.
     * The preview is for selections *before* an action.
     */
    private fun updatePreviewScoreOnAction() {
        _uiState.update { currentState ->
            currentState.copy(previewSelectionScore = 0)
        }
    }


    /**
     * Processes a [GameEvent] received from the [GameManager] and updates the [GameUiState] accordingly.
     * This function is the central point for reacting to game logic changes and reflecting them in the UI.
     *
     * @param gameEvent The [GameEvent] to process.
     */
    private fun processGameEvent(gameEvent: GameEvent) {
        // Log.d("GameVM_Event", "Received GameEvent: ${gameEvent::class.simpleName}, Data: $gameEvent")
        previousUiState = _uiState.value // Cache state before update

        when (gameEvent) {
            is GameEvent.GameStarted -> {
                _uiState.update {
                    GameUiState( // Reset to a new game state
                        players = mapPlayersToUiState(gameEvent.players, gameEvent.firstPlayer.id),
                        currentPlayerId = gameEvent.firstPlayer.id,
                        currentMessage = "Joueur ${gameEvent.firstPlayer.id}, à vous de commencer !",
                        isRollButtonEnabled = true,
                        isBankButtonEnabled = false, // Initially false
                        currentDiceRoll = List(initialDiceDisplayCount) { 0 },
                        accumulatedTurnScore = 0,
                        previewSelectionScore = 0,
                        selectedDiceVisual = List(initialDiceDisplayCount) { false },
                        scoredDiceMask = List(initialDiceDisplayCount) { false }
                    )
                }
            }

            is GameEvent.PlayerTurnStarted -> {
                _uiState.update {
                    it.copy(
                        currentPlayerId = gameEvent.player.id,
                        currentMessage = "Au tour du Joueur ${gameEvent.player.id}. Lancez les dés !",
                        currentDiceRoll = List(initialDiceDisplayCount) { 0 },
                        accumulatedTurnScore = 0,
                        previewSelectionScore = 0,
                        isRollButtonEnabled = true,
                        isBankButtonEnabled = false, // Reset for new turn
                        selectedDiceVisual = List(initialDiceDisplayCount) { false },
                        scoredDiceMask = List(initialDiceDisplayCount) { false },
                        players = mapPlayersToUiState(gameManager.allPlayers, gameEvent.player.id)
                    )
                }
            }

            is GameEvent.CurrentTurnUpdated -> {
                // Log.d("GameVM_Update", "  TurnEvent: ${gameEvent.turnEvent::class.simpleName}, Data: ${gameEvent.turnEvent}")
                when (val turnEvent = gameEvent.turnEvent) {
                    is TurnEvent.Rolled -> {
                        _uiState.update {
                            it.copy(
                                currentDiceRoll = resizeSafelyList(turnEvent.dice, initialDiceDisplayCount, 0),
                                currentMessage = if (turnEvent.canPlayerMakeAnyScore) "Sélectionnez vos dés."
                                else "Busté sur ce lancer ! Tour terminé ou essayez de banquer si score précédent.",
                                isRollButtonEnabled = turnEvent.canPlayerMakeAnyScore,
                                isBankButtonEnabled = it.accumulatedTurnScore > 0, // Bank button enabled if accumulated score > 0
                                selectedDiceVisual = List(initialDiceDisplayCount) { false }, // Clear visual selection after roll
                                scoredDiceMask = resizeSafelyList(emptyList(), initialDiceDisplayCount, false), // Clear scored mask on new roll in segment
                                previewSelectionScore = 0, // Clear preview score, handled by updatePreviewScoreOnAction already but safe
                            )
                        }
                    }
                    is TurnEvent.Scored -> {
                        _uiState.update {
                            it.copy(
                                currentDiceRoll = resizeSafelyList(turnEvent.diceStateAfterAction, initialDiceDisplayCount, 0),
                                scoredDiceMask = resizeSafelyList(turnEvent.scoredDiceMask, initialDiceDisplayCount, false),
                                accumulatedTurnScore = turnEvent.newTurnTotalScore, // This is the total for the turn from TurnManager
                                previewSelectionScore = 0, // Clear preview after scoring, handled by updatePreviewScoreOnAction already
                                currentMessage = "Score ce tour : ${turnEvent.newTurnTotalScore}. Relancez ou banquez.",
                                isRollButtonEnabled = turnEvent.canRollAgain,
                                isBankButtonEnabled = turnEvent.newTurnTotalScore > 0, // Enabled if there's score
                                selectedDiceVisual = List(initialDiceDisplayCount) { false } // Clear visual selection
                            )
                        }
                    }
                    is TurnEvent.Busted -> {
                        _uiState.update {
                            it.copy(
                                currentDiceRoll = resizeSafelyList(turnEvent.diceAtBust, initialDiceDisplayCount, 0),
                                currentMessage = "Busté ! Votre tour est terminé.",
                                isRollButtonEnabled = false,
                                isBankButtonEnabled = false, // Cannot bank after a bust
                                selectedDiceVisual = List(initialDiceDisplayCount) { false },
                                scoredDiceMask = List(initialDiceDisplayCount) { true }, // Visually indicate all dice involved in bust
                                accumulatedTurnScore = 0, // Score for the turn is lost
                                previewSelectionScore = 0
                            )
                        }
                    }
                    is TurnEvent.InvalidAction -> {
                        _uiState.update {
                            it.copy(currentMessage = "Action invalide: ${turnEvent.message}")
                        }
                    }
                    is TurnEvent.TurnEndedBanked -> {
                        _uiState.update {
                            it.copy(
                                currentMessage = "Score banqué: ${turnEvent.finalTurnScore}. Au prochain joueur.",
                                isRollButtonEnabled = false, // Buttons for next player will be set by PlayerTurnStarted
                                isBankButtonEnabled = false,
                                previewSelectionScore = 0
                                // accumulatedTurnScore is reset for the next player by PlayerTurnStarted
                            )
                        }
                    }
                }
                lastSelectionAttemptIndices = null // Clear any pending selection after turn event processed
            }

            is GameEvent.PlayerScored -> {
                val nextPlayerId = getNextPlayerId(previousUiState.currentPlayerId, gameManager.allPlayers)
                _uiState.update {
                    it.copy(
                        players = mapPlayersToUiState(gameManager.allPlayers, nextPlayerId),
                        currentPlayerId = nextPlayerId,
                        currentMessage = "Joueur ${gameEvent.player.id} a marqué ${gameEvent.scoreThisTurn} (Total: ${gameEvent.newTotalScore}). Au tour du Joueur $nextPlayerId.",
                        currentDiceRoll = List(initialDiceDisplayCount) { 0 },
                        accumulatedTurnScore = 0,
                        previewSelectionScore = 0,
                        isRollButtonEnabled = true, // For next player
                        isBankButtonEnabled = false, // For next player
                        selectedDiceVisual = List(initialDiceDisplayCount) { false },
                        scoredDiceMask = List(initialDiceDisplayCount) { false }
                    )
                }
            }
            is GameEvent.PlayerOpenedAndScored -> {
                val nextPlayerId = getNextPlayerId(previousUiState.currentPlayerId, gameManager.allPlayers)
                _uiState.update {
                    it.copy(
                        players = mapPlayersToUiState(gameManager.allPlayers, nextPlayerId),
                        currentPlayerId = nextPlayerId,
                        currentMessage = "Joueur ${gameEvent.player.id} a ouvert avec ${gameEvent.scoreThisTurn} (Total: ${gameEvent.newTotalScore}) ! Au tour du Joueur $nextPlayerId.",
                        currentDiceRoll = List(initialDiceDisplayCount) { 0 },
                        accumulatedTurnScore = 0,
                        previewSelectionScore = 0,
                        isRollButtonEnabled = true, // For next player
                        isBankButtonEnabled = false, // For next player
                        selectedDiceVisual = List(initialDiceDisplayCount) { false },
                        scoredDiceMask = List(initialDiceDisplayCount) { false }
                    )
                }
            }
            is GameEvent.PlayerFailedToOpen -> {
                val nextPlayerId = getNextPlayerId(previousUiState.currentPlayerId, gameManager.allPlayers)
                _uiState.update {
                    it.copy(
                        players = mapPlayersToUiState(gameManager.allPlayers, nextPlayerId),
                        currentPlayerId = nextPlayerId,
                        currentMessage = "Joueur ${gameEvent.player.id} n'a pas pu ouvrir (score tenté: ${gameEvent.scoreAttemptedThisTurn}). Au tour du Joueur $nextPlayerId.",
                        currentDiceRoll = List(initialDiceDisplayCount) { 0 },
                        accumulatedTurnScore = 0,
                        previewSelectionScore = 0,
                        isRollButtonEnabled = true, // For next player
                        isBankButtonEnabled = false, // For next player
                        selectedDiceVisual = List(initialDiceDisplayCount) { false },
                        scoredDiceMask = List(initialDiceDisplayCount) { false }
                    )
                }
            }
            is GameEvent.PlayerBusted -> {
                val nextPlayerId = getNextPlayerId(previousUiState.currentPlayerId, gameManager.allPlayers)
                _uiState.update {
                    it.copy(
                        players = mapPlayersToUiState(gameManager.allPlayers, nextPlayerId),
                        currentPlayerId = nextPlayerId,
                        // Display dice at bust, then clear for next player by PlayerTurnStarted if not already handled
                        currentDiceRoll = resizeSafelyList(gameEvent.diceAtBust, initialDiceDisplayCount, 0),
                        currentMessage = "Joueur ${gameEvent.player.id} a busté ! Dés: ${gameEvent.diceAtBust.joinToString()}. Au tour du Joueur $nextPlayerId.",
                        accumulatedTurnScore = 0, // Reset for the UI
                        previewSelectionScore = 0,
                        isRollButtonEnabled = true, // For next player
                        isBankButtonEnabled = false, // For next player
                        selectedDiceVisual = List(initialDiceDisplayCount) { false },
                        scoredDiceMask = List(initialDiceDisplayCount) { true } // Show bust dice visually, then reset by PlayerTurnStarted
                    )
                }
            }
            is GameEvent.PlayerWon -> {
                _uiState.update {
                    it.copy(
                        players = mapPlayersToUiState(gameManager.allPlayers, null),
                        currentPlayerId = null, // No current player as game ended
                        currentMessage = "Joueur ${gameEvent.winner.id} a gagné avec ${gameEvent.finalScore} points ! Partie terminée.",
                        isRollButtonEnabled = false,
                        isBankButtonEnabled = false,
                        currentDiceRoll = List(initialDiceDisplayCount) { 0 },
                        accumulatedTurnScore = 0,
                        previewSelectionScore = 0,
                        selectedDiceVisual = List(initialDiceDisplayCount) { false },
                        scoredDiceMask = List(initialDiceDisplayCount) { false }
                    )
                }
            }
            is GameEvent.InvalidGameAction -> {
                _uiState.update {
                    it.copy(currentMessage = "Action de jeu invalide: ${gameEvent.message}")
                }
            }
        }
    }

    /**
     * Maps a list of [Player] domain objects to a list of [PlayerUiState] objects
     * suitable for UI display.
     *
     * @param playersList The list of [Player] domain models.
     * @param currentPlayingId The ID of the player whose turn it currently is, or null if game is over.
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
     * Determines the ID of the next player in sequence.
     *
     * @param currentId The ID of the current player. Can be null if game hasn't started or just ended.
     * @param playersList The list of all players in the game.
     * @return The ID of the next player. Returns the first player if currentId is null but list is not empty.
     *         Returns null if playersList is empty.
     */
    private fun getNextPlayerId(currentId: Int?, playersList: List<Player>): Int? {
        if (playersList.isEmpty()) return null
        if (currentId == null) return playersList.firstOrNull()?.id // If no current player (e.g. game end), or for safety

        val currentIndex = playersList.indexOfFirst { it.id == currentId }
        return if (currentIndex != -1) {
            playersList[(currentIndex + 1) % playersList.size].id
        } else {
            playersList.firstOrNull()?.id // Fallback: should not happen if currentId is valid
        }
    }

    /**
     * Helper function to safely resize a list to the target display count.
     * Pads with a [defaultValue] if the source list is smaller, or truncates if it's larger.
     *
     * @param T The type of elements in the list.
     * @param sourceList The original list to resize.
     * @param targetSize The desired size of the list.
     * @param defaultValue The value to use for padding if the list is smaller than [targetSize].
     * @return A new list of size [targetSize].
     */
    private fun <T> resizeSafelyList(sourceList: List<T>, targetSize: Int, defaultValue: T): List<T> {
        if (sourceList.size == targetSize) return sourceList

        val newList = sourceList.toMutableList()
        while (newList.size < targetSize) {
            newList.add(defaultValue)
        }
        return newList.take(targetSize)
    }
}

/**
 * Factory for creating [GameViewModel] instances.
 * This is useful if [GameViewModel] has constructor dependencies, such as [GameManager].
 *
 * @property gameManager The [GameManager] instance to be injected into the [GameViewModel].
 *                       If not provided, a default [GameManager] will be instantiated by the ViewModel.
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
