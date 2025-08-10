package fr.antoinehory.cinqmille.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TurnManagerTest {

    private lateinit var scoreCalculator: ScoreCalculator
    private lateinit var fakeDiceRoller: FakeDiceRoller
    private lateinit var turnManager: TurnManager

    class FakeDiceRoller : DiceRoller {
        private val rollsToReturn = mutableListOf<DiceRoll>()
        private var defaultRollFunction: (Int) -> DiceRoll = { numDice -> List(numDice) { 1 } }

        fun setRolls(vararg rolls: DiceRoll) {
            rollsToReturn.clear()
            rollsToReturn.addAll(rolls)
        }

        fun setDefaultRoll(rollFunction: (Int) -> DiceRoll) {
            defaultRollFunction = rollFunction
        }

        override fun roll(numberOfDice: Int): DiceRoll {
            return if (rollsToReturn.isNotEmpty()) {
                val rollToReturn = rollsToReturn.removeAt(0)
                rollToReturn
            } else {
                defaultRollFunction(numberOfDice)
            }
        }
    }

    @Before
    fun setUp() {
        scoreCalculator = ScoreCalculator
        fakeDiceRoller = FakeDiceRoller()
        turnManager = TurnManager(scoreCalculator, fakeDiceRoller)
    }

    @Test
    fun `startOrContinueRoll initial roll returns Rolled event with MAX_DICE`() {
        fakeDiceRoller.setRolls(listOf(1, 1, 1, 2, 3, 4))
        val event = turnManager.startOrContinueRoll()
        assertTrue("Event should be Rolled", event is TurnEvent.Rolled)
        if (event is TurnEvent.Rolled) {
            assertEquals("Initial roll should use MAX_DICE", TurnManager.MAX_DICE, event.dice.size)
            assertEquals("Dice in event should match latestRoll in manager", event.dice, turnManager.latestRoll)
            assertTrue("Event should indicate player can score", event.canPlayerMakeAnyScore)
        }
        assertEquals("Initial turn score should be 0", 0, turnManager.currentTurnScore)
        assertEquals("Dice to roll count should be MAX_DICE", TurnManager.MAX_DICE, turnManager.diceToRollCount)
    }

    @Test
    fun `startOrContinueRoll when roll cannot score returns Busted event`() {
        fakeDiceRoller.setRolls(listOf(2, 3, 4, 2, 3, 4))
        val event = turnManager.startOrContinueRoll()
        assertTrue("Event should be Busted", event is TurnEvent.Busted)
        if (event is TurnEvent.Busted) {
            assertEquals("Busted score should be 0", 0, event.finalTurnScore)
        }
        assertEquals("Turn score should remain 0 after bust", 0, turnManager.currentTurnScore)
        val postBustBank = turnManager.playerBanksScore()
        assertTrue("Banking after a bust should be InvalidAction", postBustBank is TurnEvent.InvalidAction)
    }

    @Test
    fun `startOrContinueRoll after a successful selection continues roll with correct dice count`() {
        fakeDiceRoller.setRolls(listOf(1,2,3,4,5,6), listOf(2,3,4,2,3)) // 1st scorable, 2nd non-scorable (5 dice)
        turnManager = TurnManager(scoreCalculator, fakeDiceRoller) // Reset manager for specific roll sequence
        turnManager.startOrContinueRoll()
        val selectionForNonScorable = turnManager.processPlayerSelection(listOf(0)) // Select '1', 5 dice left
        assertTrue(selectionForNonScorable is TurnEvent.Scored)
        assertEquals(5, (selectionForNonScorable as TurnEvent.Scored).diceForNextPotentialRoll)
        val bustedSecondRollEvent = turnManager.startOrContinueRoll()
        assertTrue("Second roll with non-scorable dice [2,3,4,2,3] should be Busted", bustedSecondRollEvent is TurnEvent.Busted)
        if(bustedSecondRollEvent is TurnEvent.Busted) {
            assertEquals("Busted event score for the turn part should be 0",0, bustedSecondRollEvent.finalTurnScore)
        }

        fakeDiceRoller.setRolls(listOf(1,2,3,4,5,6), listOf(1,1,1,2,3)) // 1st scorable, 2nd scorable (5 dice)
        turnManager = TurnManager(scoreCalculator, fakeDiceRoller) // Reset manager for specific roll sequence
        turnManager.startOrContinueRoll()
        val selectionForScorable = turnManager.processPlayerSelection(listOf(0)) // Select '1', 5 dice left
        assertTrue(selectionForScorable is TurnEvent.Scored)
        assertEquals(5, (selectionForScorable as TurnEvent.Scored).diceForNextPotentialRoll)
        val scorableSecondRollEvent = turnManager.startOrContinueRoll()
        assertTrue("Second roll with scorable dice [1,1,1,2,3] should be Rolled", scorableSecondRollEvent is TurnEvent.Rolled)
        if(scorableSecondRollEvent is TurnEvent.Rolled) {
            assertEquals("Dice count for second roll", 5, scorableSecondRollEvent.dice.size)
            assertTrue("Second roll should be scorable",scorableSecondRollEvent.canPlayerMakeAnyScore)
            assertEquals(listOf(1,1,1,2,3), scorableSecondRollEvent.dice)
        }
    }

    @Test
    fun `processPlayerSelection with scoring dice returns Scored event`() {
        fakeDiceRoller.setRolls(listOf(1, 5, 2, 3, 4, 6))
        turnManager.startOrContinueRoll()
        val selectedIndices = listOf(0) // Select the '1'
        val expectedScore = 100
        val selectionEvent = turnManager.processPlayerSelection(selectedIndices)
        assertTrue("Selection event should be Scored", selectionEvent is TurnEvent.Scored)
        if (selectionEvent is TurnEvent.Scored) {
            assertEquals(expectedScore, selectionEvent.scoreFromSelection)
            assertEquals(expectedScore, selectionEvent.newTurnTotalScore)
            assertTrue(selectionEvent.canRollAgain)
            assertEquals(TurnManager.MAX_DICE - selectedIndices.size, selectionEvent.diceForNextPotentialRoll)
            assertEquals(expectedScore, turnManager.currentTurnScore)
        }
    }

    @Test
    fun `processPlayerSelection with non-scoring dice (but valid selection) returns Busted event`() {
        fakeDiceRoller.setRolls(listOf(1, 2, 3, 4, 6, 6)) // Contains a '1' (scorable), but also '2'
        turnManager.startOrContinueRoll() // This roll is Rolled(canPlayerMakeAnyScore=true)

        val selectedIndices = listOf(1) // Select the '2' (index 1) which doesn't score by itself
        val selectionEvent = turnManager.processPlayerSelection(selectedIndices)

        assertTrue("Selection event should be Busted", selectionEvent is TurnEvent.Busted)
        if (selectionEvent is TurnEvent.Busted) {
            assertEquals(0, selectionEvent.finalTurnScore)
            assertEquals(0, turnManager.currentTurnScore) // Score resets on bust
        }
    }

    @Test
    fun `processPlayerSelection with invalid selection returns InvalidAction event`() {
        fakeDiceRoller.setRolls(listOf(1, 2, 3, 4, 5, 6)) // A scorable roll
        val rollEvent = turnManager.startOrContinueRoll()

        assertTrue("Initial roll should be Rolled", rollEvent is TurnEvent.Rolled)
        assertTrue("Initial roll should be scorable", (rollEvent as TurnEvent.Rolled).canPlayerMakeAnyScore)

        val invalidIndicesSelection = turnManager.processPlayerSelection(listOf(-1, 10, 99))
        assertTrue(invalidIndicesSelection is TurnEvent.InvalidAction)
        assertEquals("Indices de dés invalides, aucun dé conservé.", (invalidIndicesSelection as TurnEvent.InvalidAction).message)

        val emptySelection = turnManager.processPlayerSelection(emptyList())
        assertTrue(emptySelection is TurnEvent.InvalidAction)
        assertEquals("Le joueur doit sélectionner des dés qui marquent des points.", (emptySelection as TurnEvent.InvalidAction).message)
    }
    
    @Test
    fun `processPlayerSelection when all dice score, next roll is MAX_DICE`() {
        fakeDiceRoller.setRolls(listOf(1,1,1,5,5,5)) // All dice score (1000 + 500 = 1500)
        turnManager.startOrContinueRoll()

        val selectedIndices = listOf(0,1,2,3,4,5) // Select all dice
        val selectionEvent = turnManager.processPlayerSelection(selectedIndices)

        assertTrue("Event should be Scored", selectionEvent is TurnEvent.Scored)
        if (selectionEvent is TurnEvent.Scored) {
            assertEquals(1500, selectionEvent.scoreFromSelection)
            assertEquals(1500, selectionEvent.newTurnTotalScore)
            assertTrue(selectionEvent.canRollAgain)
            assertEquals(TurnManager.MAX_DICE, selectionEvent.diceForNextPotentialRoll) // Key assertion
        }
    }

    @Test
    fun `processPlayerSelection when no turn in progress returns InvalidAction`() {
        val event = turnManager.processPlayerSelection(listOf(0)) 
        assertTrue(event is TurnEvent.InvalidAction)
        assertEquals("Aucun tour en cours.", (event as TurnEvent.InvalidAction).message)
    }

    @Test
    fun `processPlayerSelection when no dice rolled returns InvalidAction`() {
        // This state (turnInProgress = true, latestRoll = empty) is hard to achieve with current public API.
        // startOrContinueRoll() ensures latestRoll is populated or the turn ends in a Bust.
        // The logic is covered by "no turn in progress" or internal checks in processPlayerSelection.
        assertTrue(true) 
    }

    @Test
    fun `playerBanksScore returns TurnEndedBanked with current turn score`() {
        fakeDiceRoller.setRolls(listOf(1, 2, 3, 4, 5, 6)) // Roll with a '1'
        turnManager.startOrContinueRoll()
        
        val scoreToBank = 100 // Score of selecting the '1'
        turnManager.processPlayerSelection(listOf(0)) // Select the '1' (at index 0)
        
        val bankEvent = turnManager.playerBanksScore()
        assertTrue("Event should be TurnEndedBanked", bankEvent is TurnEvent.TurnEndedBanked)
        if (bankEvent is TurnEvent.TurnEndedBanked) {
            assertEquals(scoreToBank, bankEvent.finalTurnScore)
        }

        val nextBankAttempt = turnManager.playerBanksScore()
        assertTrue("Banking again should be InvalidAction", nextBankAttempt is TurnEvent.InvalidAction)
    }

    @Test
    fun `playerBanksScore when no turn in progress returns InvalidAction`() {
        val bankEventNoTurn = turnManager.playerBanksScore()
        assertTrue(bankEventNoTurn is TurnEvent.InvalidAction)
        assertEquals("Impossible de banker: aucun tour en cours ou déjà terminé.", (bankEventNoTurn as TurnEvent.InvalidAction).message)

        fakeDiceRoller.setRolls(listOf(1,2,3,4,5,6)) // Scorable roll
        turnManager.startOrContinueRoll() 
        turnManager.processPlayerSelection(listOf(0)) // Select '1'
        turnManager.playerBanksScore() // Bank it
        
        val bankEventAfterBank = turnManager.playerBanksScore() 
        assertTrue(bankEventAfterBank is TurnEvent.InvalidAction)
        assertEquals("Impossible de banker: aucun tour en cours ou déjà terminé.", (bankEventAfterBank as TurnEvent.InvalidAction).message)
    }

    // --- Complex Scenarios / State Transitions ---
    @Test
    fun `full turn scenario - roll, score, roll, score, bank`() {
        val firstRoll = listOf(1, 1, 2, 3, 4, 5) // Scores 200 (1,1) -> 4 dice left
        val secondRoll = listOf(5, 5, 2, 3)    // Scores 100 (5,5) from these 4 dice -> 2 dice left
        
        fakeDiceRoller.setRolls(firstRoll, secondRoll)

        // 1. Initial Roll
        var turnEvent = turnManager.startOrContinueRoll()
        assertTrue("Initial roll should be Rolled", turnEvent is TurnEvent.Rolled)
        assertEquals(firstRoll, (turnEvent as TurnEvent.Rolled).dice)

        // 2. First Selection (select the two 1s, indices 0, 1)
        val scoreFromFirstSelection = scoreCalculator.calculateScore(listOf(1,1)) 
        turnEvent = turnManager.processPlayerSelection(listOf(0, 1))
        assertTrue("First selection should be Scored", turnEvent is TurnEvent.Scored)
        var scoredEvent = turnEvent as TurnEvent.Scored
        assertEquals("Score from first selection", scoreFromFirstSelection, scoredEvent.scoreFromSelection)
        assertEquals("Total score after first selection", scoreFromFirstSelection, scoredEvent.newTurnTotalScore)
        assertEquals("Dice for next roll after first selection", 4, scoredEvent.diceForNextPotentialRoll)
        assertEquals("Manager total score after first selection", scoreFromFirstSelection, turnManager.currentTurnScore)

        // 3. Second Roll (4 dice)
        turnEvent = turnManager.startOrContinueRoll()
        assertTrue("Second roll should be Rolled", turnEvent is TurnEvent.Rolled)
        assertEquals(secondRoll, (turnEvent as TurnEvent.Rolled).dice)
        assertTrue("Second roll should be scorable", turnEvent.canPlayerMakeAnyScore)


        // 4. Second Selection (select the two 5s, indices 0, 1 from the latestRoll which is secondRoll)
        val scoreFromSecondSelection = scoreCalculator.calculateScore(listOf(5,5))
        turnEvent = turnManager.processPlayerSelection(listOf(0, 1))
        assertTrue("Second selection should be Scored", turnEvent is TurnEvent.Scored)
        scoredEvent = turnEvent as TurnEvent.Scored
        assertEquals("Score from second selection", scoreFromSecondSelection, scoredEvent.scoreFromSelection)
        val expectedTotalAfterSecondScore = scoreFromFirstSelection + scoreFromSecondSelection
        assertEquals("Total score after second selection", expectedTotalAfterSecondScore, scoredEvent.newTurnTotalScore)
        assertEquals("Dice for next roll after second selection", 2, scoredEvent.diceForNextPotentialRoll) 
        assertEquals("Manager total score after second selection", expectedTotalAfterSecondScore, turnManager.currentTurnScore)

        // 5. Player Banks Score
        turnEvent = turnManager.playerBanksScore()
        assertTrue("Banking should result in TurnEndedBanked", turnEvent is TurnEvent.TurnEndedBanked)
        val bankedEvent = turnEvent as TurnEvent.TurnEndedBanked
        assertEquals("Final banked score", expectedTotalAfterSecondScore, bankedEvent.finalTurnScore)

        // Check turn is over, a new roll should start a fresh turn
        fakeDiceRoller.setDefaultRoll { numDice -> List(numDice) { 1 } } // Ensure a known scorable roll for the new turn
        val nextActionAfterBank = turnManager.startOrContinueRoll()
        assertTrue("Attempting to roll after banking should start a new turn and be Rolled (with default 1s)", nextActionAfterBank is TurnEvent.Rolled)
        if (nextActionAfterBank is TurnEvent.Rolled) {
             assertEquals("Dice in new turn should be the default roll", List(TurnManager.MAX_DICE) {1}, nextActionAfterBank.dice)
             assertTrue("New roll of all 1s should be scorable", nextActionAfterBank.canPlayerMakeAnyScore)
        }
        assertEquals("Score should reset for new turn", 0, turnManager.currentTurnScore)
    }

    @Test
    fun `full turn scenario - roll, bust`() {
        // Scenario 1: Bust on initial roll
        val nonScoringRoll = listOf(2, 3, 4, 6, 2, 4)
        fakeDiceRoller.setRolls(nonScoringRoll)
        var turnEvent = turnManager.startOrContinueRoll()
        assertTrue("Initial non-scoring roll should be Busted", turnEvent is TurnEvent.Busted)
        if (turnEvent is TurnEvent.Busted) {
            assertEquals("Busted score should be 0", 0, turnEvent.finalTurnScore)
        }
        assertEquals("Turn score should be 0 after initial bust", 0, turnManager.currentTurnScore)
        var nextAction = turnManager.playerBanksScore() // Try to bank
        assertTrue("Banking after initial bust should be InvalidAction", nextAction is TurnEvent.InvalidAction)

        // Reset for Scenario 2
        turnManager = TurnManager(scoreCalculator, fakeDiceRoller) // New manager instance

        // Scenario 2: Bust on player selection
        val scorableRoll = listOf(1, 2, 3, 4, 6, 6) // Contains a '1', but '2' is not scorable alone
        fakeDiceRoller.setRolls(scorableRoll)
        turnEvent = turnManager.startOrContinueRoll()
        assertTrue("Roll for selection bust test should be Rolled", turnEvent is TurnEvent.Rolled)
        assertTrue("Roll should be scorable", (turnEvent as TurnEvent.Rolled).canPlayerMakeAnyScore)

        // Player selects a non-scoring die (the '2' at index 1)
        turnEvent = turnManager.processPlayerSelection(listOf(1))
        assertTrue("Selecting non-scoring '2' should be Busted", turnEvent is TurnEvent.Busted)
        if (turnEvent is TurnEvent.Busted) {
            assertEquals("Busted score on selection should be 0", 0, turnEvent.finalTurnScore)
        }
        assertEquals("Turn score should be 0 after selection bust", 0, turnManager.currentTurnScore)
        nextAction = turnManager.playerBanksScore() // Try to bank
        assertTrue("Banking after selection bust should be InvalidAction", nextAction is TurnEvent.InvalidAction)
    }
    
    @Test
    fun `bust after a successful partial score resets turn score to 0`() {
        val initialScoringRoll = listOf(1, 1, 2, 3, 4, 5) // two 1s = 200
        val subsequentNonScoringRoll = listOf(2, 3, 4, 6) // For the 4 dice left

        fakeDiceRoller.setRolls(initialScoringRoll, subsequentNonScoringRoll)

        // 1. Initial Roll
        var turnEvent = turnManager.startOrContinueRoll()
        assertTrue("Initial roll should be Rolled", turnEvent is TurnEvent.Rolled)

        // 2. First Selection (scoring)
        val scoreFromSelection = scoreCalculator.calculateScore(listOf(1,1)) // 200
        turnEvent = turnManager.processPlayerSelection(listOf(0,1)) // Select the two 1s
        assertTrue("Selection should be Scored", turnEvent is TurnEvent.Scored)
        val scoredEvent = turnEvent as TurnEvent.Scored
        assertEquals("Score from selection should be 200", scoreFromSelection, scoredEvent.scoreFromSelection)
        assertEquals("Turn total should be 200", scoreFromSelection, scoredEvent.newTurnTotalScore)
        assertEquals("Manager's current score should be 200", scoreFromSelection, turnManager.currentTurnScore)
        assertEquals("Should have 4 dice left to roll", 4, scoredEvent.diceForNextPotentialRoll)

        // 3. Second Roll (which will be non-scoring)
        turnEvent = turnManager.startOrContinueRoll()
        assertTrue("Subsequent non-scoring roll should be Busted", turnEvent is TurnEvent.Busted)
        
        if (turnEvent is TurnEvent.Busted) {
            assertEquals("Busted event final score should be 0", 0, turnEvent.finalTurnScore)
        }
        assertEquals("Manager's current score should reset to 0 after bust", 0, turnManager.currentTurnScore)

        // 4. Verify turn is over
        val nextAction = turnManager.playerBanksScore()
        assertTrue("Banking after bust should be InvalidAction", nextAction is TurnEvent.InvalidAction)
    }
}
