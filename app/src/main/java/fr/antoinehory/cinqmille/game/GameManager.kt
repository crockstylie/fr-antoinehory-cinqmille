package fr.antoinehory.cinqmille.game

// Player, DiceRoller, GameRules should be imported or accessible
// import fr.antoinehory.cinqmille.game.Player
// import fr.antoinehory.cinqmille.game.DiceRoller
// import fr.antoinehory.cinqmille.game.GameRules

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

    /** The list of [Player] objects participating in the game. */
    private val players = mutableListOf<Player>()
    /** Index of the current player in the [players] list. */
    private var currentPlayerIndex: Int = -1
    /** Manages the state and logic for the currently active player's turn. */
    private lateinit var turnManager: TurnManager

    /** The minimum score a player must achieve in a single turn to "open" their score. */
    val MIN_SCORE_TO_OPEN = 750
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

    /** Flag indicating if a game is currently active. */
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
            players.add(Player(id = i))
        }
        currentPlayerIndex = 0
        gameInProgress = true
        prepareTurnForCurrentPlayer() // This will now use the updated player
        return GameEvent.GameStarted(players.toList(), players[currentPlayerIndex])
    }

    /**
     * Initializes or re-initializes the [TurnManager] for the current player's turn.
     * This method sets up the `turnManager` with the game's `DiceRoller`,
     * `GameRules` (using `MIN_SCORE_TO_OPEN`), and the current player's `hasOpened` status.
     * It is called at the beginning of each player's turn or when the game starts.
     * If there is no current player (e.g., game not started or error state),
     * an error is logged, and the `turnManager` is not updated.
     */
    private fun prepareTurnForCurrentPlayer() {
        val player = currentPlayer
        if (player != null) {
            turnManager = TurnManager(
                diceRoller,
                GameRules(openingScoreThreshold = MIN_SCORE_TO_OPEN),
                player.hasOpened
            )
        } else {
            // Log error or handle exceptional state: Cannot prepare turn for a null player.
            // This implies game is not started, or no players, or currentPlayerIndex is invalid.
            // If ::turnManager.isInitialized, it might retain state of the previous player if not cleared elsewhere.
            println("Error: GameManager.prepareTurnForCurrentPlayer() called with a null current player.")
        }
    }

    /**
     * Handles the player's primary action which involves rolling dice.
     * If dice were selected (`selectedIndices` is not null and not empty), it processes the selection.
     * If the selection scores and allows for another roll, it automatically triggers a roll of the remaining dice.
     * If no dice were selected (`selectedIndices` is null or empty), it performs an initial roll for the turn/segment.
     *
     * @param selectedIndices The indices of dice selected by the player from a previous roll,
     *                        or null/empty if this is an initial roll for the turn/segment.
     * @return A [GameEvent] representing the outcome: [GameEvent.CurrentTurnUpdated]
     *         (with [TurnEvent.Rolled] or [TurnEvent.Scored]), [GameEvent.PlayerBusted],
     *         or [GameEvent.InvalidGameAction].
     */
    fun handleRollAction(selectedIndices: List<Int>?): GameEvent {
        if (!gameInProgress || currentPlayer == null || !::turnManager.isInitialized) {
            return GameEvent.InvalidGameAction("Game not started or no current player/turn manager.")
        }
        val player = currentPlayer!! // Capture current player

        if (selectedIndices != null && selectedIndices.isNotEmpty()) { // Process selection first
            val selectionEvent = turnManager.selectDice(selectedIndices)

            return when (selectionEvent) {
                is TurnEvent.Busted -> {
                    moveToNextPlayer()
                    GameEvent.PlayerBusted(player)
                }
                is TurnEvent.Scored -> {
                    if (selectionEvent.canRollAgain) {
                        val rollAfterSelectionEvent = turnManager.rollDice() // Auto-roll remaining
                        when (rollAfterSelectionEvent) {
                            is TurnEvent.Busted -> {
                                moveToNextPlayer()
                                GameEvent.PlayerBusted(player)
                            }
                            is TurnEvent.Rolled -> GameEvent.CurrentTurnUpdated(rollAfterSelectionEvent)
                            is TurnEvent.InvalidAction -> GameEvent.InvalidGameAction("Invalid action from auto-roll: ${rollAfterSelectionEvent.message}")
                            else -> GameEvent.InvalidGameAction("Unexpected event after auto-roll: ${rollAfterSelectionEvent::class.simpleName}")
                        }
                    } else {
                        GameEvent.CurrentTurnUpdated(selectionEvent) // Player scored, cannot roll again (must bank or turn ends if no dice)
                    }
                }
                is TurnEvent.InvalidAction -> GameEvent.CurrentTurnUpdated(selectionEvent) // Propagate invalid selection
                else -> GameEvent.InvalidGameAction("Unexpected event from selectDice: ${selectionEvent::class.simpleName}")
            }
        } else { // Initial roll for the turn or segment
            val rollEvent = turnManager.rollDice()
            return when (rollEvent) {
                is TurnEvent.Busted -> {
                    moveToNextPlayer()
                    GameEvent.PlayerBusted(player)
                }
                is TurnEvent.Rolled -> GameEvent.CurrentTurnUpdated(rollEvent)
                is TurnEvent.InvalidAction -> GameEvent.InvalidGameAction("Invalid action from initial roll: ${rollEvent.message}")
                else -> GameEvent.InvalidGameAction("Unexpected event from initial roll: ${rollEvent::class.simpleName}")
            }
        }
    }

    /**
     * Handles the current player's action to bank their current turn score.
     * Updates the player's total score, checks for opening requirements, and game-winning conditions.
     * Moves to the next player if the game is not won.
     *
     * @return A [GameEvent] representing the outcome: [GameEvent.PlayerOpenedAndScored],
     *         [GameEvent.PlayerScored], [GameEvent.PlayerFailedToOpen], [GameEvent.PlayerWon],
     *         [GameEvent.CurrentTurnUpdated] (with [TurnEvent.InvalidAction]), or [GameEvent.InvalidGameAction].
     */
    fun currentTurnBankScore(): GameEvent {
        if (!gameInProgress || currentPlayer == null || !::turnManager.isInitialized) {
            return GameEvent.InvalidGameAction("Game not started or no current player/turn manager.")
        }

        val player = currentPlayer!!
        val bankEventFromTurnManager = turnManager.bankScore()

        return when (bankEventFromTurnManager) {
            is TurnEvent.TurnEndedBanked -> {
                val bankedScoreInTurn = bankEventFromTurnManager.finalTurnScore
                var gameEvent: GameEvent

                player.lastKnownTurnScore = turnManager.getCurrentTurnScore() // What they had *before* bank determined final banked value

                if (!player.hasOpened) {
                    if (bankedScoreInTurn >= MIN_SCORE_TO_OPEN) {
                        player.hasOpened = true
                        player.totalScore += bankedScoreInTurn
                        gameEvent = GameEvent.PlayerOpenedAndScored(player, bankedScoreInTurn, player.totalScore)
                    } else {
                        // bankedScoreInTurn from TurnManager will be 0 if opening condition failed there.
                        // PlayerFailedToOpen uses lastKnownTurnScore to show what was attempted/lost.
                        gameEvent = GameEvent.PlayerFailedToOpen(player, player.lastKnownTurnScore)
                    }
                } else {
                    player.totalScore += bankedScoreInTurn
                    gameEvent = GameEvent.PlayerScored(player, bankedScoreInTurn, player.totalScore)
                }

                if ((player.hasOpened || gameEvent is GameEvent.PlayerOpenedAndScored) && player.totalScore >= TARGET_SCORE_TO_WIN) {
                    if (gameEvent !is GameEvent.PlayerFailedToOpen) { // Don't win on a failed open
                        gameInProgress = false
                        return GameEvent.PlayerWon(player, player.totalScore)
                    }
                }

                if (gameInProgress) {
                    moveToNextPlayer()
                }
                gameEvent
            }
            is TurnEvent.InvalidAction -> {
                GameEvent.CurrentTurnUpdated(bankEventFromTurnManager)
            }
            else -> GameEvent.InvalidGameAction("Unexpected event from bankScore: ${bankEventFromTurnManager::class.simpleName}")
        }
    }

    /**
     * Moves to the next player in the list and prepares the [TurnManager] for their turn.
     * This method does not emit an event itself; events are typically emitted by the action
     * that leads to the turn change (e.g., banking, busting).
     * If the game is not in progress or no players exist, this method does nothing.
     */
    private fun moveToNextPlayer() {
        if (players.isEmpty() || !gameInProgress) {
            return
        }
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size
        prepareTurnForCurrentPlayer()
        // Consider emitting GameEvent.PlayerTurnStarted(players[currentPlayerIndex], turnManager.latestRoll, etc.)
    }
}
