package fr.antoinehory.cinqmille.game

sealed class GameEvent {
    /**
     * Indique que la partie a commencé.
     * @param players La liste de tous les joueurs.
     * @param firstPlayer Le joueur dont c'est le premier tour.
     */
    data class GameStarted(val players: List<Player>, val firstPlayer: Player) : GameEvent()

    /**
     * Indique le début du tour d'un joueur.
     * @param player Le joueur dont c'est le tour.
     */
    data class PlayerTurnStarted(val player: Player) : GameEvent()

    /**
     * Événement émis lorsque le joueur actif effectue une action gérée par TurnManager (lancer, sélectionner).
     * L'UI peut écouter cet événement pour mettre à jour l'affichage des dés, du score du tour, etc.
     * @param turnEvent L'événement original de TurnManager.
     */
    data class CurrentTurnUpdated(val turnEvent: TurnEvent) : GameEvent()

    /**
     * Indique qu'un joueur a marqué des points et a réussi à "ouvrir" son score.
     * @param player Le joueur concerné.
     * @param scoreThisTurn Le score marqué dans ce tour.
     * @param newTotalScore Le score total du joueur après ce tour.
     */
    data class PlayerOpenedAndScored(val player: Player, val scoreThisTurn: Int, val newTotalScore: Int) : GameEvent()

    /**
     * Indique qu'un joueur (qui avait déjà ouvert) a marqué des points.
     * @param player Le joueur concerné.
     * @param scoreThisTurn Le score marqué dans ce tour.
     * @param newTotalScore Le score total du joueur après ce tour.
     */
    data class PlayerScored(val player: Player, val scoreThisTurn: Int, val newTotalScore: Int) : GameEvent()

    /**
     * Indique qu'un joueur a essayé de "banker" mais n'a pas atteint le score requis pour ouvrir.
     * Son score pour ce tour est perdu.
     * @param player Le joueur concerné.
     * @param scoreAttemptedThisTurn Le score que le joueur a tenté de banker.
     */
    data class PlayerFailedToOpen(val player: Player, val scoreAttemptedThisTurn: Int) : GameEvent()

    /**
     * Indique que le tour du joueur s'est terminé par un "bust".
     * Son score pour ce tour est perdu.
     * @param player Le joueur concerné.
     */
    data class PlayerBusted(val player: Player) : GameEvent()

    /**
     * Indique qu'un joueur a gagné la partie.
     * @param winner Le joueur gagnant.
     * @param finalScore Le score final du gagnant.
     */
    data class PlayerWon(val winner: Player, val finalScore: Int) : GameEvent()
    
    /**
     * Indique qu'une action tentée sur GameManager est invalide (ex: essayer de jouer alors que la partie n'a pas commencé).
     * @param message Description de l'erreur.
     */
    data class InvalidGameAction(val message: String) : GameEvent()
}
