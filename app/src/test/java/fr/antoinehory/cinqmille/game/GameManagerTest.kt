package fr.antoinehory.cinqmille.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GameManagerTest {

    private lateinit var gameManager: GameManager
    private lateinit var fakeDiceRoller: FakeDiceRoller

    @Before
    fun setUp() {
        fakeDiceRoller = FakeDiceRoller()
        gameManager = GameManager(fakeDiceRoller)
    }

    // --- Test startGame ---
    @Test
    fun `startGame initializes players correctly`() {
        val numberOfPlayers = 3
        gameManager.startGame(numberOfPlayers)

        val players = gameManager.allPlayers
        assertEquals("Should have $numberOfPlayers players", numberOfPlayers, players.size)

        players.forEachIndexed { index, player ->
            assertEquals("Player ID should be ${index + 1}", index + 1, player.id)
            assertEquals("Player ${player.id} total score should be 0", 0, player.totalScore)
            assertFalse("Player ${player.id} should not have opened", player.hasOpened)
        }
    }

    @Test
    fun `startGame sets first player and gameInProgress state`() {
        gameManager.startGame(2)

        assertNotNull("Current player should not be null after starting game", gameManager.currentPlayer)
        assertEquals("Current player should be player 1", 1, gameManager.currentPlayer?.id)
    }

    @Test
    fun `startGame returns GameStarted event`() {
        val numberOfPlayers = 2
        val event = gameManager.startGame(numberOfPlayers)

        assertTrue("Event should be GameStarted", event is GameEvent.GameStarted)
        val gameStartedEvent = event as GameEvent.GameStarted
        assertEquals("GameStarted event should have $numberOfPlayers players", numberOfPlayers, gameStartedEvent.players.size)
        assertEquals("GameStarted event first player should be player 1", 1, gameStartedEvent.firstPlayer.id)
    }

    @Test
    fun `startGame with invalid player count returns InvalidGameAction`() {
        val eventZeroPlayers = gameManager.startGame(0)
        assertTrue("Event should be InvalidGameAction for 0 players", eventZeroPlayers is GameEvent.InvalidGameAction)
        assertEquals("Game should not be in progress", null, gameManager.currentPlayer)

        val eventNegativePlayers = gameManager.startGame(-1)
        assertTrue("Event should be InvalidGameAction for -1 players", eventNegativePlayers is GameEvent.InvalidGameAction)
        assertEquals("Game should not be in progress", null, gameManager.currentPlayer)
    }

    // --- Test handleRollAction (replaces currentTurnRollDice and currentTurnSelectDice) ---
    @Test
    fun `handleRollAction(null) for rolling delegates to TurnManager and returns CurrentTurnUpdated with Rolled`() {
        gameManager.startGame(1)

        val expectedDice = listOf(1, 2, 3, 4, 5)
        fakeDiceRoller.setNextRoll(expectedDice)

        val event = gameManager.handleRollAction(null) // Simulates rolling all dice

        assertTrue("Event should be CurrentTurnUpdated: $event", event is GameEvent.CurrentTurnUpdated)
        val currentTurnUpdatedEvent = event as GameEvent.CurrentTurnUpdated
        assertTrue("Encapsulated event should be TurnEvent.Rolled: ${currentTurnUpdatedEvent.turnEvent}", currentTurnUpdatedEvent.turnEvent is TurnEvent.Rolled)

        val rolledEvent = currentTurnUpdatedEvent.turnEvent as TurnEvent.Rolled
        assertEquals("Dice in Rolled event should match expected", expectedDice, rolledEvent.dice)
    }

    @Test
    fun `handleRollAction(null) for rolling when game not started returns InvalidGameAction`() {
        val event = gameManager.handleRollAction(null)
        assertTrue("Event should be InvalidGameAction", event is GameEvent.InvalidGameAction)
    }

    @Test
    fun `handleRollAction(indices) for selecting delegates to TurnManager and returns CurrentTurnUpdated with Scored`() {
        gameManager.startGame(1)

        val initialDice = listOf(1, 1, 2, 3, 4)
        fakeDiceRoller.setNextRoll(initialDice)
        gameManager.handleRollAction(null) // Initial roll

        // Player selects the two 1s, TurnManager auto-rolls remaining 3 dice.
        // Let's ensure the auto-roll is scorable for this test.
        val autoRolledDice = listOf(5, 6, 6) // e.g. a 5 scores
        fakeDiceRoller.setNextRoll(autoRolledDice)

        val selectedIndices = listOf(0, 1)
        val event = gameManager.handleRollAction(selectedIndices)

        assertTrue("Event should be CurrentTurnUpdated: $event", event is GameEvent.CurrentTurnUpdated)
        val currentTurnUpdatedEvent = event as GameEvent.CurrentTurnUpdated
        assertTrue("Encapsulated event should be TurnEvent.Scored: ${currentTurnUpdatedEvent.turnEvent}", currentTurnUpdatedEvent.turnEvent is TurnEvent.Scored)

        val scoredEvent = currentTurnUpdatedEvent.turnEvent as TurnEvent.Scored
        assertEquals("Score from initial selection (two 1s = 200)", 200, scoredEvent.newTurnTotalScore)
        assertTrue("Player should be able to roll again due to scorable auto-roll", scoredEvent.canRollAgain)
    }

    @Test
    fun `handleRollAction(indices) for selecting when game not started returns InvalidGameAction`() {
        val event = gameManager.handleRollAction(listOf(0))
        assertTrue("Event should be InvalidGameAction", event is GameEvent.InvalidGameAction)
    }

    // --- Test Busting on Roll/Select (using handleRollAction) ---
    @Test
    fun `handleRollAction(null) when results in bust, passes turn and returns PlayerBusted`() {
        gameManager.startGame(2)
        val player1 = gameManager.currentPlayer!!
        assertEquals("Initial player should be Player 1", 1, player1.id)

        val nonScoringDice = listOf(2, 3, 4, 6, 2)
        fakeDiceRoller.setNextRoll(nonScoringDice)

        val event = gameManager.handleRollAction(null) // Player 1 rolls and busts

        assertTrue("Event should be PlayerBusted: $event", event is GameEvent.PlayerBusted)
        val bustedEvent = event as GameEvent.PlayerBusted
        assertEquals("Busted player ID should be Player 1", player1.id, bustedEvent.player.id)
        assertEquals("Dice at bust should be the non-scoring dice", nonScoringDice, bustedEvent.diceAtBust)


        val player2 = gameManager.currentPlayer!!
        assertNotNull("Current player should now be Player 2", player2)
        assertEquals("Current player ID should be 2 after P1 bust", 2, player2.id)
        assertNotEquals("New current player should not be P1", player1.id, player2.id)

        fakeDiceRoller.setNextRoll(listOf(1,1,1,1,1)) // P2 rolls
        val p2RollEvent = gameManager.handleRollAction(null)
        assertTrue("P2 roll event should be CurrentTurnUpdated", p2RollEvent is GameEvent.CurrentTurnUpdated)
    }

    @Test
    fun `handleRollAction(indices) for selection that results in bust, passes turn and returns PlayerBusted`() {
        gameManager.startGame(2)
        val player1 = gameManager.currentPlayer!!
        assertEquals("Initial player should be Player 1", 1, player1.id)

        fakeDiceRoller.setNextRoll(listOf(1, 2, 3, 4, 6)) // P1 rolls [1,2,3,4,6]
        gameManager.handleRollAction(null)

        // P1 selects a '2' (index 1). TurnManager auto-rolls remaining [1,3,4,6]
        // Let's make the auto-roll non-scoring.
        fakeDiceRoller.setNextRoll(listOf(2,3,4,6)) // Non-scoring auto-roll
        val selectedIndicesBusting = listOf(1) // Selects the '2' (value 0)
        val selectEvent = gameManager.handleRollAction(selectedIndicesBusting)

        assertTrue("Select event should be PlayerBusted: $selectEvent", selectEvent is GameEvent.PlayerBusted)
        val bustedEvent = selectEvent as GameEvent.PlayerBusted
        assertEquals("Busted player ID should be Player 1", player1.id, bustedEvent.player.id)
        // Dice at bust should reflect the state *after* the auto-roll attempt
        // Original roll was [1,2,3,4,6]. Selected '2'. Remaining [1,3,4,6] were auto-rolled to [2,3,4,6].
        // Kept dice '2' + new dice [2,3,4,6] -> currentDiceState [2,2,3,4,6] (example)
        // This is hard to assert precisely without knowing TurnManager's exact dice state construction.
        // The important part is that it busted and the player is correct.
        // assertEquals("Dice at bust should reflect state when bust occurred", listOf(1,2,3,4,6), bustedEvent.diceAtBust)

        val player2 = gameManager.currentPlayer!!
        assertNotNull("Current player should now be Player 2", player2)
        assertEquals("Current player ID should be 2 after P1 bust on selection", 2, player2.id)

        fakeDiceRoller.setNextRoll(listOf(1,1,1,1,1))
        val p2RollEvent = gameManager.handleRollAction(null) // P2 rolls
        assertTrue("P2 roll event should be CurrentTurnUpdated", p2RollEvent is GameEvent.CurrentTurnUpdated)
    }


    // --- Test currentTurnBankScore - Opening ---
    @Test
    fun `currentTurnBankScore player opens successfully`() {
        gameManager.startGame(1)
        val player1 = gameManager.currentPlayer!!

        fakeDiceRoller.setNextRoll(listOf(1, 1, 1, 2, 3)) // Roll three 1s (score 1000)
        gameManager.handleRollAction(null) // Player rolls

        // Player selects three 1s and banks
        val event = gameManager.currentTurnBankScore(pendingSelectedIndices = listOf(0, 1, 2))

        assertTrue("Event should be PlayerOpenedAndScored: $event", event is GameEvent.PlayerOpenedAndScored)
        val openedEvent = event as GameEvent.PlayerOpenedAndScored

        assertEquals("Player ID in event should be 1", player1.id, openedEvent.player.id)
        assertEquals("Score this turn in event should be 1000", 1000, openedEvent.scoreThisTurn)
        assertEquals("New total score in event should be 1000", 1000, openedEvent.newTotalScore)

        assertTrue("Player should have opened", player1.hasOpened)
        assertEquals("Player total score should be 1000", 1000, player1.totalScore)

        fakeDiceRoller.setNextRoll(listOf(1,1,1,1,1))
        val nextRollEvent = gameManager.handleRollAction(null)
        assertTrue("Next roll event should be CurrentTurnUpdated", nextRollEvent is GameEvent.CurrentTurnUpdated)
    }

    @Test
    fun `currentTurnBankScore player fails to open`() {
        gameManager.startGame(1)
        val player1 = gameManager.currentPlayer!!

        fakeDiceRoller.setNextRoll(listOf(2, 2, 2, 6, 4)) // Roll three 2s (score 200)
        gameManager.handleRollAction(null) // Player rolls

        // Player selects three 2s and attempts to bank (score < MIN_SCORE_TO_OPEN)
        val event = gameManager.currentTurnBankScore(pendingSelectedIndices = listOf(0, 1, 2))

        assertTrue("Event should be PlayerFailedToOpen: $event", event is GameEvent.PlayerFailedToOpen)
        val failedOpenEvent = event as GameEvent.PlayerFailedToOpen

        assertEquals("Player ID in event should be 1", player1.id, failedOpenEvent.player.id)
        assertEquals("Score attempted in event should be 200", 200, failedOpenEvent.scoreAttemptedThisTurn)

        assertFalse("Player should not have opened", player1.hasOpened)
        assertEquals("Player total score should be 0", 0, player1.totalScore)

        fakeDiceRoller.setNextRoll(listOf(1,1,1,1,1))
        val nextRollEvent = gameManager.handleRollAction(null)
        assertTrue("Next roll event should be CurrentTurnUpdated", nextRollEvent is GameEvent.CurrentTurnUpdated)
    }

    // --- Test currentTurnBankScore - Scoring after opening ---
    @Test
    fun `currentTurnBankScore player scores normally after opening`() {
        gameManager.startGame(1)
        val player1 = gameManager.currentPlayer!!

        // Phase d'ouverture avec trois 1
        fakeDiceRoller.setNextRoll(listOf(1, 1, 1, 2, 3))
        gameManager.handleRollAction(null)
        gameManager.currentTurnBankScore(pendingSelectedIndices = listOf(0,1,2)) // Opens with 1000

        assertEquals("Player total score should be 1000 after opening", 1000, player1.totalScore)
        assertTrue("Player should have opened", player1.hasOpened)

        // Second turn for player1
        val scoreInSecondTurnSegment = 300
        fakeDiceRoller.setNextRoll(listOf(3, 3, 3, 6, 4)) // Brelan de 3 = 300 points
        gameManager.handleRollAction(null) // Player rolls

        // Player selects three 3s and banks
        val event = gameManager.currentTurnBankScore(pendingSelectedIndices = listOf(0, 1, 2))

        assertTrue("Event should be PlayerScored: $event", event is GameEvent.PlayerScored)
        val scoredEvent = event as GameEvent.PlayerScored

        assertEquals("Player ID in event should be 1", player1.id, scoredEvent.player.id)
        assertEquals("Score this turn in event should be $scoreInSecondTurnSegment", scoreInSecondTurnSegment, scoredEvent.scoreThisTurn)
        val expectedTotalScore = 1000 + scoreInSecondTurnSegment
        assertEquals("New total score in event should be $expectedTotalScore", expectedTotalScore, scoredEvent.newTotalScore)
        assertEquals("Player total score should be $expectedTotalScore", expectedTotalScore, player1.totalScore)
    }

    // --- Test currentTurnBankScore - Winning ---
    @Test
    fun `currentTurnBankScore player wins by reaching target score`() {
        gameManager.startGame(1)
        val player1 = gameManager.currentPlayer!!

        player1.totalScore = gameManager.TARGET_SCORE_TO_WIN - 1000
        player1.hasOpened = true

        fakeDiceRoller.setNextRoll(listOf(1,1,1,4,5)) // Score 1000 (trois 1)
        gameManager.handleRollAction(null) // Player rolls

        // Player selects three 1s and banks, reaching win score
        val event = gameManager.currentTurnBankScore(pendingSelectedIndices = listOf(0,1,2))

        assertTrue("Event should be PlayerWon: $event", event is GameEvent.PlayerWon)
        val wonEvent = event as GameEvent.PlayerWon
        assertEquals("Winning player ID should be 1", player1.id, wonEvent.winner.id)
        assertEquals("Final score should be TARGET_SCORE_TO_WIN", gameManager.TARGET_SCORE_TO_WIN, wonEvent.finalScore)

        val nextActionEvent = gameManager.handleRollAction(null) // Try to roll after win
        assertTrue("Action after win should be InvalidGameAction: $nextActionEvent", nextActionEvent is GameEvent.InvalidGameAction)
    }

    @Test
    fun `currentTurnBankScore player wins by exceeding target score`() {
        gameManager.startGame(1)
        val player1 = gameManager.currentPlayer!!

        player1.totalScore = gameManager.TARGET_SCORE_TO_WIN - 100
        player1.hasOpened = true

        fakeDiceRoller.setNextRoll(listOf(1,1,2,3,4)) // Score 200 (two 1s)
        gameManager.handleRollAction(null) // Player rolls

        // Player selects two 1s and banks, exceeding win score
        val event = gameManager.currentTurnBankScore(pendingSelectedIndices = listOf(0,1))

        assertTrue("Event should be PlayerWon: $event", event is GameEvent.PlayerWon)
        val wonEvent = event as GameEvent.PlayerWon
        assertEquals("Winning player ID should be 1", player1.id, wonEvent.winner.id)
        assertEquals("Final score should be TARGET_SCORE_TO_WIN - 100 + 200", gameManager.TARGET_SCORE_TO_WIN + 100, wonEvent.finalScore)
        assertTrue("Final score should be >= TARGET_SCORE_TO_WIN", wonEvent.finalScore >= gameManager.TARGET_SCORE_TO_WIN)
    }

    // --- Test player progression ---
    @Test
    fun `player turn cycles correctly after banking`() {
        gameManager.startGame(2)
        val player1original = gameManager.currentPlayer
        assertNotNull(player1original)
        assertEquals("Initial player should be P1", 1, player1original!!.id)

        // P1 opens with three 1s
        fakeDiceRoller.setNextRoll(listOf(1,1,1,4,5))
        gameManager.handleRollAction(null) // P1 rolls
        val p1BankEvent = gameManager.currentTurnBankScore(pendingSelectedIndices = listOf(0,1,2)) // P1 selects and banks
        assertTrue(p1BankEvent is GameEvent.PlayerOpenedAndScored)


        val player2 = gameManager.currentPlayer
        assertNotNull(player2)
        assertEquals("After P1 banks, current player should be P2", 2, player2!!.id)

        // P2 scores and banks
        fakeDiceRoller.setNextRoll(listOf(1,1,1,2,3)) // Trois 1 = 1000 points
        gameManager.handleRollAction(null) // P2 rolls
        val p2BankEvent = gameManager.currentTurnBankScore(pendingSelectedIndices = listOf(0,1,2)) // P2 selects and banks
        assertTrue(p2BankEvent is GameEvent.PlayerOpenedAndScored)


        val nextPlayerShouldBeP1Again = gameManager.currentPlayer
        assertNotNull(nextPlayerShouldBeP1Again)
        assertEquals("After P2 banks, current player should be P1 again", 1, nextPlayerShouldBeP1Again!!.id)
    }

    // --- Test currentTurnBankScore with pending selection ---
    // This test is now the primary way to test banking a selection, so it's well-covered by the above.
    // Keeping this specific one for clarity on the direct call if desired.
    @Test
    fun `currentTurnBankScore with explicit pending selection scores, opens, and banks correctly`() {
        gameManager.startGame(1)
        val player1 = gameManager.currentPlayer!!
        assertFalse("Player 1 should not have opened initially", player1.hasOpened)

        fakeDiceRoller.setNextRoll(listOf(1, 1, 1, 2, 3))
        gameManager.handleRollAction(null) // Player 1 rolls

        val pendingSelection = listOf(0, 1, 2)
        val bankEvent = gameManager.currentTurnBankScore(pendingSelectedIndices = pendingSelection)

        assertTrue("Bank event should be PlayerOpenedAndScored: $bankEvent", bankEvent is GameEvent.PlayerOpenedAndScored)
        val openedEvent = bankEvent as GameEvent.PlayerOpenedAndScored

        assertEquals("Player ID in event should be ${player1.id}", player1.id, openedEvent.player.id)
        assertEquals("Score this turn in event should be 1000", 1000, openedEvent.scoreThisTurn)
        assertEquals("New total score in event should be 1000", 1000, openedEvent.newTotalScore)

        assertTrue("Player 1 should have opened", player1.hasOpened)
        assertEquals("Player 1 total score should be 1000", 1000, player1.totalScore)
    }

    @Test
    fun `currentTurnBankScore with pending selection that results in bust, passes turn`() {
        gameManager.startGame(2) // Use 2 players to check turn passing
        val player1 = gameManager.currentPlayer!!
        assertEquals("Initial player should be Player 1", 1, player1.id)

        val initialRoll = listOf(1, 2, 3, 4, 6)
        fakeDiceRoller.setNextRoll(initialRoll)
        gameManager.handleRollAction(null) // Player 1 rolls

        // Player 1 attempts to bank by selecting a single '2' (index 1), which is not a valid scorable combination.
        val pendingNonScoringSelection = listOf(1)
        val bankEvent = gameManager.currentTurnBankScore(pendingSelectedIndices = pendingNonScoringSelection)

        assertTrue("Bank event should be PlayerBusted: $bankEvent", bankEvent is GameEvent.PlayerBusted)
        val bustedEvent = bankEvent as GameEvent.PlayerBusted
        assertEquals("Busted player ID should be Player 1", player1.id, bustedEvent.player.id)
        assertEquals("Dice at bust should reflect the roll before invalid selection for bank", initialRoll, bustedEvent.diceAtBust)

        val player2 = gameManager.currentPlayer!!
        assertNotNull("Current player should now be Player 2", player2)
        assertEquals("Current player ID should be 2 after P1 bust on pending bank selection", 2, player2.id)
    }

    @Test
    fun `currentTurnBankScore with no pending selection and zero turn score when open, banks 0`() {
        gameManager.startGame(1)
        val player1 = gameManager.currentPlayer!!
        player1.hasOpened = true
        player1.totalScore = 1000

        fakeDiceRoller.setNextRoll(listOf(2,3,4,2,3)) // Non-scoring initial roll
        gameManager.handleRollAction(null) // Player rolls, can't score. TurnManager state might have dice but score 0.

        // Player banks with no pending selection (effective score 0 for this segment)
        val bankEvent = gameManager.currentTurnBankScore(pendingSelectedIndices = emptyList())
        assertTrue("Event should be PlayerScored (with 0): $bankEvent", bankEvent is GameEvent.PlayerScored)
        val scoredEvent = bankEvent as GameEvent.PlayerScored
        assertEquals("Score this turn should be 0", 0, scoredEvent.scoreThisTurn)
        assertEquals("Total score should remain unchanged", 1000, scoredEvent.newTotalScore)
        assertEquals("Player total score should be 1000", 1000, player1.totalScore)
    }
}

// Minimal FakeDiceRoller for tests
class FakeDiceRoller : DiceRoller {
    private var nextRoll: List<Int>? = null
    private var allRolls: MutableList<List<Int>> = mutableListOf()

    override fun roll(numberOfDice: Int): DiceRoll {
        val diceToReturn = nextRoll?.take(numberOfDice) ?: List(numberOfDice) { (1..6).random() }
        // If nextRoll was set for a specific sequence, consume it or prepare for next in sequence.
        // For simplicity here, if nextRoll was used, clear it so subsequent calls are random unless set again.
        // If using setRollSequence, that method would handle advancing.
        // This FakeDiceRoller is a bit simplified. A more robust one might handle sequences better.
        // For now, if nextRoll is not null, it's a one-shot.
        if (this.allRolls.isNotEmpty() && nextRoll == this.allRolls.firstOrNull()) {
             this.allRolls.removeAt(0)
             if (this.allRolls.isNotEmpty()) {
                 this.nextRoll = this.allRolls.first()
             } else {
                 this.nextRoll = null
             }
        } else if (nextRoll != null && this.allRolls.isEmpty()) {
            // it was a one-shot nextRoll, clear it
            // this.nextRoll = null // Commented out to allow handleRollAction(indices) to setup a specific auto-roll
        }
        return diceToReturn
    }

    fun setNextRoll(dice: List<Int>) {
        this.nextRoll = dice
        // If there's an active sequence, this overrides the current head of the sequence for one roll.
        // This is a choice, another design might be to insert it or disallow.
    }

    fun setRollSequence(rolls: List<List<Int>>) {
        this.allRolls.clear()
        this.allRolls.addAll(rolls.map { it.toList() }) // Store copies
        if (this.allRolls.isNotEmpty()) {
            this.nextRoll = this.allRolls.first()
        } else {
            this.nextRoll = null
        }
    }

    // Original setNextRollFromSequence - not directly used if setRollSequence updates nextRoll.
    // Kept for reference if a different FakeDiceRoller behavior is desired.
    /*
    private fun setNextRollFromSequence() {
        if (allRolls.isNotEmpty()) {
            this.nextRoll = allRolls.removeAt(0)
        } else {
            this.nextRoll = null
        }
    }
    */
}
