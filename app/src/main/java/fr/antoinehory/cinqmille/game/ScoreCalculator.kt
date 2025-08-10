package fr.antoinehory.cinqmille.game

/**
 * Utility object for calculating scores in the 5000 game.
 *
 * This object provides functions to calculate the score based on a set of dice
 * according to the game's rules.
 * All functions within this object should be accompanied by KDoc and unit tests.
 */
object ScoreCalculator {

    //region Score Constants
    internal const val SCORE_ONE = 100
    internal const val SCORE_FIVE = 50
    internal const val SCORE_THREE_ONES = 1000
    internal const val SCORE_THREE_TWOS = 200
    internal const val SCORE_THREE_THREES = 300
    internal const val SCORE_THREE_FOURS = 400
    internal const val SCORE_THREE_FIVES = 500
    internal const val SCORE_THREE_SIXES = 600
    internal const val SCORE_STRAIGHT_1_TO_5 = 500
    internal const val SCORE_STRAIGHT_2_TO_6 = 500
    // Full (A,A,B,B,B) score = pairValue * brelanValue * 100
    internal const val SCORE_FIVE_ONES_GAME_WIN = 5000
    internal const val SCORE_FIVE_FIVES = 5000 // Typically means game win or a very high score
    //endregion

    /**
     * Calculates the score for a given list of dice values.
     *
     * The input list should represent the face values of the rolled dice.
     * Dice used in a combination are not scored again individually.
     * The function prioritizes higher-scoring combinations based on the following order:
     * 1. Five "1"s (instant win).
     * 2. Five "5"s (high score, typically concludes roll for those dice).
     * 3. Brelan of "1"s (1000 points).
     * 4. Fulls (e.g., A,A,B,B,B where A is the pair, B is the brelan). Score = val(A) * val(B) * 100.
     * 5. Straights (Suites 1-5 or 2-6).
     * 6. Other Brelans (three of a kind for 2s, 3s, 4s, 5s, 6s).
     * 7. Single "1"s and "5"s.
     *
     * @param dice A list of integers representing the face values of the rolled dice.
     *             It's assumed these are valid dice values (1-6).
     *             The list can contain a variable number of dice (e.g., 5 or 6).
     * @return The calculated score for the given dice. Returns 0 if no scoring combination is found
     *         or if the input is empty.
     */
    fun calculateScore(dice: List<Int>): Int {
        if (dice.isEmpty()) {
            return 0
        }
        require(dice.all { it in 1..6 }) { "Dice values must be between 1 and 6." }

        val workingDice = dice.toMutableList()
        var score = 0

        // Priority 1 & 2: Five of a kind (special handling, may return early)
        val initialCounts = workingDice.groupingBy { it }.eachCount()
        if (initialCounts.getOrDefault(1, 0) >= 5) {
            return SCORE_FIVE_ONES_GAME_WIN
        }
        if (initialCounts.getOrDefault(5, 0) >= 5) {
            return SCORE_FIVE_FIVES
        }

        // Priority 3: Brelan of "1"s (1000 points)
        val onesCount = workingDice.count { it == 1 }
        if (onesCount >= 3) {
            score += SCORE_THREE_ONES
            repeat(3) { workingDice.remove(1) }
        }

        // Priority 4: Fulls (A,A,B,B,B)
        // A Full requires at least 5 dice, but some might have been used by Brelan of 1s.
        // The scoreFullsAndRemoveDice function itself checks if workingDice.size < 5.
        score += scoreFullsAndRemoveDice(workingDice)

        // Priority 5: Straights (Suites 1-5 or 2-6)
        if (workingDice.size >= 5) {
            val currentDiceCountsForStraights = workingDice.groupingBy { it }.eachCount()
            val straight1To5Values = listOf(1, 2, 3, 4, 5)
            val straight2To6Values = listOf(2, 3, 4, 5, 6)
            var straightFound = false

            if (straight1To5Values.all { currentDiceCountsForStraights.getOrDefault(it, 0) >= 1 }) {
                score += SCORE_STRAIGHT_1_TO_5
                straight1To5Values.forEach { dieValue -> workingDice.remove(dieValue) }
                straightFound = true
            }

            if (!straightFound && workingDice.size >= 5) { // Check again for size if 1-5 straight removed dice
                val countsFor2To6 = workingDice.groupingBy { it }.eachCount()
                if (straight2To6Values.all { countsFor2To6.getOrDefault(it, 0) >= 1 }) {
                    score += SCORE_STRAIGHT_2_TO_6
                    straight2To6Values.forEach { dieValue -> workingDice.remove(dieValue) }
                }
            }
        }

        // Priority 6: Other Brelans (2s, 3s, 4s, 5s, 6s)
        val otherBrelanScoresMap = mapOf(
            // 1 is handled separately above
            2 to SCORE_THREE_TWOS, 3 to SCORE_THREE_THREES,
            4 to SCORE_THREE_FOURS, 5 to SCORE_THREE_FIVES, 6 to SCORE_THREE_SIXES
        )
        // Order of checking other brelans (e.g., high to low, or specific if needed)
        val dieValuesForOtherBrelanCheck = listOf(6, 5, 4, 3, 2) 

        for (value in dieValuesForOtherBrelanCheck) {
            val count = workingDice.count { it == value }
            if (count >= 3) {
                score += otherBrelanScoresMap[value] ?: 0
                repeat(3) { workingDice.remove(value) }
            }
        }
        
        // Priority 7: Score remaining individual "1"s and "5"s
        // Make a copy to iterate over, as workingDice is modified
        val diceForSinglesScoring = workingDice.toList() 
        for (dieValue in diceForSinglesScoring) {
            if (dieValue == 1) {
                score += SCORE_ONE
                workingDice.remove(1) 
            } else if (dieValue == 5) {
                score += SCORE_FIVE
                workingDice.remove(5)
            }
        }

        return score
    }

    /**
     * Attempts to find and score a "Full" (a Brelan and a Pair) from the given dice.
     * If a Full is found, its score is calculated as `pairValue * brelanValue * 100`,
     * the 5 corresponding dice are removed from `workingDice`, and the score is returned.
     * This function should be called *after* any higher priority combinations (like Brelan of 1s)
     * have been processed and their dice removed from `workingDice`.
     *
     * @param workingDice The mutable list of current dice values. Dice used for a Full will be removed.
     * @return The score of the Full if found, otherwise 0.
     */
    private fun scoreFullsAndRemoveDice(workingDice: MutableList<Int>): Int {
        if (workingDice.size < 5) return 0

        val counts = workingDice.groupingBy { it }.eachCount()

        // Iterate for Brelan value (brelanValue)
        for (brelanValue in 1..6) {
            if (counts.getOrDefault(brelanValue, 0) >= 3) {
                // Iterate for Pair value (pairValue), must be different from brelanValue
                for (pairValue in 1..6) {
                    if (pairValue == brelanValue) continue
                    if (counts.getOrDefault(pairValue, 0) >= 2) {
                        // Full found: three of brelanValue, two of pairValue
                        val fullScore = pairValue * brelanValue * 100
                        
                        // Remove dice for the full
                        repeat(3) { workingDice.remove(brelanValue) }
                        repeat(2) { workingDice.remove(pairValue) }
                        
                        return fullScore
                    }
                }
            }
        }
        return 0 // No Full found
    }

    /**
     * Checks if a given dice roll can score any points.
     * This function does not calculate the actual score, but only determines
     * if there's at least one scorable combination or individual die.
     *
     * @param dice The list of dice values to check.
     * @return `true` if the roll can score points, `false` otherwise.
     */
    fun canScore(dice: List<Int>): Boolean {
        if (dice.isEmpty()) {
            return false
        }
        // A simple way to check if any score is possible is to actually calculate it.
        // If the score is greater than 0, then it's possible to score.
        // This leverages the existing calculateScore logic which handles all combinations.
        return calculateScore(dice) > 0
    }
}
