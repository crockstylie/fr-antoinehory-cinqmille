package fr.antoinehory.cinqmille.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse // Added for canScore
import org.junit.Assert.assertTrue // Added for canScore
import org.junit.Test

/**
 * Unit tests for the [ScoreCalculator] object.
 */
class ScoreCalculatorTest {

    // --- calculateScore Tests Start (existing tests) ---
    @Test
    fun `calculateScore with empty dice list should return 0`() {
        val dice = emptyList<Int>()
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(0, score)
    }

    @Test
    fun `calculateScore with single 1 should return 100`() {
        val dice = listOf(1)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(100, score)
    }

    @Test
    fun `calculateScore with single 5 should return 50`() {
        val dice = listOf(5)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(50, score)
    }

    @Test
    fun `calculateScore with non-scoring dice should return 0`() {
        val dice = listOf(2, 3, 4, 6)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(0, score)
    }

    // --- Brelans (Three of a kind) ---
    @Test
    fun `calculateScore with three 1s should return 1000`() {
        val dice = listOf(1, 1, 1)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(1000, score)
    }

    @Test
    fun `calculateScore with three 2s should return 200`() {
        val dice = listOf(2, 2, 2)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(200, score)
    }

    @Test
    fun `calculateScore with three 3s should return 300`() {
        val dice = listOf(3, 3, 3)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(300, score)
    }

    @Test
    fun `calculateScore with three 4s should return 400`() {
        val dice = listOf(4, 4, 4)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(400, score)
    }

    @Test
    fun `calculateScore with three 5s should return 500`() {
        val dice = listOf(5, 5, 5)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(500, score)
    }

    @Test
    fun `calculateScore with three 6s should return 600`() {
        val dice = listOf(6, 6, 6)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(600, score)
    }

    // --- Five of a kind ---
    @Test
    fun `calculateScore with five 1s should return 5000`() {
        val dice = listOf(1, 1, 1, 1, 1)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(5000, score)
    }

    @Test
    fun `calculateScore with five 5s should return 5000`() { // Assuming five 5s also trigger game win score
        val dice = listOf(5, 5, 5, 5, 5)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(5000, score) // Corrected: Use literal 5000
    }

    @Test
    fun `calculateScore with five 2s should return 200`() { // Scores as Brelan of 2s
        val dice = listOf(2, 2, 2, 2, 2)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(200, score)
    }

    @Test
    fun `calculateScore with five 3s should return 300`() { // Scores as Brelan of 3s
        val dice = listOf(3, 3, 3, 3, 3)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(300, score)
    }

    @Test
    fun `calculateScore with five 4s should return 400`() { // Scores as Brelan of 4s
        val dice = listOf(4, 4, 4, 4, 4)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(400, score)
    }

    @Test
    fun `calculateScore with five 6s should return 600`() { // Scores as Brelan of 6s
        val dice = listOf(6, 6, 6, 6, 6)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(600, score)
    }


    // --- Straights ---
    @Test
    fun `calculateScore with straight 1-2-3-4-5 should return 500`() {
        val dice = listOf(1, 2, 3, 4, 5)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(ScoreCalculator.SCORE_STRAIGHT_1_TO_5, score)
    }

    @Test
    fun `calculateScore with straight 2-3-4-5-6 should return 500`() {
        val dice = listOf(2, 3, 4, 5, 6)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(ScoreCalculator.SCORE_STRAIGHT_2_TO_6, score)
    }

    @Test
    fun `calculateScore with 1-2-3-4-5-6 should score straight 1-5 and remaining 6 (no score)`() {
        val dice = listOf(1, 2, 3, 4, 5, 6) // Assumes 6 dice can be thrown
        val score = ScoreCalculator.calculateScore(dice)
        // Expected: Straight 1-5 (500) + 6 (0) = 500
        assertEquals(ScoreCalculator.SCORE_STRAIGHT_1_TO_5, score)
    }

    // --- Fulls and Priority Tests ---
    @Test
    fun `calculateScore with full (2,2,3,3,3) should return 600`() {
        val dice = listOf(2, 2, 3, 3, 3)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(600, score) // 2 * 3 * 100
    }

    @Test
    fun `calculateScore with full (4,4,4,6,6) should return 2400`() {
        val dice = listOf(4, 4, 4, 6, 6)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(2400, score) // 4 * 6 * 100
    }

    @Test
    fun `calculateScore with full (5,5,5,2,2) should return 1000`() {
        val dice = listOf(5, 5, 5, 2, 2)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(1000, score) // 5 * 2 * 100
    }

    @Test
    fun `calculateScore with full (2,2,2,1,1) should return 200`() { // Special full with 1s: Brelan * 100
        val dice = listOf(2, 2, 2, 1, 1)
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(200, score) // Brelan of 2s (200) + two 1s are not used for full mult, just brelan stands.
        // Or if it's 200 * 1 * 100 = 20000, that's very high.
        // Based on other fulls, if 1 is the PAIR, it's BrelanValue * 100
        // If Brelan of 1s (1000) with pair of 2s (2,2) -> 1000 + nothing for pair of 2s or 1000*2*100 (unlikely)
        // This needs clarification on how fulls with 1s are scored if 1s are the pair.
        // Test for (1,1,1,2,2) below scores 1100 (1000 + 2*50 -> no, should be 1000 + nothing for 2,2)
        // Let's assume brelan value stands, and the pair of 1s add 200 IF they were not part of brelan of 1s.
        // If the brelan is NOT 1s: Brelan_Value (e.g. 200 for 2s) + if pair is 1s, add 200.
        // If dice = (2,2,2,1,1) -> brelan(2,2,2)=200. Pair (1,1)=200. Total = 400. This is different from existing test.
        // The existing test for (2,2,2,1,1) asserts 200. This means the two 1s DON'T score additionally.
        // OK, current logic seems to be: if full detected, Brelan * PairValue * 100.
        // If pairValue is 1, then BrelanValue * 1 * 100. So for (2,2,2,1,1) -> 2 * 1 * 100 = 200. This matches.
        assertEquals(200, score)
    }

    @Test
    fun `calculateScore with (1,1,1,5,5) should prioritize Brelan of 1 and score 1100`() {
        val dice = listOf(1, 1, 1, 5, 5)
        val score = ScoreCalculator.calculateScore(dice)
        // Expected: Brelan of 1 (1000) + two 5s (2 * 50 = 100) = 1100
        assertEquals(1100, score)
    }

    @Test
    fun `calculateScore with (1,1,1,2,2,2) should score Brelan of 1 and Brelan of 2 for 1200`() {
        val dice = listOf(1, 1, 1, 2, 2, 2) // Assumes 6 dice
        val score = ScoreCalculator.calculateScore(dice)
        // Expected: Brelan of 1 (1000) + Brelan of 2 (200) = 1200
        assertEquals(1200, score)
    }

    @Test
    fun `calculateScore with (1,1,1,1,5) should score Brelan of 1 and remaining 1 and 5 for 1150`() {
        val dice = listOf(1, 1, 1, 1, 5)
        val score = ScoreCalculator.calculateScore(dice)
        // Expected: Brelan of 1 (1000) + one 1 (100) + one 5 (50) = 1150
        assertEquals(1150, score)
    }

    @Test
    fun `calculateScore with (5,5,5,5,1) should score Brelan of 5 and remaining 5 and 1 for 650`() {
        val dice = listOf(5, 5, 5, 5, 1)
        val score = ScoreCalculator.calculateScore(dice)
        // Expected: Brelan of 5 (500) + one 5 (50) + one 1 (100) = 650
        assertEquals(650, score)
    }

    @Test
    fun `calculateScore with (1,1,1,1,1,5) should score Five 1s for 5000`() { // Overrides Brelan of 1 + 1 + 1 + 5
        val dice = listOf(1, 1, 1, 1, 1, 5) // Assumes 6 dice
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(ScoreCalculator.SCORE_FIVE_ONES_GAME_WIN, score)
    }

    @Test
    fun `calculateScore with straight 1-2-3-4-5 and two 6s should score 500`(){
        val dice = listOf(1,2,3,4,5,6,6) // 7 dice, straight has priority
        val score = ScoreCalculator.calculateScore(dice)
        assertEquals(ScoreCalculator.SCORE_STRAIGHT_1_TO_5, score)
    }
    // --- calculateScore Tests End ---

    // --- Tests for canScore() ---
    @Test
    fun `canScore with empty dice list should return false`() {
        assertFalse(ScoreCalculator.canScore(emptyList()))
    }

    @Test
    fun `canScore with single 1 should return true`() {
        assertTrue(ScoreCalculator.canScore(listOf(1)))
    }

    @Test
    fun `canScore with single 5 should return true`() {
        assertTrue(ScoreCalculator.canScore(listOf(5)))
    }

    @Test
    fun `canScore with non-scoring dice should return false`() {
        assertFalse(ScoreCalculator.canScore(listOf(2, 3, 4, 6)))
    }

    @Test
    fun `canScore with three 2s should return true`() {
        assertTrue(ScoreCalculator.canScore(listOf(2, 2, 2)))
    }

    @Test
    fun `canScore with straight 1-2-3-4-5 should return true`() {
        assertTrue(ScoreCalculator.canScore(listOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun `canScore with five 1s should return true`() {
        assertTrue(ScoreCalculator.canScore(listOf(1, 1, 1, 1, 1)))
    }

    @Test
    fun `canScore with five 2s should return true`() { // Brelan of 2s can score
        assertTrue(ScoreCalculator.canScore(listOf(2, 2, 2, 2, 2)))
    }

    @Test
    fun `canScore with complex scoring hand should return true`() {
        assertTrue(ScoreCalculator.canScore(listOf(1, 1, 1, 5, 2))) // Brelan of 1 + 5
    }

    @Test
    fun `canScore with complex non-scoring hand should return false`() {
        assertFalse(ScoreCalculator.canScore(listOf(2, 2, 3, 4, 6))) // No 1s, 5s, no brelans of value
    }
    // --- canScore() Tests End ---
}

