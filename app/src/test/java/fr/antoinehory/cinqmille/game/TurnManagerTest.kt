package fr.antoinehory.cinqmille.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TurnManagerTest {

    private lateinit var fakeDiceRoller: FakeDiceRoller
    private lateinit var turnManager: TurnManager
    private val gameRules = GameRules() // Default rules
    private val initialDiceCount = 5 // Consistent dice count for tests

    // Inner FakeDiceRoller as defined in the original test file
    class FakeDiceRoller : DiceRoller {
        private var rollsToReturn: MutableList<List<Int>> = mutableListOf()
        private var defaultRollFunc: ((Int) -> List<Int>)? = null

        override fun roll(numberOfDice: Int): DiceRoll {
            if (rollsToReturn.isNotEmpty()) {
                val roll = rollsToReturn.removeAt(0)
                return if (roll.size == numberOfDice) roll
                else if (roll.size > numberOfDice) roll.take(numberOfDice)
                else roll + List(numberOfDice - roll.size) { (1..6).random() } // Pad if too short
            }
            return defaultRollFunc?.invoke(numberOfDice) ?: List(numberOfDice) { (1..6).random() }
        }

        fun setRolls(vararg rolls: List<Int>) {
            rollsToReturn.clear()
            rollsToReturn.addAll(rolls.map { it.toList() })
        }
        fun setDefaultRoll(rollFunc: (Int) -> List<Int>) {
            this.defaultRollFunc = rollFunc
        }
    }


    @Before
    fun setUp() {
        fakeDiceRoller = FakeDiceRoller()
        // Default TurnManager uses canPlayerOpen = false
        turnManager = TurnManager(fakeDiceRoller, gameRules, false, initialDiceCount)
    }

    @Test
    fun `rollDice initial roll returns Rolled event with correct dice count`() {
        fakeDiceRoller.setRolls(listOf(1, 1, 1, 2, 3))
        val event = turnManager.rollDice()

        assertTrue("Event should be Rolled: $event", event is TurnEvent.Rolled)
        val rolledEvent = event as TurnEvent.Rolled
        assertEquals("Initial roll should use initialDiceCount", initialDiceCount, rolledEvent.dice.size)
        assertEquals("Dice in event should match roller's output", listOf(1,1,1,2,3), rolledEvent.dice)
        assertTrue("Event should indicate player can score", rolledEvent.canPlayerMakeAnyScore)
    }

    @Test
    fun `rollDice when roll cannot score returns Busted event`() {
        fakeDiceRoller.setRolls(listOf(2, 3, 4, 2, 3))
        val event = turnManager.rollDice()

        assertTrue("Event should be Busted: $event", event is TurnEvent.Busted)
        val bustedEvent = event as TurnEvent.Busted
        assertEquals("Busted score from event should be 0", 0, bustedEvent.finalTurnScore)
        assertEquals("Dice at bust should match roller's output", listOf(2,3,4,2,3), bustedEvent.diceAtBust)
    }

    @Test
    fun `selectDice after a successful roll continues roll with correct dice count if applicable`() {
        // Scenario 1: Selection leads to a bust on the auto-roll
        fakeDiceRoller.setRolls(
            listOf(1, 2, 3, 4, 5), // Initial roll
            listOf(2, 3, 4, 2)     // Auto-roll for 4 dice, non-scoring
        )
        turnManager = TurnManager(fakeDiceRoller, gameRules, false, initialDiceCount)
        turnManager.rollDice() // Initial roll: [1,2,3,4,5]

        val selectionEventBust = turnManager.selectDice(listOf(0)) // Player selects the '1' (index 0)
        assertTrue("Selection event should be Busted (due to auto-roll): $selectionEventBust", selectionEventBust is TurnEvent.Busted)
        val bustedEvent = selectionEventBust as TurnEvent.Busted
        assertEquals("Dice at bust should be the state after auto-roll", listOf(1,2,3,4,2).sorted(), bustedEvent.diceAtBust.sorted())


        // Scenario 2: Selection leads to a scorable auto-roll
        fakeDiceRoller.setRolls(
            listOf(1, 2, 3, 4, 5), // Initial roll
            listOf(1, 1, 5, 6)     // Next roll for remaining 4 dice (scorable for [1,1,1,5,6] state)
        )
        turnManager = TurnManager(fakeDiceRoller, gameRules, false, initialDiceCount) // canPlayerOpen = false
        turnManager.rollDice() // Initial roll: [1,2,3,4,5]
        val selectionEventScored = turnManager.selectDice(listOf(0)) // Select '1' (score 100)

        assertTrue("Selection event should be Scored: $selectionEventScored", selectionEventScored is TurnEvent.Scored)
        val scoredEvent = selectionEventScored as TurnEvent.Scored
        assertEquals("Score after selecting '1' (100)", 100 , scoredEvent.newTurnTotalScore) // Event score is from selection
        assertTrue("Should be able to roll again", scoredEvent.canRollAgain)
        assertEquals("Dice state should reflect selection and new roll", listOf(1,1,1,5,6).sorted(), scoredEvent.diceStateAfterAction.sorted())
        assertEquals("Dice state size should be initialDiceCount", initialDiceCount, scoredEvent.diceStateAfterAction.size)
        assertTrue("Mask should reflect '1' as scored", scoredEvent.scoredDiceMask[0])
    }


    @Test
    fun `selectDice with scoring dice returns Busted event if auto-roll busts`() {
        fakeDiceRoller.setRolls(
            listOf(1, 5, 2, 3, 4),
            listOf(2,2,3,3)
        )
        turnManager.rollDice()

        val selectedIndices = listOf(0)
        val selectionEvent = turnManager.selectDice(selectedIndices)

        assertTrue("Selection event should be Busted: $selectionEvent", selectionEvent is TurnEvent.Busted)
        val bustedEvent = selectionEvent as TurnEvent.Busted
        assertEquals("Busted finalTurnScore should be 0", 0, bustedEvent.finalTurnScore)
    }

    @Test
    fun `selectDice with non-scoring dice (but valid selection by index) returns Busted event`() {
        fakeDiceRoller.setRolls(listOf(1, 2, 3, 4, 6)) // Roll [1,2,3,4,6]
        turnManager.rollDice()
        // Select '2' (index 1). Score for [2] is 0. This is a bust.
        val selectedIndices = listOf(1)
        val selectionEvent = turnManager.selectDice(selectedIndices)

        assertTrue("Selection event should be Busted: $selectionEvent", selectionEvent is TurnEvent.Busted)
        val bustedEvent = selectionEvent as TurnEvent.Busted
        assertEquals("Busted finalTurnScore should be 0", 0, bustedEvent.finalTurnScore)
    }


    @Test
    fun `selectDice with invalid selection returns InvalidAction event`() {
        fakeDiceRoller.setRolls(listOf(1, 2, 3, 4, 5))
        turnManager.rollDice()

        val invalidIndicesSelection = turnManager.selectDice(listOf(-1, 10, 99))
        assertTrue("Selection with invalid indices: $invalidIndicesSelection", invalidIndicesSelection is TurnEvent.InvalidAction)

        val emptySelection = turnManager.selectDice(emptyList())
        assertTrue("Empty selection: $emptySelection", emptySelection is TurnEvent.InvalidAction)
    }


    @Test
    fun `selectDice when all dice score, next roll is initialDiceCount`() {
        fakeDiceRoller.setRolls(
            listOf(1,1,1,5,5), // Initial roll (scores 1000 + 100 = 1100, all dice score)
            listOf(6,6,6,6,6)  // Next roll after "plein"
        )
        turnManager.rollDice() // Roll [1,1,1,5,5]
        val selectedIndices = listOf(0,1,2,3,4) // Select all
        val selectionEvent = turnManager.selectDice(selectedIndices) // Event score should be 1100 (from plein)

        assertTrue("Event should be Scored: $selectionEvent", selectionEvent is TurnEvent.Scored)
        val scoredEvent = selectionEvent as TurnEvent.Scored
        assertEquals("Score from selecting all (1,1,1,5,5)", 1100, scoredEvent.newTurnTotalScore)
        assertTrue("Should be able to roll again", scoredEvent.canRollAgain)
        assertEquals("Dice state after action should be the new roll", listOf(6,6,6,6,6), scoredEvent.diceStateAfterAction)
        assertFalse("Scored mask should be all false", scoredEvent.scoredDiceMask.any { it })
    }

    @Test
    fun `selectDice when no turn in progress (no roll first) returns InvalidAction`() {
        val event = turnManager.selectDice(listOf(0))
        assertTrue("Event should be InvalidAction: $event", event is TurnEvent.InvalidAction)
    }

    @Test
    fun `bankScore returns TurnEndedBanked with current turn score`() {
        val currentCanPlayerOpen = false
        turnManager = TurnManager(fakeDiceRoller, gameRules, currentCanPlayerOpen, initialDiceCount)

        fakeDiceRoller.setRolls(
             listOf(1,1,1,2,3) // Roll (1000 pts for 1,1,1)
        )
        turnManager.rollDice() // Roll [1,1,1,2,3]

        // Player selects the three 1s. Auto-roll for [2,3]
        fakeDiceRoller.setRolls(listOf(5,5)) // Auto-roll for 2 dice scores 100.
        val selectEvent = turnManager.selectDice(listOf(0,1,2)) // Select [1,1,1]
        assertTrue(selectEvent is TurnEvent.Scored)
        val scoredSelectEvent = selectEvent as TurnEvent.Scored
        assertEquals("Score from selection [1,1,1] should be 1000", 1000, scoredSelectEvent.newTurnTotalScore) // Score from this selection

        // TurnManager internal currentTurnScore is now 1000 (selection) + 100 (auto-roll) = 1100

        val bankEvent = turnManager.bankScore()
        assertTrue("Event should be TurnEndedBanked: $bankEvent", bankEvent is TurnEvent.TurnEndedBanked)
        assertEquals("Banked score should be total accumulated (1000+100)",1100, (bankEvent as TurnEvent.TurnEndedBanked).finalTurnScore)

        // Try to bank again after turn ended
        val nextBankAttempt = turnManager.bankScore()
        // With simplified bankScore, this always returns TurnEndedBanked(0) as state is reset.
        assertTrue("Banking again should be TurnEndedBanked: $nextBankAttempt", nextBankAttempt is TurnEvent.TurnEndedBanked)
        assertEquals("Banking again after turn ended should yield 0 score", 0, (nextBankAttempt as TurnEvent.TurnEndedBanked).finalTurnScore)
    }

    @Test
    fun `bankScore when no turn in progress returns appropriate event`() {
        // Scenario 1: Player has not opened (default setUp, canPlayerOpen = false)
        val canPlayerOpenFirstScenario = false
        turnManager = TurnManager(fakeDiceRoller, gameRules, canPlayerOpenFirstScenario, initialDiceCount)
        val bankEventNoTurn = turnManager.bankScore()

        // With simplified bankScore, this always returns TurnEndedBanked(0)
        assertTrue("Banking with no score and not open should be TurnEndedBanked: $bankEventNoTurn", bankEventNoTurn is TurnEvent.TurnEndedBanked)
        assertEquals("Banking with no score and not open should yield 0", 0, (bankEventNoTurn as TurnEvent.TurnEndedBanked).finalTurnScore)


        // Scenario 2: Player opened, then tries to bank 0 after a turn ended
        val canPlayerOpenSecondScenario = true
        turnManager = TurnManager(fakeDiceRoller, gameRules, canPlayerOpenSecondScenario, initialDiceCount)
        // Simulate a turn that ended (e.g., via bank/bust which calls resetTurnStateAfterBustOrBank)
        // turnManager.bankScore() // This would make it reset. Or just rely on resetForNewTurn or fresh manager.
        // Let's ensure state is as if after a bank
        turnManager.bankScore() // Ends a hypothetical empty turn, currentScore is 0, hasRolledInSegment is false

        val bankEventAfterBank = turnManager.bankScore()
        assertTrue("Banking 0 when open should be TurnEndedBanked(0): $bankEventAfterBank", bankEventAfterBank is TurnEvent.TurnEndedBanked)
        assertEquals(0, (bankEventAfterBank as TurnEvent.TurnEndedBanked).finalTurnScore)
    }

    @Test
    fun `full turn scenario - roll, score, roll, score, bank`() {
        val currentCanPlayerOpen = false
        val firstRollDice = listOf(1, 1, 2, 3, 4)         // Roll [1,1,2,3,4]
        val autoRollAfterFirstSelection = listOf(5, 5, 6) // For 3 dice [2,3,4] -> scores 100 for [5,5]
        val autoRollAfterSecondSelection = listOf(2)      // For 1 die [6] -> non-scorable (bust)

        fakeDiceRoller.setRolls(firstRollDice, autoRollAfterFirstSelection, autoRollAfterSecondSelection)
        turnManager = TurnManager(fakeDiceRoller, gameRules, currentCanPlayerOpen, initialDiceCount)

        var turnEventResponse = turnManager.rollDice() // Roll [1,1,2,3,4]
        assertTrue(turnEventResponse is TurnEvent.Rolled)

        // Select two 1s (score 200). Auto-rolls 3 dice [2,3,4] into [5,5,6] (scores 100 for 5,5).
        turnEventResponse = turnManager.selectDice(listOf(0, 1))
        assertTrue(turnEventResponse is TurnEvent.Scored)
        var scoredEvent = turnEventResponse as TurnEvent.Scored
        assertEquals("Score from selecting [1,1] should be 200", 200, scoredEvent.newTurnTotalScore)
        assertTrue(scoredEvent.canRollAgain)
        // TurnManager internal currentTurnScore is now 200 (selection) + 100 (auto-roll) = 300
        // Dice state is now [1,1,5,5,6] with [1,1] masked.

        // Select two 5s (score 100). Indices [2,3] from [1,1,5,5,6]. Auto-rolls 1 die [6] into [2] (bust).
        turnEventResponse = turnManager.selectDice(listOf(2, 3))
        assertTrue("Second selection should be Busted: $turnEventResponse", turnEventResponse is TurnEvent.Busted)
        val bustedEvent = turnEventResponse as TurnEvent.Busted
        assertEquals("Busted finalTurnScore should be 0", 0, bustedEvent.finalTurnScore)
        // TurnManager internal currentTurnScore is now 0.

        turnEventResponse = turnManager.bankScore()
        assertTrue(turnEventResponse is TurnEvent.TurnEndedBanked)
        assertEquals("Final banked score should be 0 after bust", 0, (turnEventResponse as TurnEvent.TurnEndedBanked).finalTurnScore)
    }

    @Test
    fun `full turn scenario - roll, bust`() {
        val currentCanPlayerOpen = false
        turnManager = TurnManager(fakeDiceRoller, gameRules, currentCanPlayerOpen, initialDiceCount)
        fakeDiceRoller.setRolls(listOf(2, 3, 4, 6, 2))
        var turnEventResponse = turnManager.rollDice() // Roll [2,3,4,6,2] -> BUST
        assertTrue(turnEventResponse is TurnEvent.Busted)

        turnEventResponse = turnManager.bankScore() // Attempt to bank after bust
        // With simplified bankScore, this always returns TurnEndedBanked(0)
        assertTrue("Banking after bust should be TurnEndedBanked: $turnEventResponse", turnEventResponse is TurnEvent.TurnEndedBanked)
        assertEquals("Banking after bust should yield 0", 0, (turnEventResponse as TurnEvent.TurnEndedBanked).finalTurnScore)
    }

     @Test
    fun `bust after a successful partial score, TurnManager signals Busted`() {
        val currentCanPlayerOpen = false
        turnManager = TurnManager(fakeDiceRoller, gameRules, currentCanPlayerOpen, initialDiceCount)
        fakeDiceRoller.setRolls(
            listOf(1, 1, 2, 3, 4),     // Initial roll (select 1,1 -> 200pts)
            listOf(2, 3, 6)            // Auto-roll for 3 dice (non-scoring -> bust)
        )
        turnManager.rollDice() // Roll [1,1,2,3,4]

        // Select [1,1] (score 200). Auto-roll 3 dice [2,3,4] into [2,3,6] (busts).
        val selectionEvent = turnManager.selectDice(listOf(0,1))
        assertTrue("Selection leading to bust on auto-roll: $selectionEvent", selectionEvent is TurnEvent.Busted)
        val bustedEvent = selectionEvent as TurnEvent.Busted
        assertEquals("Busted event final score for the bust action should be 0", 0, bustedEvent.finalTurnScore)
        // TurnManager internal currentTurnScore is now 0.

        val bankAttemptAfterBust = turnManager.bankScore()
        // With simplified bankScore, this always returns TurnEndedBanked(0)
        assertTrue("Banking after bust should be TurnEndedBanked: $bankAttemptAfterBust", bankAttemptAfterBust is TurnEvent.TurnEndedBanked)
        assertEquals("Banking after bust should yield 0", 0, (bankAttemptAfterBust as TurnEvent.TurnEndedBanked).finalTurnScore)
    }
}
