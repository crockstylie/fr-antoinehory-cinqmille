package fr.antoinehory.cinqmille.game

// Assurez-vous que DiceRoll est accessible (généralement un typealias pour List<Int>)
// typealias DiceRoll = List<Int> // Si ce n'est pas déjà défini ailleurs et importé

class GameRules(
    val openingScoreThreshold: Int = 500 // Seuil d'ouverture par défaut
) {

    /**
     * Calcule le score pour une sélection de dés donnée.
     * C'est une implémentation très basique. Les vraies règles sont plus complexes.
     * - Un 1 vaut 100 points.
     * - Un 5 vaut 50 points.
     * - Trois dés identiques (brelan) valent 100 * la valeur du dé (sauf pour les 1 qui valent 1000).
     * - D'autres combinaisons (suites, etc.) ne sont pas gérées ici pour la simplicité.
     */
    fun calculateScore(dice: DiceRoll): Int {
        if (dice.isEmpty()) return 0

        var score = 0
        val counts = dice.groupingBy { it }.eachCount()

        // Gérer les brelans en premier (simplification)
        for ((dieValue, count) in counts) {
            if (count >= 3) {
                score += when (dieValue) {
                    1 -> 1000 // Brelan de 1
                    else -> dieValue * 100 // Brelan des autres
                }
                // Pour cette version simple, on retire les dés du brelan pour éviter double comptage
                // avec les 1 et 5 individuels. Une vraie logique serait plus fine.
                val diceWithoutBrelan = dice.toMutableList()
                repeat(3) { diceWithoutBrelan.remove(dieValue) }
                return score + calculateScore(diceWithoutBrelan) // Appel récursif pour les dés restants
            }
        }

        // Gérer les 1 et 5 individuels (s'ils ne font pas partie d'un brelan déjà compté)
        for (die in dice) {
            when (die) {
                1 -> score += 100
                5 -> score += 50
            }
        }
        return score
    }

    /**
     * Vérifie si un lancer de dés donné contient au moins une combinaison qui peut marquer des points.
     * Pour cette version simple, on vérifie juste si un appel à calculateScore sur n'importe quel sous-ensemble
     * non vide (ou le set entier) donnerait un score.
     * Une approche plus performante analyserait directement les combinaisons possibles.
     */
    fun canScoreFromRoll(dice: DiceRoll): Boolean {
        if (dice.isEmpty()) return false

        // Test simple : si le calcul direct donne un score, c'est bon.
        if (calculateScore(dice) > 0) return true

        // Pour une logique plus complète, il faudrait vérifier tous les sous-ensembles possibles.
        // Ici, on peut se contenter d'une vérification basique :
        // Y a-t-il au moins un 1 ou un 5 ? Ou un brelan ?
        // Cette fonction est un placeholder et devrait être implémentée avec les vraies règles de scoring.
        val counts = dice.groupingBy { it }.eachCount()
        if (counts.containsKey(1) || counts.containsKey(5)) return true
        if (counts.any { it.value >= 3 }) return true // Vérifie s'il y a un brelan potentiel

        return false // Placeholder, une vraie logique est nécessaire
    }

    /**
     * Logique (placeholder) pour déterminer si un joueur PEUT relancer après avoir marqué.
     * Dans le Cinq Mille, on peut généralement toujours relancer si on a marqué et qu'il reste des dés
     * ou si on a marqué avec tous les dés.
     *
     * @param lastScoredRoll Les dés qui viennent d'être utilisés pour marquer.
     * @param diceAvailableForNextRoll Les dés qui restent pour un prochain lancer.
     * @return True si le joueur peut relancer.
     */
    fun canRollAfterScoring(lastScoredRoll: DiceRoll, diceAvailableForNextRoll: DiceRoll): Boolean {
        // Si on a marqué (ce qui est implicite si cette fonction est appelée après un score),
        // on peut relancer s'il reste des dés, OU si on a utilisé tous les dés (auquel cas on relance 5 nouveaux dés).
        return diceAvailableForNextRoll.isNotEmpty() || lastScoredRoll.size == initialDiceCount // Simplification
    }

    // Nombre de dés initial pour un tour ou après avoir marqué avec tous les dés.
    val initialDiceCount: Int = 5 // Redondant avec TurnManager, mais peut être utile ici aussi.
}
