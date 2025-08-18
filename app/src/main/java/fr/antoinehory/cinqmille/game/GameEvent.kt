package fr.antoinehory.cinqmille.game

/**
 * Represents all possible events that can occur at the overall game level,
 * managed by [GameManager].
 * These events are typically observed by the UI layer (e.g., ViewModel) to react to game state changes.
 */
sealed class GameEvent {
    /**
     * Indicates that the game has started.
     * @param players The list of all players in the game.
     * @param firstPlayer The player who will take the first turn.
     */
    data class GameStarted(val players: List<Player>, val firstPlayer: Player) : GameEvent()

    /**
     * Indicates the start of a new player's turn.
     * @param player The player whose turn it is now.
     */
    data class PlayerTurnStarted(val player: Player) : GameEvent()

    /**
     * Event emitted when the active player performs an action managed by [TurnManager]
     * (e.g., rolling dice, selecting dice).
     * The UI can listen to this event to update the display of dice, current turn score, etc.
     * @param turnEvent The original [TurnEvent] from [TurnManager] that triggered this update.
     */
    data class CurrentTurnUpdated(val turnEvent: TurnEvent) : GameEvent()

    /**
     * Indicates that a player has successfully scored points and "opened" their score for the first time.
     * @param player The concerned player.
     * @param scoreThisTurn The score achieved in the current turn that allowed opening.
     * @param newTotalScore The player's new total score after this turn.
     */
    data class PlayerOpenedAndScored(val player: Player, val scoreThisTurn: Int, val newTotalScore: Int) : GameEvent()

    /**
     * Indicates that a player (who had already opened) has successfully scored more points.
     * @param player The concerned player.
     * @param scoreThisTurn The score achieved in the current turn.
     * @param newTotalScore The player's new total score after this turn.
     */
    data class PlayerScored(val player: Player, val scoreThisTurn: Int, val newTotalScore: Int) : GameEvent()

    /**
     * Indicates that a player attempted to bank their score but did not meet the
     * required score to open. Their score for the current turn is lost.
     * @param player The concerned player.
     * @param scoreAttemptedThisTurn The score the player tried to bank but was insufficient to open.
     */
    data class PlayerFailedToOpen(val player: Player, val scoreAttemptedThisTurn: Int) : GameEvent()

    /**
     * Indicates that the player's turn ended in a "bust" (no score achieved or invalid selection).
     * Their score for the current turn is lost.
     * @param player The player who busted.
     */
    data class PlayerBusted(val player: Player) : GameEvent()

    /**
     * Indicates that a player has won the game by reaching or exceeding the target score.
     * @param winner The player who won.
     * @param finalScore The winner's final total score.
     */
    data class PlayerWon(val winner: Player, val finalScore: Int) : GameEvent()

    /**
     * Indicates that an action attempted on [GameManager] is invalid
     * (e.g., trying to play when the game hasn't started, or an invalid number of players).
     * @param message A description of the error or invalid action.
     */
    data class InvalidGameAction(val message: String) : GameEvent()
}