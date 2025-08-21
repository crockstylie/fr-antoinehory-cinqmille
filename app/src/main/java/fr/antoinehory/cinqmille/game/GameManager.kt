package fr.antoinehory.cinqmille.game

// Assurez-vous que Player, DiceRoller, GameRules, DiceRoll sont importés ou accessibles.
// import fr.antoinehory.cinqmille.game.Player
// import fr.antoinehory.cinqmille.game.DiceRoller
// import fr.antoinehory.cinqmille.game.GameRules
// import fr.antoinehory.cinqmille.game.DiceRoll // Si nécessaire pour GameEvent.PlayerBusted

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
    val MIN_SCORE_TO_OPEN = 750 // Conforme à GameRules par défaut
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

    fun handleRollAction(selectedIndices: List<Int>?): GameEvent {
        if (!gameInProgress || currentPlayer == null || !::turnManager.isInitialized) {
            return GameEvent.InvalidGameAction("Game not started or no current player/turn manager.")
        }
        val player = currentPlayer!!

        val turnEventResult = if (selectedIndices != null && selectedIndices.isNotEmpty()) {
            // MODIFIED: Pass processRestOfTurnAutomatically = true for standard roll/selection
            turnManager.selectDice(selectedIndices, processRestOfTurnAutomatically = true)
        } else {
            turnManager.rollDice()
        }

        return when (turnEventResult) {
            is TurnEvent.Busted -> {
                val diceAtBust = turnEventResult.diceAtBust
                // Player busts their turn, score for this turn is 0.
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

    fun currentTurnBankScore(pendingSelectedIndices: List<Int>? = null): GameEvent {
        if (!gameInProgress || currentPlayer == null || !::turnManager.isInitialized) {
            return GameEvent.InvalidGameAction("Game not started or no current player/turn manager.")
        }

        val player = currentPlayer!!

        if (pendingSelectedIndices != null && pendingSelectedIndices.isNotEmpty()) {
            // MODIFIED: Pass processRestOfTurnAutomatically = false for banking a selection
            val selectionResult = turnManager.selectDice(
                selectedIndices = pendingSelectedIndices,
                processRestOfTurnAutomatically = false
            )

            when (selectionResult) {
                is TurnEvent.Busted -> {
                    // Bust occurred while trying to select dice just before banking.
                    // The turnManager's internal score *might* have been updated before this bust,
                    // but a bust from selectDice implies the selection itself was problematic (scored 0).
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
                    // This shouldn't happen from selectDice.
                    return GameEvent.InvalidGameAction("Unexpected 'TurnEndedBanked' event from selectDice before banking.")
                }
            }
        }

        // Now, proceed to bank the score accumulated in the turnManager.
        val turnManagerBankEvent = turnManager.bankScore() // turnManagerEvent is TurnEvent.TurnEndedBanked or InvalidAction

        if (turnManagerBankEvent is TurnEvent.TurnEndedBanked) {
            val bankedScoreInTurn = turnManagerBankEvent.finalTurnScore // This is the actual score from TurnManager
            var gameEventToReturn: GameEvent

            player.lastKnownTurnScore = bankedScoreInTurn // Store what was actually banked this turn

            if (!player.hasOpened) {
                if (bankedScoreInTurn >= MIN_SCORE_TO_OPEN) {
                    player.hasOpened = true
                    player.totalScore += bankedScoreInTurn
                    gameEventToReturn = GameEvent.PlayerOpenedAndScored(player, bankedScoreInTurn, player.totalScore)
                } else {
                    // Player failed to open. bankedScoreInTurn is the score they *attempted* to open with.
                    // Their totalScore does not change.
                    gameEventToReturn = GameEvent.PlayerFailedToOpen(player, bankedScoreInTurn)
                }
            } else { // Player has already opened
                if (bankedScoreInTurn > 0) { // Only add positive score
                    player.totalScore += bankedScoreInTurn
                    gameEventToReturn = GameEvent.PlayerScored(player, bankedScoreInTurn, player.totalScore)
                } else {
                    // Player banked 0 or less (if possible) after opening. No change in total score.
                    // Still, a "PlayerScored" event might be relevant to signal turn end.
                    gameEventToReturn = GameEvent.PlayerScored(player, 0, player.totalScore)
                }
            }

            // Check for win condition only if a valid score affecting progression was made.
            // This means they successfully opened OR they were already open and banked > 0.
            val scoreContributesToWin = (player.hasOpened && bankedScoreInTurn >= MIN_SCORE_TO_OPEN && gameEventToReturn is GameEvent.PlayerOpenedAndScored) ||
                    (player.hasOpened && bankedScoreInTurn > 0 && gameEventToReturn is GameEvent.PlayerScored && gameEventToReturn.scoreThisTurn >0)


            if (scoreContributesToWin && player.totalScore >= TARGET_SCORE_TO_WIN) {
                gameInProgress = false
                return GameEvent.PlayerWon(player, player.totalScore)
            }

            if (gameInProgress) {
                moveToNextPlayer()
            }
            return gameEventToReturn

        } else if (turnManagerBankEvent is TurnEvent.InvalidAction) {
            // This can happen if bankScore() from TurnManager returns InvalidAction
            // (e.g. TurnManager's internal rules for banking 0).
            return GameEvent.InvalidGameAction("Cannot bank: ${turnManagerBankEvent.message}")
        } else {
            // Should not happen if TurnManager.bankScore() is correctly typed
            return GameEvent.InvalidGameAction("Unexpected event type from TurnManager.bankScore(): ${turnManagerBankEvent::class.simpleName}")
        }
    }

    fun calculatePreviewScore(diceValues: List<Int>): Int {
        return ScoreCalculator.calculateScore(diceValues)
    }

    private fun moveToNextPlayer() {
        if (players.isEmpty()) {
            return
        }
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size
        if (currentPlayer != null) {
            prepareTurnForCurrentPlayer()
        } else {
            gameInProgress = false
        }
    }
}
