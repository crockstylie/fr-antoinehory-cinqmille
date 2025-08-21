package fr.antoinehory.cinqmille.ui.game

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.antoinehory.cinqmille.game.DefaultDiceRoller
import fr.antoinehory.cinqmille.game.GameManager
import fr.antoinehory.cinqmille.game.GameEvent
import fr.antoinehory.cinqmille.game.Player
import fr.antoinehory.cinqmille.game.TurnEvent
// DiceRoll import might be needed if GameEvent subtypes directly expose it, or for DiceRoll type alias.
// import fr.antoinehory.cinqmille.game.DiceRoll

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Cinq Mille game screen.
 *
 * This ViewModel is responsible for managing the UI state of the game,
 * handling user interactions (like rolling dice, banking score), and communicating
 * with the [GameManager] to process game logic.
 *
 * @property gameManager The underlying game logic manager.
 */
class GameViewModel(private val gameManager: GameManager) : ViewModel() {

    /**
     * Secondary constructor that initializes the ViewModel with a default [GameManager]
     * using a [DefaultDiceRoller]. Useful for creation without a ViewModel factory.
     */
    constructor() : this(GameManager(DefaultDiceRoller()))

    private val _uiState = MutableStateFlow(GameUiState())
    /**
     * The [StateFlow] emitting the current UI state ([GameUiState]) of the game.
     * Observers can collect this flow to react to changes in the game's displayable state.
     */
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var lastSelectionAttemptIndices: List<Int>? = null

    /**
     * Starts a new game with the specified number of players.
     * Delegates to [GameManager.startGame] and updates the UI state based on the result.
     *
     * @param numberOfPlayers The number of players to start the game with.
     */
    fun startGame(numberOfPlayers: Int) {
        viewModelScope.launch {
            val gameEvent = gameManager.startGame(numberOfPlayers)
            updateUiStateFromGameEvent(gameEvent)
        }
    }

    /**
     * Handles the user's action to roll dice.
     *
     * If dice are visually selected (and not already part of a scored set), these selections
     * are passed to the [GameManager.handleRollAction]. Otherwise, it's treated as a
     * roll of available dice. The UI state is updated based on the [GameEvent] received.
     */
    fun rollDice() {
        viewModelScope.launch {
            if (_uiState.value.isRollButtonEnabled) {
                val currentRoll = _uiState.value.currentDiceRoll
                val currentScoredMask = _uiState.value.scoredDiceMask
                val currentVisualSelection = _uiState.value.selectedDiceVisual

                val effectiveSelectedIndices: List<Int>? = if (currentRoll.isNotEmpty()) {
                    currentVisualSelection
                        .mapIndexedNotNull { index, isSelected ->
                            if (isSelected &&
                                index < currentRoll.size && currentRoll[index] != 0 &&
                                index < currentScoredMask.size && !currentScoredMask[index]
                            ) {
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
                Log.d("GameVM_RollDice", "Calling handleRollAction with selected indices: $effectiveSelectedIndices")
                val gameEvent = gameManager.handleRollAction(effectiveSelectedIndices)
                updateUiStateFromGameEvent(gameEvent)
            }
        }
    }

    /**
     * Handles the user's action to bank their current turn score.
     * If dice are visually selected, their score is first validated and added to the turn's total.
     * Then, the total turn score is banked.
     * Delegates to [GameManager.currentTurnBankScore] and updates the UI state.
     */
    fun bankScore() {
        viewModelScope.launch {
            if (_uiState.value.isBankButtonEnabled) {
                val currentState = _uiState.value
                val pendingSelectedIndices: List<Int>? = if (currentState.currentDiceRoll.isNotEmpty()) {
                    currentState.selectedDiceVisual
                        .mapIndexedNotNull { index, isSelected ->
                            if (isSelected &&
                                index < currentState.currentDiceRoll.size && currentState.currentDiceRoll[index] != 0 &&
                                index < currentState.scoredDiceMask.size && !currentState.scoredDiceMask[index]
                            ) {
                                index
                            } else {
                                null
                            }
                        }
                        .takeIf { it.isNotEmpty() }
                } else {
                    null
                }
                lastSelectionAttemptIndices = pendingSelectedIndices

                Log.d("GameVM_BankScore", "Calling currentTurnBankScore with pending selected indices: $pendingSelectedIndices")
                val gameEvent = gameManager.currentTurnBankScore(pendingSelectedIndices)
                updateUiStateFromGameEvent(gameEvent)
            } else {
                Log.d("GameVM_BankScore", "Bank button pressed but isBankButtonEnabled is false.")
            }
        }
    }

    /**
     * Toggles the visual selection state of a die at the given [index].
     * Updates the displayed preview score and the enabled state of the bank button live
     * based on the current selection.
     * A die can be selected if it's currently displayed (not a placeholder),
     * and not already part of a "scored and kept" set for the current turn segment.
     *
     * @param index The 0-based index of the die to toggle.
     */
    // Dans GameViewModel.kt

    fun toggleDieSelection(index: Int) {
        val currentState = _uiState.value
        val currentVisualSelection = currentState.selectedDiceVisual
        val currentDice = currentState.currentDiceRoll
        val currentScoredMask = currentState.scoredDiceMask
        val currentPlayer = currentState.players.find { it.id == currentState.currentPlayerId }

        if (index >= 0 && index < currentDice.size &&
            currentDice[index] != 0 &&
            index < currentVisualSelection.size
        ) {
            if (currentScoredMask.getOrElse(index) { false }) {
                Log.d("GameVM_ToggleDie", "Attempted to toggle an already scored die at index $index.")
                return
            }

            val newVisualSelection = currentVisualSelection.toMutableList()
            newVisualSelection[index] = !newVisualSelection[index]

            val diceForPreviewScore = mutableListOf<Int>()
            for (i in currentDice.indices) {
                if (newVisualSelection.getOrElse(i) { false } &&
                    !currentScoredMask.getOrElse(i) { false } &&
                    currentDice.getOrElse(i) { 0 } != 0
                ) {
                    diceForPreviewScore.add(currentDice[i])
                }
            }

            val previewScore = gameManager.calculatePreviewScore(diceForPreviewScore)
            val potentialTotalScore = currentState.accumulatedTurnScore + previewScore

            // MODIFIED: Logic for enabling bank button
            val canBank: Boolean
            if (currentPlayer?.hasOpened == true) {
                canBank = potentialTotalScore > 0
            } else {
                // Assumes gameManager.MIN_SCORE_TO_OPEN is accessible.
                // If not, MIN_SCORE_TO_OPEN needs to be available to the ViewModel,
                // possibly from GameManager or duplicated if static.
                canBank = potentialTotalScore >= gameManager.MIN_SCORE_TO_OPEN
            }

            _uiState.update {
                it.copy(
                    selectedDiceVisual = newVisualSelection,
                    previewSelectionScore = previewScore,
                    isBankButtonEnabled = canBank
                )
            }
        }
    }

    /**
     * Updates the internal UI state based on a [GameEvent] received from the [GameManager].
     * This function is central to translating game logic outcomes into displayable UI changes.
     *
     * @param gameEvent The event from the GameManager detailing a change in game state.
     */
    private fun updateUiStateFromGameEvent(gameEvent: GameEvent) {
        Log.d("GameVM_Update", "Received GameEvent: ${gameEvent::class.simpleName}, Data: $gameEvent")

        val previousUiState = _uiState.value
        val initialDiceDisplayCount = GameUiState.INITIAL_DICE_COUNT

        when (gameEvent) {
            is GameEvent.GameStarted -> {
                _uiState.update {
                    it.copy(
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
                Log.d("GameVM_Update", "  TurnEvent: ${gameEvent.turnEvent::class.simpleName}, Data: ${gameEvent.turnEvent}")
                when (val turnEvent = gameEvent.turnEvent) {
                    is TurnEvent.Rolled -> {
                        _uiState.update {
                            it.copy(
                                currentDiceRoll = resizeSafelyList(turnEvent.dice, initialDiceDisplayCount, 0),
                                currentMessage = if (turnEvent.canPlayerMakeAnyScore) "Sélectionnez vos dés."
                                else "Busté sur ce lancer ! Tour terminé ou essayez de banquer si score précédent.",
                                isRollButtonEnabled = turnEvent.canPlayerMakeAnyScore,
                                // Bank button enabled if accumulated score > 0, or if this roll makes score > 0
                                // This will also be updated by toggleDieSelection if user selects dice.
                                isBankButtonEnabled = it.accumulatedTurnScore > 0,
                                selectedDiceVisual = List(initialDiceDisplayCount) { false },
                                scoredDiceMask = resizeSafelyList(emptyList(), initialDiceDisplayCount, false),
                                previewSelectionScore = 0,
                            )
                        }
                    }
                    is TurnEvent.Scored -> {
                        _uiState.update {
                            it.copy(
                                currentDiceRoll = resizeSafelyList(turnEvent.diceStateAfterAction, initialDiceDisplayCount, 0),
                                scoredDiceMask = resizeSafelyList(turnEvent.scoredDiceMask, initialDiceDisplayCount, false),
                                accumulatedTurnScore = turnEvent.newTurnTotalScore,
                                previewSelectionScore = 0,
                                currentMessage = "Score ce tour : ${turnEvent.newTurnTotalScore}. Relancez ou banquez.",
                                isRollButtonEnabled = turnEvent.canRollAgain,
                                isBankButtonEnabled = turnEvent.newTurnTotalScore > 0, // Enabled if there's score
                                selectedDiceVisual = List(initialDiceDisplayCount) { false }
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
                                scoredDiceMask = List(initialDiceDisplayCount) { true },
                                accumulatedTurnScore = 0,
                                previewSelectionScore = 0
                            )
                        }
                    }
                    is TurnEvent.InvalidAction -> {
                        _uiState.update {
                            // isBankButtonEnabled might need re-evaluation based on current accumulatedScore
                            // and if the invalid action cleared a preview. For now, keep as is or rely on next valid state.
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
                                // accumulatedTurnScore is effectively 0 for the UI for this player now.
                            )
                        }
                    }
                }
                lastSelectionAttemptIndices = null
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
                        currentDiceRoll = resizeSafelyList(gameEvent.diceAtBust, initialDiceDisplayCount, 0),
                        currentMessage = "Joueur ${gameEvent.player.id} a busté ! Dés: ${gameEvent.diceAtBust.joinToString()}. Au tour du Joueur $nextPlayerId.",
                        accumulatedTurnScore = 0,
                        previewSelectionScore = 0,
                        isRollButtonEnabled = true, // For next player
                        isBankButtonEnabled = false, // For next player
                        selectedDiceVisual = List(initialDiceDisplayCount) { false },
                        scoredDiceMask = List(initialDiceDisplayCount) { true }
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
     * @param currentPlayingId The ID of the player whose turn it currently is, or null.
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
     * @param currentId The ID of the current player, or null if no current player.
     * @param playersList The list of all players in the game.
     * @return The ID of the next player, or null if it cannot be determined.
     */
    private fun getNextPlayerId(currentId: Int?, playersList: List<Player>): Int? {
        if (currentId == null && playersList.isNotEmpty()) return playersList.first().id
        if (currentId == null || playersList.isEmpty()) return null

        val currentIndex = playersList.indexOfFirst { it.id == currentId }
        return if (currentIndex != -1 && playersList.isNotEmpty()) {
            playersList[(currentIndex + 1) % playersList.size].id
        } else {
            playersList.firstOrNull()?.id // Fallback
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
 * This is useful if [GameViewModel] has constructor dependencies, like [GameManager].
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
