package fr.antoinehory.cinqmille.ui.game

import app.cash.turbine.test
import fr.antoinehory.cinqmille.game.FakeDiceRoller // Assurez-vous que le chemin est correct
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

    private lateinit var testDispatcher: TestDispatcher // Utilisé pour contrôler le temps des coroutines
    private lateinit var viewModel: GameViewModel
    private lateinit var fakeDiceRoller: FakeDiceRoller
    private lateinit var gameManager: GameManager

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher() // Ou UnconfinedTestDispatcher pour des tests plus simples
        Dispatchers.setMain(testDispatcher)

        fakeDiceRoller = FakeDiceRoller()
        gameManager = GameManager(fakeDiceRoller)
        viewModel = GameViewModel(gameManager) // Assurez-vous que GameViewModel prend GameManager en constructeur
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest(testDispatcher) {
        val initialState = viewModel.uiState.value
        assertEquals("Bienvenue au Cinq Mille !", initialState.currentMessage)
        assertTrue(initialState.players.isEmpty())
        assertNull(initialState.currentPlayerId)
        assertTrue(initialState.currentDiceRoll.isEmpty())
        assertEquals(0, initialState.currentTurnScore)
        assertTrue(initialState.isRollButtonEnabled)
        assertFalse(initialState.isBankButtonEnabled)
    }

    @Test
    fun `startGame updates UI state correctly`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            var state = awaitItem() // Initial state
            assertEquals("Bienvenue au Cinq Mille !", state.currentMessage)

            viewModel.startGame(1)
            advanceUntilIdle() // Exécuter les coroutines en attente sur le testDispatcher

            state = awaitItem() // State after startGame
            assertEquals(1, state.players.size)
            assertEquals(1, state.currentPlayerId)
            assertEquals("Joueur 1, à vous de commencer !", state.currentMessage)
            // ... autres assertions ...

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `rollDice with scorable roll updates UI with dice`() = runTest(testDispatcher) {
        viewModel.startGame(1) // Start game to have an active player
        advanceUntilIdle()

        val scorableRoll = listOf(1, 5, 2, 3, 4) // e.g., 1=100, 5=50 -> 150 points
        fakeDiceRoller.setNextRoll(scorableRoll)

        viewModel.uiState.test {
            awaitItem() // State after startGame

            viewModel.rollDice()
            advanceUntilIdle() // Important: Laisser le temps à la coroutine de rollDice et à l'update de se faire

            val stateAfterRoll = awaitItem()

            assertEquals("Les dés affichés doivent correspondre au lancer scorable.", scorableRoll, stateAfterRoll.currentDiceRoll)
            assertFalse("Les dés ne doivent pas être vides après un lancer scorable.", stateAfterRoll.currentDiceRoll.isEmpty())
            assertTrue("Le message doit indiquer de sélectionner les dés.", stateAfterRoll.currentMessage.contains("Sélectionnez vos dés"))
            assertTrue("Le bouton Lancer doit être activé si le lancer est scorable.", stateAfterRoll.isRollButtonEnabled)
            assertFalse("Le bouton Banquer doit être désactivé avant sélection.", stateAfterRoll.isBankButtonEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `rollDice with bust roll updates UI with empty dice and bust message`() = runTest(testDispatcher) {
        viewModel.startGame(1)
        advanceUntilIdle()

        val bustRoll = listOf(2, 3, 4, 6, 2) // Non-scoring roll
        fakeDiceRoller.setNextRoll(bustRoll)

        viewModel.uiState.test {
            awaitItem() // State after startGame

            viewModel.rollDice()
            advanceUntilIdle()

            // GameManager.currentTurnRollDice(), s'il détecte un bust via TurnManager,
            // devrait émettre GameEvent.PlayerBusted.
            // GameViewModel.updateUiStateFromGameEvent, pour PlayerBusted, met currentDiceRoll = emptyList().
            val stateAfterBust = awaitItem()

            assertTrue("Les dés devraient être vides après un bust complet du tour.", stateAfterBust.currentDiceRoll.isEmpty())
            assertTrue("Le message doit indiquer que le joueur 1 a busté.", stateAfterBust.currentMessage.contains("Joueur 1 a busté"))
            assertEquals(0, stateAfterBust.currentTurnScore)
            // Pour un seul joueur, le prochain joueur est toujours le même.
            // Si le jeu se terminait, currentPlayerId serait null.
            // Supposons que le jeu continue pour le joueur 1 (ou passe au prochain s'il y en avait).
            // Le bouton Lancer devrait être activé pour le "prochain" tour.
            assertTrue("Bouton Lancer devrait être actif pour le prochain tour/joueur", stateAfterBust.isRollButtonEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }
}
