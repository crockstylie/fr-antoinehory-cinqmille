package fr.antoinehory.cinqmille.ui.game

import app.cash.turbine.test
import fr.antoinehory.cinqmille.game.FakeDiceRoller
import fr.antoinehory.cinqmille.game.GameManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class GameViewModelTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var viewModel: GameViewModel
    private lateinit var fakeDiceRoller: FakeDiceRoller
    private lateinit var gameManager: GameManager

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        fakeDiceRoller = FakeDiceRoller()
        gameManager = GameManager(fakeDiceRoller)
        viewModel = GameViewModel(gameManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            val initialState = awaitItem() // Initial state emitted on collection
            assertEquals("Bienvenue au Cinq Mille ! Choisissez le nombre de joueurs pour commencer.", initialState.currentMessage)
            assertTrue(initialState.players.isEmpty())
            assertNull(initialState.currentPlayerId)
            assertTrue(initialState.currentDiceRoll.isEmpty())
            assertEquals(0, initialState.accumulatedTurnScore)
            assertFalse(initialState.isRollButtonEnabled) // Corrected: should be false initially
            assertFalse(initialState.isBankButtonEnabled)
            assertEquals(0, initialState.previewSelectionScore)
            assertTrue(initialState.selectedDiceVisual.none { it })

            cancelAndConsumeRemainingEvents() // Ensure no other initial emissions if any
        }
    }

    @Test
    fun `startGame updates UI state correctly`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            assertEquals("Bienvenue au Cinq Mille ! Choisissez le nombre de joueurs pour commencer.", awaitItem().currentMessage) // Initial state

            viewModel.startGame(1)
            advanceUntilIdle()

            val stateAfterStart = awaitItem() // State after startGame
            assertEquals(1, stateAfterStart.players.size)
            assertEquals(1, stateAfterStart.currentPlayerId)
            assertEquals("Joueur 1, à vous de commencer !", stateAfterStart.currentMessage)
            assertTrue(stateAfterStart.isRollButtonEnabled)
            assertFalse(stateAfterStart.isBankButtonEnabled)
            assertEquals(List(GameUiState.INITIAL_DICE_COUNT) { 0 }, stateAfterStart.currentDiceRoll)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `rollDice with scorable roll updates UI with dice`() = runTest(testDispatcher) {
        viewModel.startGame(1)
        advanceUntilIdle() // startGame emits a state

        val scorableRoll = listOf(1, 5, 2, 3, 4)
        fakeDiceRoller.setNextRoll(scorableRoll)

        viewModel.uiState.test {
            awaitItem() // Consume the state from startGame

            viewModel.rollDice()
            advanceUntilIdle()

            val stateAfterRoll = awaitItem()
            assertEquals(scorableRoll, stateAfterRoll.currentDiceRoll)
            assertTrue(stateAfterRoll.currentMessage.contains("Sélectionnez vos dés."))
            assertTrue(stateAfterRoll.isRollButtonEnabled)
            assertFalse(stateAfterRoll.isBankButtonEnabled)
            assertEquals(0, stateAfterRoll.previewSelectionScore)
            assertTrue(stateAfterRoll.selectedDiceVisual.none { it })

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `rollDice with bust roll updates UI`() = runTest(testDispatcher) {
        viewModel.startGame(1)
        advanceUntilIdle() // startGame emits a state

        val bustRoll = listOf(2, 3, 4, 6, 2)
        fakeDiceRoller.setNextRoll(bustRoll)

        viewModel.uiState.test {
            awaitItem() // Consume the state from startGame

            viewModel.rollDice()
            advanceUntilIdle()

            val stateAfterBust = awaitItem()
            assertEquals(bustRoll, stateAfterBust.currentDiceRoll)
            // Message update for bust in GameManager changed slightly to include dice
            assertTrue(stateAfterBust.currentMessage.contains("Joueur 1 a busté ! Dés: 2, 3, 4, 6, 2."))
            assertEquals(0, stateAfterBust.accumulatedTurnScore)
            // The current logic in GameViewModel upon TurnEvent.Busted leads to:
            // isRollButtonEnabled = false, isBankButtonEnabled = false
            // Then upon GameEvent.PlayerBusted -> isRollButtonEnabled = true (for next player)
            // The state we are checking here is *after* GameEvent.PlayerBusted is processed.
            assertTrue(stateAfterBust.isRollButtonEnabled) // Next player's turn, roll is enabled.
            assertFalse(stateAfterBust.isBankButtonEnabled) // Bank is disabled at start of new turn.
            assertTrue(stateAfterBust.selectedDiceVisual.all { !it })
            assertEquals(List(GameUiState.INITIAL_DICE_COUNT){true}, stateAfterBust.scoredDiceMask)


            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `toggleDieSelection updates selectedDiceVisual and previewSelectionScore`() = runTest(testDispatcher) {
        viewModel.startGame(1)
        advanceUntilIdle() // startGame emits state

        val initialRoll = listOf(1, 5, 2, 3, 4) // 1=100, 5=50
        fakeDiceRoller.setNextRoll(initialRoll)
        viewModel.rollDice()
        advanceUntilIdle() // rollDice emits state

        viewModel.uiState.test {
            var state = awaitItem() // State after rollDice
            assertEquals(initialRoll, state.currentDiceRoll)
            assertTrue(state.selectedDiceVisual.none { it })
            assertEquals(0, state.previewSelectionScore)

            viewModel.toggleDieSelection(0) // Select '1'
            advanceUntilIdle()
            state = awaitItem()
            assertTrue(state.selectedDiceVisual[0])
            assertEquals(100, state.previewSelectionScore)

            viewModel.toggleDieSelection(1) // Select '5'
            advanceUntilIdle()
            state = awaitItem()
            assertTrue(state.selectedDiceVisual[0])
            assertTrue(state.selectedDiceVisual[1])
            assertEquals(150, state.previewSelectionScore)

            viewModel.toggleDieSelection(0) // Deselect '1'
            advanceUntilIdle()
            state = awaitItem()
            assertFalse(state.selectedDiceVisual[0])
            assertTrue(state.selectedDiceVisual[1])
            assertEquals(50, state.previewSelectionScore)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `bankScore with pending selection successfully opens player`() = runTest(testDispatcher) {
        val player1Id = 1
        viewModel.startGame(player1Id)
        advanceUntilIdle() // startGame emits state

        val openingRoll = listOf(1, 1, 1, 2, 3) // Three 1s = 1000 points
        fakeDiceRoller.setNextRoll(openingRoll)
        viewModel.rollDice()
        advanceUntilIdle() // rollDice emits state

        viewModel.toggleDieSelection(0)
        advanceUntilIdle() // toggleDieSelection emits state
        viewModel.toggleDieSelection(1)
        advanceUntilIdle() // toggleDieSelection emits state
        viewModel.toggleDieSelection(2)
        advanceUntilIdle() // toggleDieSelection emits state

        viewModel.uiState.test {
            var state = awaitItem() // State after selections
            assertEquals(1000, state.previewSelectionScore)
            assertFalse(state.players.first().hasOpened)

            viewModel.bankScore() // Uses pending selection
            advanceUntilIdle()

            state = awaitItem() // State after bankScore processing (includes PlayerOpenedAndScored effects)

            assertTrue("Message check. Got: ${state.currentMessage}", state.currentMessage.startsWith("Joueur $player1Id a ouvert avec 1000"))
            assertEquals(1, state.players.size)
            assertEquals(1000, state.players.first { it.id == player1Id }.totalScore)
            assertTrue(state.players.first { it.id == player1Id }.hasOpened)

            assertEquals(0, state.accumulatedTurnScore) // Reset for next turn
            assertTrue(state.isRollButtonEnabled)      // Enabled for next turn
            assertFalse(state.isBankButtonEnabled)     // Disabled for start of next turn
            assertEquals(List(GameUiState.INITIAL_DICE_COUNT) { 0 }, state.currentDiceRoll) // Cleared for next turn

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `rollDice with pending selection scores dice and updates UI for next roll`() = runTest(testDispatcher) {
        viewModel.startGame(1)
        advanceUntilIdle() // startGame emits state

        val initialRoll = listOf(1, 1, 5, 2, 3) // Two 1s (200), one 5 (50)
        fakeDiceRoller.setNextRoll(initialRoll)
        viewModel.rollDice() // Initial roll
        advanceUntilIdle() // rollDice emits state

        viewModel.toggleDieSelection(0)
        advanceUntilIdle() // toggleDieSelection emits state
        viewModel.toggleDieSelection(1)
        advanceUntilIdle() // toggleDieSelection emits state

        viewModel.uiState.test {
            var state = awaitItem() // State after selections
            assertEquals(200, state.previewSelectionScore)

            val nextDiceRollForRemaining = listOf(6, 6, 6) // Brelan of 6 = 600
            fakeDiceRoller.setNextRoll(nextDiceRollForRemaining)

            viewModel.rollDice() // Should use pending selection (two 1s), score 200, then re-roll 3 dice.
            // GameManager's event newTurnTotalScore likely only reflects the 200 for this emission.
            advanceUntilIdle()

            state = awaitItem() // State after scoring selection and re-rolling.
            assertEquals(200, state.accumulatedTurnScore) // Corrected expectation
            assertTrue(state.currentMessage.contains("Score ce tour : 200. Relancez ou banquez.")) // Corrected expectation
            assertEquals(nextDiceRollForRemaining, state.currentDiceRoll.drop(2).take(nextDiceRollForRemaining.size))
            assertTrue(state.isRollButtonEnabled)
            assertTrue(state.isBankButtonEnabled) // Bank should be enabled as accumulatedTurnScore (200) > 0
            assertTrue(state.scoredDiceMask[0])
            assertTrue(state.scoredDiceMask[1])
            assertFalse(state.scoredDiceMask[2]) // Only first two dice were part of the initial scored segment

            cancelAndConsumeRemainingEvents()
        }
    }
}

