package fr.antoinehory.cinqmille.ui.game

import fr.antoinehory.cinqmille.game.DefaultDiceRoller
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class DefaultDiceRollerTest {

    private lateinit var diceRoller: DefaultDiceRoller

    @Before
    fun setUp() {
        diceRoller = DefaultDiceRoller()
    }

    @Test
    fun `roll with positive number of dice returns correct size and values`() {
        val numberOfDice = 5
        val rollResult = diceRoller.roll(numberOfDice)

        Assert.assertEquals(
            "Roll result should have $numberOfDice dice.",
            numberOfDice,
            rollResult.size
        )
        rollResult.forEach { dieValue ->
            Assert.assertTrue(
                "Die value should be between 1 and 6, was $dieValue.",
                dieValue in 1..6
            )
        }
    }

    @Test
    fun `roll with MAX_DICE returns correct size`() {
        // Assuming TurnManager.MAX_DICE is accessible or we use a known value like 5 or 6
        // For now, let's use a common value used in tests, e.g. 5
        val maxDice = 5 // Or TurnManager.MAX_DICE if directly accessible and stable for test
        val rollResult = diceRoller.roll(maxDice)
        Assert.assertEquals(
            "Roll result for MAX_DICE should have $maxDice dice.",
            maxDice,
            rollResult.size
        )
        rollResult.forEach { dieValue ->
            Assert.assertTrue(
                "Die value should be between 1 and 6, was $dieValue.",
                dieValue in 1..6
            )
        }
    }

    @Test
    fun `roll with zero dice returns empty list`() {
        val rollResult = diceRoller.roll(0)
        Assert.assertTrue(
            "Roll result for zero dice should be an empty list.",
            rollResult.isEmpty()
        )
    }

    @Test
    fun `roll with negative number of dice returns empty list`() {
        val rollResult = diceRoller.roll(-3)
        Assert.assertTrue(
            "Roll result for negative dice should be an empty list.",
            rollResult.isEmpty()
        )
    }

    @Test
    fun `roll with one die returns one value between 1 and 6`() {
        val rollResult = diceRoller.roll(1)
        Assert.assertEquals("Roll result for one die should have 1 element.", 1, rollResult.size)
        Assert.assertTrue("Die value should be between 1 and 6.", rollResult[0] in 1..6)
    }

    @Test
    fun `roll multiple times produces different results (statistically likely)`() {
        // This test is probabilistic, but with enough rolls, differences should appear.
        val roll1 = diceRoller.roll(5)
        val roll2 = diceRoller.roll(5)
        val roll3 = diceRoller.roll(5)

        // It's highly unlikely all three full rolls are identical if random.
        // A simpler check: it's unlikely roll1 == roll2 AND roll2 == roll3
        // unless Random is seeded or broken.
        // For a more robust test, one might check statistical distribution over many rolls,
        // but for a simple dice roller, just checking for non-identical results is usually sufficient.
        Assert.assertFalse(
            "Multiple rolls of 5 dice are expected to differ (statistically). Roll1: $roll1, Roll2: $roll2",
            roll1 == roll2 && roll2 == roll3 && roll1.size == 5
        )
    }
}