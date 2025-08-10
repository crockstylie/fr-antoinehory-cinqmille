package fr.antoinehory.cinqmille.game

import org.junit.Assert.*
import org.junit.Test

class GameTurnTest {

    // Tests for selectDiceFromRoll()
    @Test
    fun `selectDiceFromRoll selects dice correctly with valid indices`() {
        val currentRoll: DiceRoll = listOf(1, 2, 3, 4, 5)
        val indicesToKeep = listOf(0, 2, 4)
        val expected: DiceRoll = listOf(1, 3, 5)
        assertEquals(expected, selectDiceFromRoll(currentRoll, indicesToKeep))

        val anotherRoll: DiceRoll = listOf(6, 6, 1, 5, 2)
        val anotherIndices = listOf(1, 3)
        val anotherExpected: DiceRoll = listOf(6, 5)
        assertEquals(anotherExpected, selectDiceFromRoll(anotherRoll, anotherIndices))
    }

    @Test
    fun `selectDiceFromRoll ignores out-of-bounds indices`() {
        val currentRoll: DiceRoll = listOf(1, 2, 3)
        val indicesToKeep = listOf(0, 2, 3, -1) // 3 and -1 are out of bounds
        val expected: DiceRoll = listOf(1, 3)
        assertEquals(expected, selectDiceFromRoll(currentRoll, indicesToKeep))
    }

    @Test
    fun `selectDiceFromRoll handles duplicate indices`() {
        val currentRoll: DiceRoll = listOf(1, 2, 3, 4, 5)
        // Order of selected dice should reflect the order of unique valid indices if distinct() and map() is used
        // The implementation uses filter().distinct().map() on indices, so output order depends on indicesToKeep order.
        val indicesToKeep = listOf(4, 0, 2, 0, 4, 2) 
        val expected: DiceRoll = listOf(5, 1, 3) 
        assertEquals(expected, selectDiceFromRoll(currentRoll, indicesToKeep))
        
        val indicesToKeep2 = listOf(0, 2, 0, 4, 2)
        val expected2: DiceRoll = listOf(1, 3, 5)
        assertEquals(expected2, selectDiceFromRoll(currentRoll, indicesToKeep2))
    }

    @Test
    fun `selectDiceFromRoll with empty roll returns empty list`() {
        val currentRoll: DiceRoll = emptyList()
        val indicesToKeep = listOf(0, 1)
        assertTrue(selectDiceFromRoll(currentRoll, indicesToKeep).isEmpty())
    }

    @Test
    fun `selectDiceFromRoll with empty indicesToKeep returns empty list`() {
        val currentRoll: DiceRoll = listOf(1, 2, 3, 4, 5)
        val indicesToKeep: List<Int> = emptyList()
        assertTrue(selectDiceFromRoll(currentRoll, indicesToKeep).isEmpty())
    }

    // Tests for getRemainingDice()
    @Test
    fun `getRemainingDice returns non-kept dice correctly`() {
        val currentRoll: DiceRoll = listOf(1, 2, 3, 4, 5)
        val indicesKept = listOf(0, 2, 4) // Keep 1, 3, 5
        val expected: DiceRoll = listOf(2, 4) // Remaining are 2, 4
        assertEquals(expected, getRemainingDice(currentRoll, indicesKept))
    }

    @Test
    fun `getRemainingDice when all dice are kept returns empty list`() {
        val currentRoll: DiceRoll = listOf(1, 2, 3)
        val indicesKept = listOf(0, 1, 2)
        assertTrue(getRemainingDice(currentRoll, indicesKept).isEmpty())
    }

    @Test
    fun `getRemainingDice when no dice are kept returns original roll`() {
        val currentRoll: DiceRoll = listOf(1, 2, 3, 4, 5)
        val indicesKept: List<Int> = emptyList()
        assertEquals(currentRoll, getRemainingDice(currentRoll, indicesKept))
        // Also check it's a copy, not the same instance, if the function guarantees it.
        // The current implementation returns currentRoll.toList() which is a shallow copy.
        assertNotSame(currentRoll, getRemainingDice(currentRoll, indicesKept))
    }

    @Test
    fun `getRemainingDice ignores invalid indices in indicesKept`() {
        val currentRoll: DiceRoll = listOf(1, 2, 3, 4, 5)
        val indicesKept = listOf(0, 2, 4, -1, 10) // Keep 1, 3, 5; ignore -1, 10
        val expected: DiceRoll = listOf(2, 4) // Remaining are 2, 4
        assertEquals(expected, getRemainingDice(currentRoll, indicesKept))
    }

    @Test
    fun `getRemainingDice with empty roll returns empty list`() {
        val currentRoll: DiceRoll = emptyList()
        val indicesKept = listOf(0, 1)
        assertTrue(getRemainingDice(currentRoll, indicesKept).isEmpty())
    }
}
