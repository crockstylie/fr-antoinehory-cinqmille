package fr.antoinehory.cinqmille.game

// Assurez-vous que Player, DiceRoller, ScoreCalculator (si encore utilisé ailleurs), GameRules sont importés ou accessibles
// import fr.antoinehory.cinqmille.game.Player // Exemple
// import fr.antoinehory.cinqmille.game.DiceRoller // Exemple
// import fr.antoinehory.cinqmille.game.GameRules // Exemple

/**
 * Manages the overall game flow, player states, and turn transitions for the Cinq Mille game.
 *
 * This class orchestrates the game by:
 * - Starting and initializing the game with a set number of players.
 * - Keeping track of all players and their scores.
 * - Managing whose turn it is.
 * - Delegating turn-specific actions (rolling, selecting dice, banking) to a [TurnManager].
 * - Determining game-ending conditions (e.g., a player reaching the target score).
 * - Emitting [GameEvent]s to notify observers (like a ViewModel) of changes in the game state.
 *
 * @property diceRoller The [DiceRoller] instance used for rolling dice during turns.
 *                      Passed to the [TurnManager].
 */
class GameManager(private val diceRoller: DiceRoller) {

    private val players = mutableListOf<Player>()
    private var currentPlayerIndex: Int = -1
    private lateinit var turnManager: TurnManager // MODIFIÉ: lateinit

    /** The minimum score a player must achieve in a single turn to "open" their score. */
    val MIN_SCORE_TO_OPEN = 750 // Pourrait venir de GameRules aussi
    /** The target score a player must reach or exceed to win the game. */
    val TARGET_SCORE_TO_WIN = 5000

    /**
     * The player whose turn it currently is.
     * Returns null if the game is not in progress or if there are no players.
     */
    val currentPlayer: Player?
        get() = players.getOrNull(currentPlayerIndex)

    /**
     * A read-only list of all players currently in the game.
     */
    val allPlayers: List<Player>
        get() = players.toList()

    private var gameInProgress: Boolean = false

    /**
     * Starts a new game with the specified number of players.
     * Initializes player list, sets the first player, and resets game state.
     *
     * @param numberOfPlayers The number of players for the game. Must be positive.
     * @return [GameEvent.GameStarted] if the game starts successfully,
     *         or [GameEvent.InvalidGameAction] if the number of players is invalid.
     */
    fun startGame(numberOfPlayers: Int): GameEvent {
        if (numberOfPlayers <= 0) {
            return GameEvent.InvalidGameAction("Number of players must be positive.")
        }
        players.clear()
        for (i in 1..numberOfPlayers) {
            players.add(Player(id = i)) // Assume Player constructor is (id: Int)
        }
        currentPlayerIndex = 0
        gameInProgress = true
        prepareTurnForCurrentPlayer() // Initialise turnManager pour le premier joueur
        return GameEvent.GameStarted(players.toList(), players[currentPlayerIndex])
    }

    /**
     * Resets the [TurnManager] for the current player's turn.
     */
    private fun prepareTurnForCurrentPlayer() {
        val player = currentPlayer
        if (player == null) {
            // Should not happen if game is in progress and players exist. Handle error or ensure state.
            // For now, let's assume if this is called, currentPlayer is valid.
            // Or, we could throw an IllegalStateException if player is null.
            // As a safeguard during init, or if called at a wrong time:
            if (!::turnManager.isInitialized && players.isNotEmpty()) {
                turnManager = TurnManager(diceRoller, GameRules(openingScoreThreshold = MIN_SCORE_TO_OPEN), players[0].hasOpened) // MODIFIÉ
            } else if (player != null) {
                turnManager = TurnManager(diceRoller, GameRules(openingScoreThreshold = MIN_SCORE_TO_OPEN), player.hasOpened) // MODIFIÉ
            }
            // If player is null and turnManager was already initialized, it might keep state of last player.
            // This logic assumes prepareTurnForCurrentPlayer is called when currentPlayer is definitively set.
        } else {
            turnManager = TurnManager(diceRoller, GameRules(openingScoreThreshold = MIN_SCORE_TO_OPEN), player.hasOpened) // MODIFIÉ
        }
    }

    /**
     * Handles the current player's action to roll the dice.
     * Delegates to [TurnManager] to process the roll.
     *
     * @return A [GameEvent] representing the outcome of the roll.
     */
    fun currentTurnRollDice(): GameEvent {
        if (!gameInProgress || currentPlayer == null || !::turnManager.isInitialized) {
            return GameEvent.InvalidGameAction("Game not started or no current player/turn manager.")
        }
        val player = currentPlayer!! // Capture current player before potential turn change
        val turnEvent = turnManager.rollDice() // MODIFIÉ

        return when (turnEvent) {
            is TurnEvent.Busted -> {
                val bustedPlayer = player
                moveToNextPlayer()
                GameEvent.PlayerBusted(bustedPlayer)
            }
            is TurnEvent.Rolled -> {
                GameEvent.CurrentTurnUpdated(turnEvent)
            }
            // rollDice() devrait seulement retourner Rolled ou Busted (ou InvalidAction, mais on le gère avant)
            // Donc, pas besoin d'être exhaustif pour Scored, TurnEndedBanked ici.
            // Si TurnManager.rollDice() peut retourner autre chose, il faudra l'ajouter.
            else -> GameEvent.InvalidGameAction("Unexpected event from rollDice: ${turnEvent::class.simpleName}")
        }
    }

    /**
     * Handles the current player's action to select dice after a roll.
     * Delegates to [TurnManager] to process the selection.
     *
     * @param selectedIndices The indices of the dice selected by the player from their current roll.
     * @return A [GameEvent] representing the outcome of the selection.
     */
    fun currentTurnSelectDice(selectedIndices: List<Int>): GameEvent {
        if (!gameInProgress || currentPlayer == null || !::turnManager.isInitialized) {
            return GameEvent.InvalidGameAction("Game not started or no current player/turn manager.")
        }
        val player = currentPlayer!!
        val turnEvent = turnManager.selectDice(selectedIndices) // MODIFIÉ

        return when (turnEvent) {
            is TurnEvent.Busted -> {
                val bustedPlayer = player
                moveToNextPlayer()
                GameEvent.PlayerBusted(bustedPlayer)
            }
            is TurnEvent.Scored, is TurnEvent.InvalidAction -> { // InvalidAction peut venir de selectDice
                GameEvent.CurrentTurnUpdated(turnEvent)
            }
            // selectDice() retourne Scored, Busted, ou InvalidAction.
            // Rolled ou TurnEndedBanked ne devraient pas arriver ici.
            else -> GameEvent.InvalidGameAction("Unexpected event from selectDice: ${turnEvent::class.simpleName}")

        }
    }

    /**
     * Handles the current player's action to bank their current turn score.
     * Updates the player's total score and checks for game-winning conditions.
     *
     * @return A [GameEvent] representing the outcome.
     */
    fun currentTurnBankScore(): GameEvent {
        if (!gameInProgress || currentPlayer == null || !::turnManager.isInitialized) {
            return GameEvent.InvalidGameAction("Game not started or no current player/turn manager.")
        }

        val player = currentPlayer!!
        val bankEventFromTurnManager = turnManager.bankScore() // MODIFIÉ

        return when (bankEventFromTurnManager) {
            is TurnEvent.TurnEndedBanked -> {
                val bankedScore = bankEventFromTurnManager.finalTurnScore
                var playerEvent: GameEvent

                player.lastKnownTurnScore = bankedScore // Pour référence

                if (!player.hasOpened) {
                    if (bankedScore >= MIN_SCORE_TO_OPEN) {
                        player.hasOpened = true
                        player.totalScore += bankedScore
                        playerEvent = GameEvent.PlayerOpenedAndScored(player, bankedScore, player.totalScore)
                    } else {
                        // Score non suffisant pour ouvrir, le joueur perd son tour et son score de tour (bankedScore est 0 de TurnManager).
                        playerEvent = GameEvent.PlayerFailedToOpen(player, bankedScore) // bankedScore sera 0 ici
                    }
                } else { // Player has already opened
                    player.totalScore += bankedScore
                    playerEvent = GameEvent.PlayerScored(player, bankedScore, player.totalScore)
                }

                if ((player.hasOpened && playerEvent !is GameEvent.PlayerFailedToOpen) && player.totalScore >= TARGET_SCORE_TO_WIN) {
                    gameInProgress = false
                    return GameEvent.PlayerWon(player, player.totalScore)
                }

                moveToNextPlayer()
                playerEvent
            }
            is TurnEvent.InvalidAction -> { // Ex: trying to bank when not allowed (score 0, or not opened yet)
                GameEvent.CurrentTurnUpdated(bankEventFromTurnManager) // Le tour ne se termine pas forcément ici.
            }
            // bankScore() ne devrait pas retourner Busted, Rolled, Scored.
            // Si TurnManager.bankScore() peut retourner Busted (e.g. pour "busted by trying to bank 0 after a non-scoring selection"),
            // il faudrait le gérer. Mais la logique actuelle de TurnManager retourne InvalidAction ou TurnEndedBanked(0).
            else -> GameEvent.InvalidGameAction("Unexpected event from bankScore: ${bankEventFromTurnManager::class.simpleName}")

        }
    }

    /**
     * Moves to the next player in the list.
     * If the game is not in progress, this method does nothing.
     * Prepares the [TurnManager] for the new player's turn.
     */
    private fun moveToNextPlayer() {
        if (players.isEmpty() || !gameInProgress) {
            return
        }
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size
        prepareTurnForCurrentPlayer() // Réinitialise turnManager pour le nouveau joueur
    }
}