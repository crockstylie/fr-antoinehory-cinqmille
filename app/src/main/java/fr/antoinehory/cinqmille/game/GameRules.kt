package fr.antoinehory.cinqmille.game

// Assurez-vous que DiceRoll est accessible (généralement un typealias pour List<Int>)
// typealias DiceRoll = List<Int> // Si ce n'est pas déjà défini ailleurs et importé

class GameRules(
    val openingScoreThreshold: Int = 500 // Seuil d'ouverture par défaut
) {

    // La méthode calculateScore a été SUPPRIMÉE.
    // Elle est maintenant dans ScoreCalculator.kt

    // La méthode canScoreFromRoll a été SUPPRIMÉE.
    // Elle est maintenant dans ScoreCalculator.kt (sous le nom canScore)

    /**
     * Logique pour déterminer si un joueur PEUT relancer après avoir marqué.
     * Le joueur peut relancer s'il reste des dés non sélectionnés ou si tous les dés du lancer
     * précédent ont été utilisés pour marquer (ce qui permet de relancer un nouveau set complet).
     *
     * @param diceAvailableForNextRoll Les dés qui restent (non sélectionnés) pour un prochain lancer.
     * @param allDiceFromPreviousRollScored True si tous les dés du lancer qui vient d'être joué
     *                                      ont été utilisés pour marquer des points.
     * @return True si le joueur peut relancer.
     */
    fun canRollAfterScoring(diceAvailableForNextRoll: DiceRoll, allDiceFromPreviousRollScored: Boolean): Boolean {
        // On peut relancer s'il reste des dés, OU si on a utilisé tous les dés (auquel cas on relance un set complet).
        return diceAvailableForNextRoll.isNotEmpty() || allDiceFromPreviousRollScored
    }
}
