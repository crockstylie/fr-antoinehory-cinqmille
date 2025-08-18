package fr.antoinehory.cinqmille.ui.game

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

/**
 * ViewModel for the game screen, responsible for managing the game's UI state
 * and handling user interactions.
 * It interacts with the [GameManager] to process game logic and updates
 * the UI state via a [StateFlow] of [GameUiState].
 *
 * @property gameManager The game manager instance that handles the core game logic.
 */
class GameViewModel(private val gameManager: GameManager) : ViewModel() {

    /**
     * Secondary constructor for use without dependency injection (e.g., by `viewModels()` without a factory).
     * This default constructor is often used by the system if no factory is provided.
     * It initializes a [GameManager] with a [DefaultDiceRoller].
     */
    constructor() : this(GameManager(DefaultDiceRoller()))

    private val _uiState = MutableStateFlow(GameUiState())
    /**
     * The current state of the game UI, observed by the Composables.
     */
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /**
     * Starts a new game with the specified number of players.
     * It calls the [GameManager] to initialize the game and updates the UI state accordingly.
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
     * Handles the action of rolling the dice for the current player.
     * It calls the [GameManager] and updates the UI state based on the outcome.
     * This action is only performed if the "Roll" button is currently enabled in the UI state.
     */
    fun rollDice() {
        viewModelScope.launch {
            if (_uiState.value.isRollButtonEnabled) {
                val gameEvent = gameManager.currentTurnRollDice()
                updateUiStateFromGameEvent(gameEvent)
            }
        }
    }

    /**
     * Confirms the current visual selection of dice and sends it to the GameManager.
     * The selected dice are derived from the `selectedDiceVisual` state.
     */
    fun selectDice() { // Parameter removed
        viewModelScope.launch {
            val finalSelectedIndices = _uiState.value.selectedDiceVisual
                .mapIndexedNotNull { index, isSelected -> if (isSelected) index else null }

            val gameEvent = gameManager.currentTurnSelectDice(finalSelectedIndices)
            updateUiStateFromGameEvent(gameEvent)
        }
    }

    /**
     * Handles the action of banking the current turn's score for the current player.
     * It calls the [GameManager] to bank the score and updates the UI state.
     * This action is only performed if the "Bank" button is currently enabled in the UI state.
     */
    fun bankScore() {
        viewModelScope.launch {
            if (_uiState.value.isBankButtonEnabled) {
                val gameEvent = gameManager.currentTurnBankScore()
                updateUiStateFromGameEvent(gameEvent)
            }
        }
    }

    /**
     * Toggles the selection state of a die at the given index.
     * This updates the `selectedDiceVisual` state for UI feedback.
     *
     * @param index The 0-based index of the die in the `currentDiceRoll` to toggle.
     */
    fun toggleDieSelection(index: Int) {
        val currentVisualSelection = _uiState.value.selectedDiceVisual
        val currentDice = _uiState.value.currentDiceRoll

        if (index >= 0 && index < currentDice.size && index < currentVisualSelection.size) {
            val newVisualSelection = currentVisualSelection.toMutableList()
            newVisualSelection[index] = !newVisualSelection[index]
            _uiState.update { it.copy(selectedDiceVisual = newVisualSelection) }
        }
    }

    /**
     * Updates the [GameUiState] based on a [GameEvent] received from the [GameManager].
     * This function is central to keeping the UI synchronized with the game's backend logic.
     *
     * @param gameEvent The event from the [GameManager] to process.
     */
    private fun updateUiStateFromGameEvent(gameEvent: GameEvent) {
        val currentState = _uiState.value // Keep for getNextPlayerId if needed for a brief moment
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
                        selectedDiceIndices = emptyList(),
                        selectedDiceVisual = emptyList()
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
                        selectedDiceIndices = emptyList(),
                        selectedDiceVisual = emptyList(),
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
                                selectedDiceIndices = emptyList(), 
                                selectedDiceVisual = List(turnEvent.dice.size) { false } 
                            )
                        }
                    }
                    is TurnEvent.Scored -> { // MODIFIED BLOCK
                        _uiState.update {
                            val newDiceRollToShow = turnEvent.remainingDiceInHand
                            it.copy(
                                currentTurnScore = turnEvent.newTurnTotalScore,
                                currentMessage = "Score ce tour : ${turnEvent.newTurnTotalScore}. Dés marqués: ${turnEvent.diceSelected.joinToString()}. Relancez ou banquez.",
                                isRollButtonEnabled = turnEvent.canRollAgain,
                                isBankButtonEnabled = true, 
                                currentDiceRoll = newDiceRollToShow, // MODIFIED
                                selectedDiceIndices = emptyList(),   
                                selectedDiceVisual = List(newDiceRollToShow.size) { false } // MODIFIED
                            )
                        }
                    }
                    is TurnEvent.Busted -> { 
                        _uiState.update {
                            it.copy(
                                currentMessage = "Sélection bustée ! Votre tour est terminé.", 
                                isRollButtonEnabled = false, 
                                isBankButtonEnabled = false, 
                                selectedDiceIndices = emptyList(), 
                                selectedDiceVisual = emptyList() 
                            )
                        }
                    }
                    is TurnEvent.TurnEndedBanked -> { 
                        _uiState.update {
                            it.copy(
                                currentMessage = "Score banqué (via TurnEndedBanked). Préparez le prochain joueur.",
                                selectedDiceVisual = emptyList() 
                            )
                        }
                    }
                    is TurnEvent.InvalidAction -> {
                        _uiState.update {
                            it.copy(currentMessage = "Action invalide: ${turnEvent.message}")
                        }
                    }
                }
            }
            is GameEvent.PlayerScored -> {
                val nextPlayerId = getNextPlayerId(currentState.currentPlayerId)
                _uiState.update {
                    it.copy(
                        players = mapPlayersToUiState(gameManager.allPlayers, nextPlayerId),
                        currentPlayerId = nextPlayerId,
                        currentMessage = "Joueur ${gameEvent.player.id} a marqué ${gameEvent.scoreThisTurn}. Total: ${gameEvent.newTotalScore}. Au tour du Joueur $nextPlayerId.",
                        currentDiceRoll = emptyList(),
                        currentTurnScore = 0,
                        isRollButtonEnabled = true,
                        isBankButtonEnabled = false,
                        selectedDiceIndices = emptyList(),
                        selectedDiceVisual = emptyList()
                    )
                }
            }
            is GameEvent.PlayerOpenedAndScored -> {
                val nextPlayerId = getNextPlayerId(currentState.currentPlayerId)
                _uiState.update {
                    it.copy(
                        players = mapPlayersToUiState(gameManager.allPlayers, nextPlayerId),
                        currentPlayerId = nextPlayerId,
                        currentMessage = "Joueur ${gameEvent.player.id} a ouvert avec ${gameEvent.scoreThisTurn}! Total: ${gameEvent.newTotalScore}. Au tour du Joueur $nextPlayerId.",
                        currentDiceRoll = emptyList(),
                        currentTurnScore = 0,
                        isRollButtonEnabled = true,
                        isBankButtonEnabled = false,
                        selectedDiceIndices = emptyList(),
                        selectedDiceVisual = emptyList()
                    )
                }
            }
            is GameEvent.PlayerFailedToOpen -> {
                val nextPlayerId = getNextPlayerId(currentState.currentPlayerId)
                _uiState.update {
                    it.copy(
                        players = mapPlayersToUiState(gameManager.allPlayers, nextPlayerId),
                        currentPlayerId = nextPlayerId,
                        currentMessage = "Joueur ${gameEvent.player.id} n'a pas pu ouvrir avec ${gameEvent.scoreAttemptedThisTurn}. Au tour du Joueur $nextPlayerId.",
                        currentDiceRoll = emptyList(),
                        currentTurnScore = 0,
                        isRollButtonEnabled = true,
                        isBankButtonEnabled = false,
                        selectedDiceIndices = emptyList(),
                        selectedDiceVisual = emptyList()
                    )
                }
            }
            is GameEvent.PlayerBusted -> { 
                val nextPlayerId = getNextPlayerId(currentState.currentPlayerId)
                _uiState.update {
                    it.copy(
                        players = mapPlayersToUiState(gameManager.allPlayers, nextPlayerId),
                        currentPlayerId = nextPlayerId,
                        currentMessage = "Joueur ${gameEvent.player.id} a busté ! Au tour du Joueur $nextPlayerId.",
                        currentDiceRoll = emptyList(),
                        currentTurnScore = 0,
                        isRollButtonEnabled = true,
                        isBankButtonEnabled = false,
                        selectedDiceIndices = emptyList(),
                        selectedDiceVisual = emptyList()
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
                        selectedDiceIndices = emptyList(),
                        selectedDiceVisual = emptyList()
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

    private fun mapPlayersToUiState(players: List<Player>, currentPlayingId: Int?): List<PlayerUiState> {
        return players.map { player ->
            PlayerUiState(
                id = player.id,
                totalScore = player.totalScore,
                hasOpened = player.hasOpened,
                isCurrentPlayer = player.id == currentPlayingId
            )
        }
    }

    private fun getNextPlayerId(currentId: Int?): Int? {
        if (currentId == null) return null 
        val players = gameManager.allPlayers
        if (players.isEmpty()) return null 
        val currentIndex = players.indexOfFirst { it.id == currentId }
        if (currentIndex == -1) return null 
        return players[(currentIndex + 1) % players.size].id
    }
}

@Suppress("UNCHECKED_CAST")
class GameViewModelFactory(private val gameManager: GameManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            return GameViewModel(gameManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
