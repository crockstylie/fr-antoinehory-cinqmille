package fr.antoinehory.cinqmille.game

import org.junit.Assert.* // JUnit 4 style assertions
import org.junit.Test

class PlayerTest {

    @Test
    fun `player creation with default values is correct`() {
        val player = Player(id = 1)
        assertEquals(1, player.id)
        assertEquals(0, player.totalScore)
        assertFalse(player.hasOpened)
        assertEquals(0, player.lastKnownTurnScore)
        // assertFalse(player.isCurrentPlayer) // Supprimé
    }

    @Test
    fun `player creation with all parameters is correct`() {
        // isCurrentPlayer n'est plus un paramètre du constructeur principal de Player
        val player = Player(
            id = 2,
            totalScore = 1000,
            hasOpened = true,
            lastKnownTurnScore = 250
            // isCurrentPlayer = true // Supprimé
        )
        assertEquals(2, player.id)
        assertEquals(1000, player.totalScore)
        assertTrue(player.hasOpened)
        assertEquals(250, player.lastKnownTurnScore)
        // assertTrue(player.isCurrentPlayer) // Supprimé
    }

    @Test
    fun `modifying totalScore works`() {
        val player = Player(id = 1)
        player.totalScore = 500
        assertEquals(500, player.totalScore)
    }

    @Test
    fun `modifying hasOpened works`() {
        val player = Player(id = 1)
        player.hasOpened = true
        assertTrue(player.hasOpened)
    }

    @Test
    fun `modifying lastKnownTurnScore works`() {
        val player = Player(id = 1)
        player.lastKnownTurnScore = 150
        assertEquals(150, player.lastKnownTurnScore)
    }

    // @Test // Supprimé
    // fun `modifying isCurrentPlayer works`() {
    //     val player = Player(id = 1)
    //     player.isCurrentPlayer = true
    //     assertTrue(player.isCurrentPlayer)
    // }

    @Test
    fun `copy creates a new instance with potentially modified values`() {
        val player1 = Player(id = 1, totalScore = 100)
        val player2 = player1.copy(totalScore = 200)

        assertNotSame(player1, player2)
        assertEquals(1, player2.id)
        assertEquals(200, player2.totalScore)

        val player3 = player1.copy() // Copie simple
        assertNotSame(player1, player3)
        assertEquals(player1.id, player3.id)
        assertEquals(player1.totalScore, player3.totalScore)
        assertEquals(player1.hasOpened, player3.hasOpened)
        assertEquals(player1.lastKnownTurnScore, player3.lastKnownTurnScore)
        // assertEquals(player1.isCurrentPlayer, player3.isCurrentPlayer) // Supprimé

        player3.totalScore = 5000
        assertEquals(100, player1.totalScore)
        assertEquals(5000, player3.totalScore)
    }
}
