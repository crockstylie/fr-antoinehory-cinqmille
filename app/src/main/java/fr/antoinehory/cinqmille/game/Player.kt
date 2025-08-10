package fr.antoinehory.cinqmille.game

/**
 * Represents a player in the Cinq Mille game.
 *
 * @property id The unique identifier for the player.
 * @property totalScore The player's current total score accumulated across all turns. Mutable.
 * @property hasOpened True if the player has met the initial score requirement to "open", false otherwise. Mutable.
 * @property lastKnownTurnScore Stores the score achieved by the player in their final turn,
 *                              particularly useful if this turn resulted in a win. Mutable.
 */
data class Player(
    val id: Int,
    var totalScore: Int = 0,
    var hasOpened: Boolean = false,
    var lastKnownTurnScore: Int = 0 // Utilisé pour savoir combien le joueur a marqué au tour où il a gagné
) {
    // On pourrait ajouter des fonctions ici si un joueur avait des actions spécifiques
    // Par exemple, une fonction pour mettre à jour le score en respectant certaines règles.
    // fun updateScore(pointsGagnes: Int) {
    //     this.totalScore += pointsGagnes
    //     // Logique pour hasOpened si ce score permet d'ouvrir, etc.
    // }
}
