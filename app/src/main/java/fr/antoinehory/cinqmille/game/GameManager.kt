package fr.antoinehory.cinqmille.game

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

    /** The minimum score a player must achieve in a single turn to "open" their score. Typically matches `GameRules.openingScoreThreshold`. */
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
     * Initializes player states, sets the first player, and prepares the [TurnManager].
     *
     * @param numberOfPlayers The number of players for this game. Must be positive.
     * @return [GameEvent.GameStarted] with the initial list of players and the first player,
     *         or [GameEvent.InvalidGameAction] if the number of players is not positive.
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

        val player = currentPlayer!!
        turnManager = TurnManager(
            diceRoller,
            GameRules(openingScoreThreshold = MIN_SCORE_TO_OPEN),
            player.hasOpened
        )
        return GameEvent.GameStarted(players.toList(), player)
    }

    /**
     * Prepares the [TurnManager] for the current player's turn.
     * This involves resetting the [TurnManager] with the player's "opened" status.
     * If the [TurnManager] hasn't been initialized yet, it creates a new instance.
     * This method expects `currentPlayer` to be non-null.
     */
    private fun prepareTurnForCurrentPlayer() {
        val player = currentPlayer
        if (player != null) {
            if (::turnManager.isInitialized) {
                turnManager.resetForNewTurn(player.hasOpened)
            } else {
                turnManager = TurnManager(
                    diceRoller,
                    GameRules(openingScoreThreshold = MIN_SCORE_TO_OPEN),
                    player.hasOpened
                )
            }
        } else {
            // This should ideally not happen if game flow is correct
            println("Error: GameManager.prepareTurnForCurrentPlayer() called with a null current player.")
        }
    }

    /**
     * Handles a player's action to either roll all dice or select dice to keep and roll the rest.
     *
     * This method delegates the core dice rolling and selection logic to the [TurnManager].
     * Based on the [TurnEvent] returned by the [TurnManager], it updates the game state,
     * such as moving to the next player if the current player busts.
     *
     * If `selectedIndices` is null or empty, it's considered a "roll all available dice" action.
     * If `selectedIndices` is provided, it's considered a "select dice and roll remaining" action.
     *
     * @param selectedIndices A list of 0-based indices of the dice the player wishes to keep from
     *                        the *previous roll's current dice*. If null or empty, all dice currently
     *                        held by the [TurnManager] (if any, after previous selections) or a full
     *                        set of new dice will be rolled.
     * @return A [GameEvent] indicating the outcome, such as [GameEvent.CurrentTurnUpdated]
     *         with details from the [TurnManager], or [GameEvent.PlayerBusted] if the roll results in a bust.
     *         Returns [GameEvent.InvalidGameAction] if the action is not permissible at the current game state.
     */
    fun handleRollAction(selectedIndices: List<Int>?): GameEvent {
        if (!gameInProgress || currentPlayer == null || !::turnManager.isInitialized) {
            return GameEvent.InvalidGameAction("Game not started or no current player/turn manager.")
        }
        val player = currentPlayer!!

        val turnEventResult = if (selectedIndices != null && selectedIndices.isNotEmpty()) {
            turnManager.selectDice(selectedIndices, processRestOfTurnAutomatically = true)
        } else {
            turnManager.rollDice()
        }

        return when (turnEventResult) {
            is TurnEvent.Busted -> {
                val diceAtBust = turnEventResult.diceAtBust
                player.lastKnownTurnScore = 0 // Explicitly set to 0 on bust
                moveToNextPlayer()
                GameEvent.PlayerBusted(player, diceAtBust)
            }
            is TurnEvent.Scored, is TurnEvent.Rolled, is TurnEvent.InvalidAction -> {
                GameEvent.CurrentTurnUpdated(turnEventResult)
            }
            is TurnEvent.TurnEndedBanked -> {
                GameEvent.InvalidGameAction("Unexpected TurnEndedBanked from handleRollAction.")
            }
        }
    }

    /**
     * Allows the current player to bank their accumulated score for the turn.
     *
     * This method first handles any pending dice selection (if `pendingSelectedIndices` is provided)
     * before attempting to bank the score via the [TurnManager].
     *
     * If a selection is made via `pendingSelectedIndices`:
     *   - If this selection results in a bust, the player busts, and their turn ends.
     *   - If the selection is invalid, an [GameEvent.InvalidGameAction] is returned.
     *   - If the selection is valid and scores, the score is added to the turn's total before banking.
     *
     * After handling any pending selection, the method attempts to bank the turn's score:
     * - Updates the player's total score.
     * - Checks if the player has "opened" if they haven't already.
     * - Checks for win conditions.
     * - Moves to the next player if the game is still in progress.
     *
     * @param pendingSelectedIndices Optional. A list of 0-based indices of dice to select and score
     *                               *before* banking. If the player has already rolled and has dice
     *                               they wish to score and immediately bank without re-rolling.
     * @return A [GameEvent] reflecting the outcome:
     *         - [GameEvent.PlayerOpenedAndScored] if the player opened their score.
     *         - [GameEvent.PlayerScored] if the player (already opened) added to their score.
     *         - [GameEvent.PlayerFailedToOpen] if the player couldn't open.
     *         - [GameEvent.PlayerBusted] if a bust occurred during a pending selection.
     *         - [GameEvent.PlayerWon] if the player wins.
     *         - [GameEvent.InvalidGameAction] if banking is not allowed or a selection is invalid.
     */
    fun currentTurnBankScore(pendingSelectedIndices: List<Int>? = null): GameEvent {
        if (!gameInProgress || currentPlayer == null || !::turnManager.isInitialized) {
            return GameEvent.InvalidGameAction("Game not started or no current player/turn manager.")
        }

        val player = currentPlayer!!

        if (pendingSelectedIndices != null && pendingSelectedIndices.isNotEmpty()) {
            val selectionResult = turnManager.selectDice(
                selectedIndices = pendingSelectedIndices,
                processRestOfTurnAutomatically = false
            )

            when (selectionResult) {
                is TurnEvent.Busted -> {
                    val diceAtBust = selectionResult.diceAtBust
                    player.lastKnownTurnScore = 0 // Score lost
                    moveToNextPlayer()
                    return GameEvent.PlayerBusted(player, diceAtBust)
                }
                is TurnEvent.InvalidAction -> {
                    return GameEvent.InvalidGameAction("Invalid dice selection before banking: ${selectionResult.message}")
                }
                is TurnEvent.Scored -> {
                    // Selection was successful. The turnManager's score is updated internally.
                    // Now proceed to bank this updated score.
                }
                is TurnEvent.Rolled -> {
                    return GameEvent.InvalidGameAction("Unexpected 'Rolled' event after selecting dice before banking.")
                }
                is TurnEvent.TurnEndedBanked -> {
                    return GameEvent.InvalidGameAction("Unexpected 'TurnEndedBanked' event from selectDice before banking.")
                }
            }
        }

        val turnManagerBankEvent = turnManager.bankScore()

        if (turnManagerBankEvent is TurnEvent.TurnEndedBanked) {
            val bankedScoreInTurn = turnManagerBankEvent.finalTurnScore
            var gameEventToReturn: GameEvent

            player.lastKnownTurnScore = bankedScoreInTurn

            if (!player.hasOpened) {
                if (bankedScoreInTurn >= MIN_SCORE_TO_OPEN) {
                    player.hasOpened = true
                    player.totalScore += bankedScoreInTurn
                    gameEventToReturn = GameEvent.PlayerOpenedAndScored(player, bankedScoreInTurn, player.totalScore)
                } else {
                    gameEventToReturn = GameEvent.PlayerFailedToOpen(player, bankedScoreInTurn)
                }
            } else {
                if (bankedScoreInTurn > 0) {
                    player.totalScore += bankedScoreInTurn
                    gameEventToReturn = GameEvent.PlayerScored(player, bankedScoreInTurn, player.totalScore)
                } else {
                    gameEventToReturn = GameEvent.PlayerScored(player, 0, player.totalScore)
                }
            }

            val scoreContributesToWin = (player.hasOpened && bankedScoreInTurn >= MIN_SCORE_TO_OPEN && gameEventToReturn is GameEvent.PlayerOpenedAndScored) ||
                    (player.hasOpened && bankedScoreInTurn > 0 && gameEventToReturn is GameEvent.PlayerScored && gameEventToReturn.scoreThisTurn > 0)

            if (scoreContributesToWin && player.totalScore >= TARGET_SCORE_TO_WIN) {
                gameInProgress = false
                return GameEvent.PlayerWon(player, player.totalScore)
            }

            if (gameInProgress) {
                moveToNextPlayer()
            }
            return gameEventToReturn

        } else if (turnManagerBankEvent is TurnEvent.InvalidAction) {
            return GameEvent.InvalidGameAction("Cannot bank: ${turnManagerBankEvent.message}")
        } else {
            return GameEvent.InvalidGameAction("Unexpected event type from TurnManager.bankScore(): ${turnManagerBankEvent::class.simpleName}")
        }
    }

    /**
     * Calculates the potential score for a given set of dice values.
     * This is a utility function that directly uses the [ScoreCalculator].
     *
     * @param diceValues A list of integers representing the dice to score.
     * @return The calculated score based on the game's scoring rules.
     */
    fun calculatePreviewScore(diceValues: List<Int>): Int {
        return ScoreCalculator.calculateScore(diceValues)
    }

    /**
     * Moves the game to the next player in sequence.
     * If there are no players or the current player was the last, it might set `gameInProgress` to false
     * (though currently, it only sets `gameInProgress` to false if `currentPlayer` becomes null, which
     * shouldn't happen with a non-empty player list due to the modulo operator).
     * Prepares the [TurnManager] for the new current player.
     */
    private fun moveToNextPlayer() {
        if (players.isEmpty()) {
            gameInProgress = false // No players, game cannot continue
            return
        }
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size
        // It's important that currentPlayer is updated based on new currentPlayerIndex before prepareTurnForCurrentPlayer
        val nextPlayer = currentPlayer
        if (nextPlayer != null) {
            prepareTurnForCurrentPlayer() // This will use the new currentPlayer
        } else {
            // This case (null player with non-empty list) should not be reached with modulo arithmetic
            // unless players list was cleared, which is handled at the start of the method.
            gameInProgress = false
        }
    }
}

