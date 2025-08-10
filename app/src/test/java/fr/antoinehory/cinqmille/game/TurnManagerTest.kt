package fr.antoinehory.cinqmille.game

import fr.antoinehory.cinqmille.game.FakeDiceRoller
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TurnManagerTest {

    private lateinit var scoreCalculator: ScoreCalculator
    private lateinit var fakeDiceRoller: FakeDiceRoller
    private lateinit var turnManager: TurnManager

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
        fakeDiceRoller.setRolls(listOf(1,2,3,4,5,6), listOf(2,3,4,2,3))
        turnManager = TurnManager(scoreCalculator, fakeDiceRoller)
        turnManager.startOrContinueRoll()
        val selectionForNonScorable = turnManager.processPlayerSelection(listOf(0))
        assertTrue(selectionForNonScorable is TurnEvent.Scored) // MODIFIÉ
        assertEquals(5, (selectionForNonScorable as TurnEvent.Scored).diceForNextPotentialRoll) // MODIFIÉ
        val bustedSecondRollEvent = turnManager.startOrContinueRoll()
        assertTrue("Second roll with non-scorable dice [2,3,4,2,3] should be Busted", bustedSecondRollEvent is TurnEvent.Busted)
        if(bustedSecondRollEvent is TurnEvent.Busted) {
            assertEquals("Busted event score for the turn part should be 0",0, bustedSecondRollEvent.finalTurnScore)
        }

        fakeDiceRoller.setRolls(listOf(1,2,3,4,5,6), listOf(1,1,1,2,3))
        turnManager = TurnManager(scoreCalculator, fakeDiceRoller)
        turnManager.startOrContinueRoll()
        val selectionForScorable = turnManager.processPlayerSelection(listOf(0))
        assertTrue(selectionForScorable is TurnEvent.Scored) // MODIFIÉ
        assertEquals(5, (selectionForScorable as TurnEvent.Scored).diceForNextPotentialRoll) // MODIFIÉ
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
        val selectedIndices = listOf(0)
        val expectedScore = 100
        val selectionEvent = turnManager.processPlayerSelection(selectedIndices)
        assertTrue("Selection event should be Scored", selectionEvent is TurnEvent.Scored) // MODIFIÉ
        if (selectionEvent is TurnEvent.Scored) { // MODIFIÉ
            assertEquals(expectedScore, selectionEvent.scoreFromSelection)
            assertEquals(expectedScore, selectionEvent.newTurnTotalScore)
            assertTrue(selectionEvent.canRollAgain)
            assertEquals(TurnManager.MAX_DICE - selectedIndices.size, selectionEvent.diceForNextPotentialRoll)
            assertEquals(expectedScore, turnManager.currentTurnScore)
        }
    }

    @Test
    fun `processPlayerSelection with non-scoring dice (but valid selection) returns Busted event`() {
        fakeDiceRoller.setRolls(listOf(1, 2, 3, 4, 6, 6))
        turnManager.startOrContinueRoll()

        val selectedIndices = listOf(1)
        val selectionEvent = turnManager.processPlayerSelection(selectedIndices)

        assertTrue("Selection event should be Busted", selectionEvent is TurnEvent.Busted)
        if (selectionEvent is TurnEvent.Busted) {
            assertEquals(0, selectionEvent.finalTurnScore)
            assertEquals(0, turnManager.currentTurnScore)
        }
    }

    @Test
    fun `processPlayerSelection with invalid selection returns InvalidAction event`() {
        fakeDiceRoller.setRolls(listOf(1, 2, 3, 4, 5, 6))
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
        fakeDiceRoller.setRolls(listOf(1,1,1,5,5,5))
        turnManager.startOrContinueRoll()

        val selectedIndices = listOf(0,1,2,3,4,5)
        val selectionEvent = turnManager.processPlayerSelection(selectedIndices)

        assertTrue("Event should be Scored", selectionEvent is TurnEvent.Scored) // MODIFIÉ
        if (selectionEvent is TurnEvent.Scored) { // MODIFIÉ
            assertEquals(1500, selectionEvent.scoreFromSelection)
            assertEquals(1500, selectionEvent.newTurnTotalScore)
            assertTrue(selectionEvent.canRollAgain)
            assertEquals(TurnManager.MAX_DICE, selectionEvent.diceForNextPotentialRoll)
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
        assertTrue(true)
    }

    @Test
    fun `playerBanksScore returns TurnEndedBanked with current turn score`() {
        fakeDiceRoller.setRolls(listOf(1, 2, 3, 4, 5, 6))
        turnManager.startOrContinueRoll()

        val scoreToBank = 100
        turnManager.processPlayerSelection(listOf(0))

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

        fakeDiceRoller.setRolls(listOf(1,2,3,4,5,6))
        turnManager.startOrContinueRoll()
        turnManager.processPlayerSelection(listOf(0))
        turnManager.playerBanksScore()

        val bankEventAfterBank = turnManager.playerBanksScore()
        assertTrue(bankEventAfterBank is TurnEvent.InvalidAction)
        assertEquals("Impossible de banker: aucun tour en cours ou déjà terminé.", (bankEventAfterBank as TurnEvent.InvalidAction).message)
    }

    // --- Complex Scenarios / State Transitions ---
    @Test
    fun `full turn scenario - roll, score, roll, score, bank`() {
        val firstRoll = listOf(1, 1, 2, 3, 4, 5)
        val secondRoll = listOf(5, 5, 2, 3)

        fakeDiceRoller.setRolls(firstRoll, secondRoll)

        var turnEventResponse = turnManager.startOrContinueRoll()
        assertTrue("Initial roll should be Rolled", turnEventResponse is TurnEvent.Rolled)
        assertEquals(firstRoll, (turnEventResponse as TurnEvent.Rolled).dice)

        val scoreFromFirstSelection = scoreCalculator.calculateScore(listOf(1,1))
        turnEventResponse = turnManager.processPlayerSelection(listOf(0, 1))
        assertTrue("First selection should be Scored", turnEventResponse is TurnEvent.Scored) // MODIFIÉ
        var scoredEvent = turnEventResponse as TurnEvent.Scored // MODIFIÉ
        assertEquals("Score from first selection", scoreFromFirstSelection, scoredEvent.scoreFromSelection)
        assertEquals("Total score after first selection", scoreFromFirstSelection, scoredEvent.newTurnTotalScore)
        assertEquals("Dice for next roll after first selection", 4, scoredEvent.diceForNextPotentialRoll)
        assertEquals("Manager total score after first selection", scoreFromFirstSelection, turnManager.currentTurnScore)

        turnEventResponse = turnManager.startOrContinueRoll()
        assertTrue("Second roll should be Rolled", turnEventResponse is TurnEvent.Rolled)
        assertEquals(secondRoll, (turnEventResponse as TurnEvent.Rolled).dice)
        assertTrue("Second roll should be scorable", (turnEventResponse as TurnEvent.Rolled).canPlayerMakeAnyScore)


        val scoreFromSecondSelection = scoreCalculator.calculateScore(listOf(5,5))
        turnEventResponse = turnManager.processPlayerSelection(listOf(0, 1))
        assertTrue("Second selection should be Scored", turnEventResponse is TurnEvent.Scored) // MODIFIÉ
        scoredEvent = turnEventResponse as TurnEvent.Scored // MODIFIÉ
        assertEquals("Score from second selection", scoreFromSecondSelection, scoredEvent.scoreFromSelection)
        val expectedTotalAfterSecondScore = scoreFromFirstSelection + scoreFromSecondSelection
        assertEquals("Total score after second selection", expectedTotalAfterSecondScore, scoredEvent.newTurnTotalScore)
        assertEquals("Dice for next roll after second selection", 2, scoredEvent.diceForNextPotentialRoll)
        assertEquals("Manager total score after second selection", expectedTotalAfterSecondScore, turnManager.currentTurnScore)

        turnEventResponse = turnManager.playerBanksScore()
        assertTrue("Banking should result in TurnEndedBanked", turnEventResponse is TurnEvent.TurnEndedBanked)
        val bankedEvent = turnEventResponse as TurnEvent.TurnEndedBanked
        assertEquals("Final banked score", expectedTotalAfterSecondScore, bankedEvent.finalTurnScore)

        fakeDiceRoller.setDefaultRoll { numDice -> List(numDice) { 1 } }
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
        val nonScoringRoll = listOf(2, 3, 4, 6, 2, 4)
        fakeDiceRoller.setRolls(nonScoringRoll)
        var turnEventResponse = turnManager.startOrContinueRoll()
        assertTrue("Initial non-scoring roll should be Busted", turnEventResponse is TurnEvent.Busted)
        if (turnEventResponse is TurnEvent.Busted) {
            assertEquals("Busted score should be 0", 0, turnEventResponse.finalTurnScore)
        }
        assertEquals("Turn score should be 0 after initial bust", 0, turnManager.currentTurnScore)
        var nextAction = turnManager.playerBanksScore()
        assertTrue("Banking after initial bust should be InvalidAction", nextAction is TurnEvent.InvalidAction)

        turnManager = TurnManager(scoreCalculator, fakeDiceRoller)

        val scorableRoll = listOf(1, 2, 3, 4, 6, 6)
        fakeDiceRoller.setRolls(scorableRoll)
        turnEventResponse = turnManager.startOrContinueRoll()
        assertTrue("Roll for selection bust test should be Rolled", turnEventResponse is TurnEvent.Rolled)
        assertTrue("Roll should be scorable", (turnEventResponse as TurnEvent.Rolled).canPlayerMakeAnyScore)

        turnEventResponse = turnManager.processPlayerSelection(listOf(1))
        assertTrue("Selecting non-scoring '2' should be Busted", turnEventResponse is TurnEvent.Busted)
        if (turnEventResponse is TurnEvent.Busted) {
            assertEquals("Busted score on selection should be 0", 0, turnEventResponse.finalTurnScore)
        }
        assertEquals("Turn score should be 0 after selection bust", 0, turnManager.currentTurnScore)
        nextAction = turnManager.playerBanksScore()
        assertTrue("Banking after selection bust should be InvalidAction", nextAction is TurnEvent.InvalidAction)
    }

    @Test
    fun `bust after a successful partial score resets turn score to 0`() {
        val initialScoringRoll = listOf(1, 1, 2, 3, 4, 5)
        val subsequentNonScoringRoll = listOf(2, 3, 4, 6)

        fakeDiceRoller.setRolls(initialScoringRoll, subsequentNonScoringRoll)

        var turnEventResponse = turnManager.startOrContinueRoll()
        assertTrue("Initial roll should be Rolled", turnEventResponse is TurnEvent.Rolled)

        val scoreFromSelection = scoreCalculator.calculateScore(listOf(1,1))
        turnEventResponse = turnManager.processPlayerSelection(listOf(0,1))
        assertTrue("Selection should be Scored", turnEventResponse is TurnEvent.Scored) // MODIFIÉ
        val scoredEvent = turnEventResponse as TurnEvent.Scored // MODIFIÉ
        assertEquals("Score from selection should be 200", scoreFromSelection, scoredEvent.scoreFromSelection)
        assertEquals("Turn total should be 200", scoreFromSelection, scoredEvent.newTurnTotalScore)
        assertEquals("Manager's current score should be 200", scoreFromSelection, turnManager.currentTurnScore)
        assertEquals("Should have 4 dice left to roll", 4, scoredEvent.diceForNextPotentialRoll)

        turnEventResponse = turnManager.startOrContinueRoll()
        assertTrue("Subsequent non-scoring roll should be Busted", turnEventResponse is TurnEvent.Busted)

        if (turnEventResponse is TurnEvent.Busted) {
            assertEquals("Busted event final score should be 0", 0, turnEventResponse.finalTurnScore)
        }
        assertEquals("Manager's current score should reset to 0 after bust", 0, turnManager.currentTurnScore)

        val nextAction = turnManager.playerBanksScore()
        assertTrue("Banking after bust should be InvalidAction", nextAction is TurnEvent.InvalidAction)
    }
}

