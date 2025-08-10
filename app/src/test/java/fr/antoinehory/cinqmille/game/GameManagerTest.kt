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

    // --- Test currentTurnRollDice ---
    @Test
    fun `currentTurnRollDice delegates to TurnManager and returns CurrentTurnUpdated`() {
        gameManager.startGame(1)

        val expectedDice = listOf(1, 2, 3, 4, 5)
        fakeDiceRoller.setNextRoll(expectedDice)

        val event = gameManager.currentTurnRollDice()

        assertTrue("Event should be CurrentTurnUpdated", event is GameEvent.CurrentTurnUpdated)
        val currentTurnUpdatedEvent = event as GameEvent.CurrentTurnUpdated
        assertTrue("Encapsulated event should be TurnEvent.Rolled", currentTurnUpdatedEvent.turnEvent is TurnEvent.Rolled)

        val rolledEvent = currentTurnUpdatedEvent.turnEvent as TurnEvent.Rolled
        assertEquals("Dice in Rolled event should match expected", expectedDice, rolledEvent.dice)
    }

    @Test
    fun `currentTurnRollDice when game not started returns InvalidGameAction`() {
        val event = gameManager.currentTurnRollDice()
        assertTrue("Event should be InvalidGameAction", event is GameEvent.InvalidGameAction)
    }

    // --- Test currentTurnSelectDice ---
    @Test
    fun `currentTurnSelectDice delegates to TurnManager and returns CurrentTurnUpdated`() {
        gameManager.startGame(1)

        val initialDice = listOf(1, 1, 2, 3, 4)
        fakeDiceRoller.setNextRoll(initialDice)
        gameManager.currentTurnRollDice()

        val selectedIndices = listOf(0, 1)
        val event = gameManager.currentTurnSelectDice(selectedIndices)

        assertTrue("Event should be CurrentTurnUpdated", event is GameEvent.CurrentTurnUpdated)
        val currentTurnUpdatedEvent = event as GameEvent.CurrentTurnUpdated
        assertTrue("Encapsulated event should be TurnEvent.Scored: $currentTurnUpdatedEvent", currentTurnUpdatedEvent.turnEvent is TurnEvent.Scored)

        val scoredEvent = currentTurnUpdatedEvent.turnEvent as TurnEvent.Scored
        assertEquals("Score from selection should be 200", 200, scoredEvent.scoreFromSelection)
        assertEquals("New turn total score should be 200", 200, scoredEvent.newTurnTotalScore)
        assertEquals("Dice for next potential roll should be 3", 3, scoredEvent.diceForNextPotentialRoll)
        assertTrue("Player should be able to roll again", scoredEvent.canRollAgain)
    }

    @Test
    fun `currentTurnSelectDice when game not started returns InvalidGameAction`() {
        val event = gameManager.currentTurnSelectDice(listOf(0))
        assertTrue("Event should be InvalidGameAction", event is GameEvent.InvalidGameAction)
    }

    // --- Test Busting on Roll/Select ---
    @Test
    fun `currentTurnRollDice when results in bust, passes turn and returns PlayerBusted`() {
        gameManager.startGame(2)
        val player1 = gameManager.currentPlayer!!
        assertEquals("Initial player should be Player 1", 1, player1.id)

        val nonScoringDice = listOf(2, 3, 4, 6, 2)
        fakeDiceRoller.setNextRoll(nonScoringDice)

        val event = gameManager.currentTurnRollDice()

        assertTrue("Event should be PlayerBusted: $event", event is GameEvent.PlayerBusted)
        val bustedEvent = event as GameEvent.PlayerBusted
        assertEquals("Busted player ID should be Player 1", player1.id, bustedEvent.player.id)

        val player2 = gameManager.currentPlayer!!
        assertNotNull("Current player should now be Player 2", player2)
        assertEquals("Current player ID should be 2 after P1 bust", 2, player2.id)
        assertNotEquals("New current player should not be P1", player1.id, player2.id)

        fakeDiceRoller.setNextRoll(listOf(1,1,1,1,1))
        val p2RollEvent = gameManager.currentTurnRollDice()
        assertTrue("P2 roll event should be CurrentTurnUpdated", p2RollEvent is GameEvent.CurrentTurnUpdated)
        val castedP2RollEvent = p2RollEvent as GameEvent.CurrentTurnUpdated
        assertTrue("Encapsulated P2 event should be Rolled", castedP2RollEvent.turnEvent is TurnEvent.Rolled)
        val p2Rolled = castedP2RollEvent.turnEvent as TurnEvent.Rolled
        assertEquals("P2 roll should use 5 dice", 5, p2Rolled.dice.size)
    }

    @Test
    fun `currentTurnSelectDice when results in bust, passes turn and returns PlayerBusted`() {
        gameManager.startGame(2)
        val player1 = gameManager.currentPlayer!!
        assertEquals("Initial player should be Player 1", 1, player1.id)

        fakeDiceRoller.setNextRoll(listOf(1, 2, 3, 4, 6))
        val rollEvent = gameManager.currentTurnRollDice()
        assertTrue("Roll event for P1 should be CurrentTurnUpdated", rollEvent is GameEvent.CurrentTurnUpdated)
        val castedRollEvent = rollEvent as GameEvent.CurrentTurnUpdated
        assertTrue("Encapsulated P1 roll event should be Rolled", castedRollEvent.turnEvent is TurnEvent.Rolled)

        val selectedIndices = listOf(1)
        val selectEvent = gameManager.currentTurnSelectDice(selectedIndices)

        assertTrue("Select event should be PlayerBusted: $selectEvent", selectEvent is GameEvent.PlayerBusted)
        val bustedEvent = selectEvent as GameEvent.PlayerBusted
        assertEquals("Busted player ID should be Player 1", player1.id, bustedEvent.player.id)

        val player2 = gameManager.currentPlayer!!
        assertNotNull("Current player should now be Player 2", player2)
        assertEquals("Current player ID should be 2 after P1 bust on selection", 2, player2.id)

        fakeDiceRoller.setNextRoll(listOf(1,1,1,1,1))
        val p2RollEvent = gameManager.currentTurnRollDice()
        assertTrue("P2 roll event should be CurrentTurnUpdated", p2RollEvent is GameEvent.CurrentTurnUpdated)
    }


    // --- Test currentTurnBankScore - Opening ---
    @Test
    fun `currentTurnBankScore player opens successfully`() {
        gameManager.startGame(1)
        val player1 = gameManager.currentPlayer!!

        // MODIFIÉ: Utiliser trois 1 pour un score d'ouverture de 1000
        fakeDiceRoller.setNextRoll(listOf(1, 1, 1, 2, 3))
        gameManager.currentTurnRollDice()

        // MODIFIÉ: Sélectionner les trois 1
        gameManager.currentTurnSelectDice(listOf(0, 1, 2))

        val event = gameManager.currentTurnBankScore()

        assertTrue("Event should be PlayerOpenedAndScored: $event", event is GameEvent.PlayerOpenedAndScored)
        val openedEvent = event as GameEvent.PlayerOpenedAndScored

        assertEquals("Player ID in event should be 1", player1.id, openedEvent.player.id)
        assertEquals("Score this turn in event should be 1000", 1000, openedEvent.scoreThisTurn) // Attendu 1000
        assertEquals("New total score in event should be 1000", 1000, openedEvent.newTotalScore) // Attendu 1000

        assertTrue("Player should have opened", player1.hasOpened)
        assertEquals("Player total score should be 1000", 1000, player1.totalScore) // Attendu 1000

        fakeDiceRoller.setNextRoll(listOf(1,1,1,1,1))
        val nextRollEvent = gameManager.currentTurnRollDice()
        assertTrue("Next roll event should be CurrentTurnUpdated", nextRollEvent is GameEvent.CurrentTurnUpdated)
        val castedNextRollEvent = nextRollEvent as GameEvent.CurrentTurnUpdated
        assertTrue("Encapsulated event should be Rolled", castedNextRollEvent.turnEvent is TurnEvent.Rolled)
        val rolledEvent = castedNextRollEvent.turnEvent as TurnEvent.Rolled
        assertEquals("Next roll should use 5 dice after banking", 5, rolledEvent.dice.size)
    }

    @Test
    fun `currentTurnBankScore player fails to open`() {
        gameManager.startGame(1)
        val player1 = gameManager.currentPlayer!!

        fakeDiceRoller.setNextRoll(listOf(2, 2, 2, 6, 4))
        gameManager.currentTurnRollDice()

        gameManager.currentTurnSelectDice(listOf(0, 1, 2))

        val event = gameManager.currentTurnBankScore()

        assertTrue("Event should be PlayerFailedToOpen: $event", event is GameEvent.PlayerFailedToOpen)
        val failedOpenEvent = event as GameEvent.PlayerFailedToOpen

        assertEquals("Player ID in event should be 1", player1.id, failedOpenEvent.player.id)
        assertEquals("Score attempted in event should be 200", 200, failedOpenEvent.scoreAttemptedThisTurn)

        assertFalse("Player should not have opened", player1.hasOpened)
        assertEquals("Player total score should be 0", 0, player1.totalScore)

        fakeDiceRoller.setNextRoll(listOf(1,1,1,1,1))
        val nextRollEvent = gameManager.currentTurnRollDice()
        assertTrue("Next roll event should be CurrentTurnUpdated", nextRollEvent is GameEvent.CurrentTurnUpdated)
        val castedNextRollEvent = nextRollEvent as GameEvent.CurrentTurnUpdated
        assertTrue("Encapsulated event should be Rolled", castedNextRollEvent.turnEvent is TurnEvent.Rolled)
        val rolledEvent = castedNextRollEvent.turnEvent as TurnEvent.Rolled
        assertEquals("Next roll should use 5 dice after failing to open", 5, rolledEvent.dice.size)
    }

    // --- Test currentTurnBankScore - Scoring after opening ---
    @Test
    fun `currentTurnBankScore player scores normally after opening`() {
        gameManager.startGame(1)
        val player1 = gameManager.currentPlayer!!

        // MODIFIÉ: Phase d'ouverture avec trois 1
        fakeDiceRoller.setNextRoll(listOf(1, 1, 1, 2, 3))
        gameManager.currentTurnRollDice()
        gameManager.currentTurnSelectDice(listOf(0, 1, 2))
        gameManager.currentTurnBankScore()

        assertEquals("Player total score should be 1000 after opening", 1000, player1.totalScore) // Attendu 1000
        assertTrue("Player should have opened", player1.hasOpened)

        val scoreInSecondTurn = 300
        fakeDiceRoller.setNextRoll(listOf(3, 3, 3, 6, 4)) // Brelan de 3 = 300 points
        gameManager.currentTurnRollDice()
        gameManager.currentTurnSelectDice(listOf(0, 1, 2))

        val event = gameManager.currentTurnBankScore()

        assertTrue("Event should be PlayerScored: $event", event is GameEvent.PlayerScored)
        val scoredEvent = event as GameEvent.PlayerScored

        assertEquals("Player ID in event should be 1", player1.id, scoredEvent.player.id)
        assertEquals("Score this turn in event should be $scoreInSecondTurn", scoreInSecondTurn, scoredEvent.scoreThisTurn)
        val expectedTotalScore = 1000 + scoreInSecondTurn // 1000 (ouverture) + 300 (ce tour)
        assertEquals("New total score in event should be $expectedTotalScore", expectedTotalScore, scoredEvent.newTotalScore)

        assertEquals("Player total score should be $expectedTotalScore", expectedTotalScore, player1.totalScore)

        fakeDiceRoller.setNextRoll(listOf(1,1,1,1,1))
        val nextRollEvent = gameManager.currentTurnRollDice()
        assertTrue("Next roll event should be CurrentTurnUpdated", nextRollEvent is GameEvent.CurrentTurnUpdated)
        val castedNextRollEvent = nextRollEvent as GameEvent.CurrentTurnUpdated
        assertTrue("Encapsulated event should be Rolled", castedNextRollEvent.turnEvent is TurnEvent.Rolled)
        val rolledEvent = castedNextRollEvent.turnEvent as TurnEvent.Rolled
        assertEquals("Next roll should use 5 dice", 5, rolledEvent.dice.size)
    }

    // --- Test currentTurnBankScore - Winning ---
    @Test
    fun `currentTurnBankScore player wins by reaching target score`() {
        gameManager.startGame(1)
        val player1 = gameManager.currentPlayer!!

        player1.totalScore = gameManager.TARGET_SCORE_TO_WIN - 1000
        player1.hasOpened = true

        // MODIFIÉ: Score 1000 (trois 1) pour gagner
        fakeDiceRoller.setNextRoll(listOf(1,1,1,4,5))
        gameManager.currentTurnRollDice()
        gameManager.currentTurnSelectDice(listOf(0,1,2))

        val event = gameManager.currentTurnBankScore()

        assertTrue("Event should be PlayerWon: $event", event is GameEvent.PlayerWon)
        val wonEvent = event as GameEvent.PlayerWon
        assertEquals("Winning player ID should be 1", player1.id, wonEvent.winner.id)
        assertEquals("Final score should be TARGET_SCORE_TO_WIN", gameManager.TARGET_SCORE_TO_WIN, wonEvent.finalScore)

        val nextActionEvent = gameManager.currentTurnRollDice()
        assertTrue("Action after win should be InvalidGameAction: $nextActionEvent", nextActionEvent is GameEvent.InvalidGameAction)
    }

    @Test
    fun `currentTurnBankScore player wins by exceeding target score`() {
        gameManager.startGame(1)
        val player1 = gameManager.currentPlayer!!

        player1.totalScore = gameManager.TARGET_SCORE_TO_WIN - 100
        player1.hasOpened = true

        fakeDiceRoller.setNextRoll(listOf(1,1,2,3,4)) // Score 200
        gameManager.currentTurnRollDice()
        gameManager.currentTurnSelectDice(listOf(0,1))

        val event = gameManager.currentTurnBankScore()

        assertTrue("Event should be PlayerWon: $event", event is GameEvent.PlayerWon)
        val wonEvent = event as GameEvent.PlayerWon
        assertEquals("Winning player ID should be 1", player1.id, wonEvent.winner.id)
        assertEquals("Final score should be TARGET_SCORE_TO_WIN - 100 + 200", gameManager.TARGET_SCORE_TO_WIN + 100, wonEvent.finalScore)
        assertTrue("Final score should be >= TARGET_SCORE_TO_WIN", wonEvent.finalScore >= gameManager.TARGET_SCORE_TO_WIN)

        val nextActionEvent = gameManager.currentTurnRollDice()
        assertTrue("Action after win should be InvalidGameAction: $nextActionEvent", nextActionEvent is GameEvent.InvalidGameAction)
    }

    // --- Test player progression ---
    @Test
    fun `player turn cycles correctly after banking`() {
        gameManager.startGame(2)
        val player1 = gameManager.currentPlayer
        assertNotNull(player1)
        assertEquals("Initial player should be P1", 1, player1!!.id)

        // MODIFIÉ: P1 ouvre avec trois 1
        fakeDiceRoller.setNextRoll(listOf(1,1,1,4,5))
        gameManager.currentTurnRollDice()
        gameManager.currentTurnSelectDice(listOf(0,1,2))
        gameManager.currentTurnBankScore()

        val player2 = gameManager.currentPlayer
        assertNotNull(player2)
        assertEquals("After P1 banks, current player should be P2", 2, player2!!.id)

        // P2 scores and banks (ouvre si score >= 750)
        fakeDiceRoller.setNextRoll(listOf(1,1,1,2,3)) // Trois 1 = 1000 points
        gameManager.currentTurnRollDice()
        gameManager.currentTurnSelectDice(listOf(0,1,2))
        gameManager.currentTurnBankScore()

        val nextPlayerShouldBeP1 = gameManager.currentPlayer
        assertNotNull(nextPlayerShouldBeP1)
        assertEquals("After P2 banks, current player should be P1 again", 1, nextPlayerShouldBeP1!!.id)
    }
}

