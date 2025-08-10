package fr.antoinehory.cinqmille.game

// La data class Player(val id: Int, ...) est SUPPRIMÉE d'ici.
// Elle se trouve maintenant dans son propre fichier Player.kt dans le même package.
// Si d'autres classes comme DiceRoller, ScoreCalculator, TurnManager sont dans d'autres packages,
// des imports seraient nécessaires. Ici, on suppose qu'elles sont accessibles (même package ou importées).

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

    private val players = mutableListOf<Player>() // Utilise Player depuis fr.antoinehory.cinqmille.game.Player
    private var currentPlayerIndex: Int = -1
    private var turnManager: TurnManager = TurnManager(ScoreCalculator, diceRoller)

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
            // Player est maintenant la classe de Player.kt
            players.add(Player(id = i))
        }
        currentPlayerIndex = 0
        gameInProgress = true
        prepareTurnForCurrentPlayer()
        return GameEvent.GameStarted(players.toList(), players[currentPlayerIndex])
    }

    /**
     * Resets the [TurnManager] for the current player's turn.
     */
    private fun prepareTurnForCurrentPlayer() {
        turnManager = TurnManager(ScoreCalculator, diceRoller)
        // Notifier qu'un nouveau tour commence pour le joueur actuel
        // Ceci est géré par la logique appelante qui peut émettre PlayerTurnStarted
        // ou l'état du GameStarted le fait implicitement pour le premier joueur.
    }

    /**
     * Handles the current player's action to roll the dice.
     * Delegates to [TurnManager] to process the roll.
     *
     * @return A [GameEvent] representing the outcome of the roll (e.g., [GameEvent.CurrentTurnUpdated]
     *         with dice results, or [GameEvent.PlayerBusted] if the roll results in a bust that ends the turn).
     *         Returns [GameEvent.InvalidGameAction] if the game is not in progress.
     */
    fun currentTurnRollDice(): GameEvent {
        if (!gameInProgress || currentPlayer == null) {
            return GameEvent.InvalidGameAction("Game not started or no current player.")
        }
        val player = currentPlayer!! // Capture current player before potential turn change
        val turnEvent = turnManager.startOrContinueRoll()

        return when (turnEvent) {
            is TurnEvent.Busted -> { // Busted on the first roll of the turn or a re-roll
                // GameManager considère un bust du TurnManager comme la fin du tour du joueur.
                val bustedPlayer = player // Le joueur qui a busté
                moveToNextPlayer()
                GameEvent.PlayerBusted(bustedPlayer)
            }
            is TurnEvent.Rolled -> {
                // TODO: Detect instant win from turnEvent (e.g. 5 ones on first roll of turn)
                // This logic might belong in TurnManager or here after TurnManager.Rolled
                GameEvent.CurrentTurnUpdated(turnEvent)
            }
            else -> {
                // Pour les autres TurnEvents (Scored, InvalidAction, TurnEndedBanked)
                // qui ne devraient pas être le résultat direct de startOrContinueRoll
                // mais pourraient l'être si la logique de TurnManager est complexe.
                // On les encapsule dans CurrentTurnUpdated pour que le ViewModel puisse les gérer.
                GameEvent.CurrentTurnUpdated(turnEvent)
            }
        }
    }

    /**
     * Handles the current player's action to select dice after a roll.
     * Delegates to [TurnManager] to process the selection.
     *
     * @param selectedIndices The indices of the dice selected by the player from their current roll.
     * @return A [GameEvent] representing the outcome of the selection (e.g., [GameEvent.CurrentTurnUpdated]
     *         with new scores, or [GameEvent.PlayerBusted] if the selection results in a bust).
     *         Returns [GameEvent.InvalidGameAction] if the game is not in progress.
     */
    fun currentTurnSelectDice(selectedIndices: List<Int>): GameEvent {
        if (!gameInProgress || currentPlayer == null) {
            return GameEvent.InvalidGameAction("Game not started or no current player.")
        }
        val player = currentPlayer!!
        val turnEvent = turnManager.processPlayerSelection(selectedIndices)

        return when (turnEvent) {
            is TurnEvent.Busted -> {
                val bustedPlayer = player
                moveToNextPlayer()
                GameEvent.PlayerBusted(bustedPlayer)
            }
            is TurnEvent.Scored, is TurnEvent.Rolled, is TurnEvent.InvalidAction -> {
                // TODO: Detect instant win from turnEvent if selection completes a winning hand
                GameEvent.CurrentTurnUpdated(turnEvent)
            }
            is TurnEvent.TurnEndedBanked -> {
                // This shouldn't happen from processPlayerSelection.
                // It's an outcome of banking.
                GameEvent.InvalidGameAction("Unexpected TurnEndedBanked from dice selection.")
            }
        }
    }

    /**
     * Handles the current player's action to bank their current turn score.
     * Updates the player's total score and checks for game-winning conditions.
     *
     * @return A [GameEvent] representing the outcome (e.g., [GameEvent.PlayerScored],
     *         [GameEvent.PlayerOpenedAndScored], [GameEvent.PlayerFailedToOpen], [GameEvent.PlayerWon],
     *         or [GameEvent.PlayerBusted] if banking isn't possible and results in a bust).
     *         Returns [GameEvent.InvalidGameAction] if the game is not in progress.
     */
    fun currentTurnBankScore(): GameEvent {
        if (!gameInProgress || currentPlayer == null) {
            return GameEvent.InvalidGameAction("Game not started or no current player.")
        }

        val player = currentPlayer!!
        val bankEventFromTurnManager = turnManager.playerBanksScore()

        return when (bankEventFromTurnManager) {
            is TurnEvent.TurnEndedBanked -> {
                val bankedScore = bankEventFromTurnManager.finalTurnScore
                var playerEvent: GameEvent // To hold the specific event for the player

                // Update lastKnownTurnScore before totalScore for win condition accuracy
                player.lastKnownTurnScore = bankedScore

                if (!player.hasOpened) {
                    if (bankedScore >= MIN_SCORE_TO_OPEN) {
                        player.hasOpened = true
                        player.totalScore += bankedScore
                        playerEvent = GameEvent.PlayerOpenedAndScored(player, bankedScore, player.totalScore)
                    } else {
                        // Score non suffisant pour ouvrir, le joueur perd son tour et son score de tour.
                        playerEvent = GameEvent.PlayerFailedToOpen(player, bankedScore)
                    }
                } else { // Player has already opened
                    player.totalScore += bankedScore
                    playerEvent = GameEvent.PlayerScored(player, bankedScore, player.totalScore)
                }

                // Check for win condition only if the player successfully banked (opened or scored)
                if ((player.hasOpened && playerEvent !is GameEvent.PlayerFailedToOpen) && player.totalScore >= TARGET_SCORE_TO_WIN) {
                    gameInProgress = false // Game ends
                    // PlayerWon event should use the score from player.lastKnownTurnScore if that's the rule,
                    // or player.totalScore. GameManager's PlayerWon uses totalScore.
                    return GameEvent.PlayerWon(player, player.totalScore)
                }

                // If no win, move to next player (unless it was a failed open attempt, where the turn also ends)
                moveToNextPlayer()
                playerEvent // Return PlayerOpenedAndScored, PlayerScored, or PlayerFailedToOpen
            }
            is TurnEvent.Busted -> { // Banking resulted in a bust (e.g., trying to bank 0 points after busting)
                val bustedPlayer = player
                moveToNextPlayer()
                GameEvent.PlayerBusted(bustedPlayer)
            }
            is TurnEvent.InvalidAction -> {
                // Player tried to bank when not allowed (e.g. 0 points and not busted yet)
                // The turn doesn't necessarily end here. Let ViewModel decide based on message.
                GameEvent.CurrentTurnUpdated(bankEventFromTurnManager)
            }
            is TurnEvent.Rolled, is TurnEvent.Scored -> {
                // These should not occur from playerBanksScore().
                GameEvent.InvalidGameAction("Unexpected event type (${bankEventFromTurnManager::class.simpleName}) from banking action.")
            }
        }
    }

    /**
     * Moves to the next player in the list.
     * If the game is not in progress, this method does nothing.
     * Prepares the [TurnManager] for the new player's turn.
     * Note: This method itself does not emit a GameEvent for player turn started;
     * that's typically handled by the calling context or the ViewModel reacting to the previous player's turn ending.
     */
    private fun moveToNextPlayer() {
        if (players.isEmpty() || !gameInProgress) {
            return
        }
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size
        prepareTurnForCurrentPlayer()
        // Consider if a GameEvent.PlayerTurnStarted should be emitted here
        // or if the ViewModel should infer this from the previous event.
        // For now, ViewModel infers or reacts to specific end-of-turn events.
    }
}
