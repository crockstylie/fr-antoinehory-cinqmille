package fr.antoinehory.cinqmille.game

data class Player(
    val id: Int,
    var totalScore: Int = 0,
    var hasOpened: Boolean = false
)

class GameManager(private val diceRoller: DiceRoller) {

    private val players = mutableListOf<Player>()
    private var currentPlayerIndex: Int = -1
    private var turnManager: TurnManager = TurnManager(ScoreCalculator, diceRoller)

    val MIN_SCORE_TO_OPEN = 750
    val TARGET_SCORE_TO_WIN = 5000

    val currentPlayer: Player?
        get() = players.getOrNull(currentPlayerIndex)

    val allPlayers: List<Player>
        get() = players.toList()

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
        prepareTurnForCurrentPlayer()
        return GameEvent.GameStarted(players.toList(), players[currentPlayerIndex])
    }

    private fun prepareTurnForCurrentPlayer() {
        turnManager = TurnManager(ScoreCalculator, diceRoller)
    }

    fun currentTurnRollDice(): GameEvent {
        if (!gameInProgress || currentPlayer == null) {
            return GameEvent.InvalidGameAction("Game not started or no current player.")
        }
        val player = currentPlayer!! // Capture current player before potential turn change
        val turnEvent = turnManager.startOrContinueRoll()

        if (turnEvent is TurnEvent.Busted) {
            moveToNextPlayer()
            return GameEvent.PlayerBusted(player) // Event refers to the player who busted
        }

        // TODO: Detect instant win from turnEvent (e.g. 5 ones on first roll of turn)
        return GameEvent.CurrentTurnUpdated(turnEvent)
    }

    fun currentTurnSelectDice(selectedIndices: List<Int>): GameEvent {
        if (!gameInProgress || currentPlayer == null) {
            return GameEvent.InvalidGameAction("Game not started or no current player.")
        }
        val player = currentPlayer!! // Capture current player
        val turnEvent = turnManager.processPlayerSelection(selectedIndices)

        if (turnEvent is TurnEvent.Busted) {
            moveToNextPlayer()
            return GameEvent.PlayerBusted(player) // Event refers to the player who busted
        }
        // TODO: Detect instant win from turnEvent if selection completes a winning hand
        return GameEvent.CurrentTurnUpdated(turnEvent)
    }

    fun currentTurnBankScore(): GameEvent {
        if (!gameInProgress || currentPlayer == null) {
            return GameEvent.InvalidGameAction("Game not started or no current player.")
        }

        val player = currentPlayer!!
        val bankEventFromTurnManager = turnManager.playerBanksScore()

        return when (bankEventFromTurnManager) {
            is TurnEvent.TurnEndedBanked -> {
                val bankedScore = bankEventFromTurnManager.finalTurnScore
                var playerEvent: GameEvent

                if (!player.hasOpened) {
                    if (bankedScore >= MIN_SCORE_TO_OPEN) {
                        player.hasOpened = true
                        player.totalScore += bankedScore
                        playerEvent = GameEvent.PlayerOpenedAndScored(player, bankedScore, player.totalScore)
                    } else {
                        playerEvent = GameEvent.PlayerFailedToOpen(player, bankedScore)
                    }
                } else {
                    player.totalScore += bankedScore
                    playerEvent = GameEvent.PlayerScored(player, bankedScore, player.totalScore)
                }

                if (player.hasOpened && player.totalScore >= TARGET_SCORE_TO_WIN) {
                    gameInProgress = false
                    return GameEvent.PlayerWon(player, player.totalScore)
                }

                moveToNextPlayer()
                playerEvent
            }
            is TurnEvent.Busted -> {
                moveToNextPlayer()
                GameEvent.PlayerBusted(player)
            }
            is TurnEvent.InvalidAction -> {
                GameEvent.CurrentTurnUpdated(bankEventFromTurnManager)
            }
            // ELSE BRANCH TO MAKE 'when' EXHAUSTIVE
            else -> {
                // These cases (Rolled, Scored) should not logically occur from playerBanksScore().
                // Handle as an invalid or unexpected state.
                GameEvent.InvalidGameAction("Unexpected event type from banking: ${bankEventFromTurnManager::class.simpleName}")
            }
        }
    }

    private fun moveToNextPlayer() {
        if (players.isEmpty() || !gameInProgress) {
            return
        }
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size
        prepareTurnForCurrentPlayer()
    }
}