package fr.antoinehory.cinqmille.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.antoinehory.cinqmille.game.GameManager // Pour le Preview
import fr.antoinehory.cinqmille.game.DefaultDiceRoller // Pour le Preview

/**
 * The main screen for the Cinq Mille game.
 * Displays the game state including player scores, current turn information, dice rolls,
 * and action buttons for the player.
 *
 * This Composable observes [GameUiState] from the [gameViewModel] to reactively update the UI.
 * User interactions are delegated to the [gameViewModel].
 *
 * @param gameViewModel The ViewModel that holds the game logic and state.
 */
@Composable
fun GameScreen(gameViewModel: GameViewModel) {
    val uiState by gameViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Display a general message to the user (e.g., instructions, game events, winner).
        Text(text = uiState.currentMessage, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))

        // Display scores for all players.
        uiState.players.forEach { player ->
            Text(
                text = "Joueur ${player.id}: ${player.totalScore} pts ${if (player.hasOpened) "(Ouvert)" else ""} ${if (player.isCurrentPlayer) "<--" else ""}",
                fontSize = 16.sp
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Display current player's turn information and actions if a game is in progress.
        if (uiState.currentPlayerId != null) {
            Text(text = "Tour du Joueur ${uiState.currentPlayerId}", fontSize = 16.sp)
            Text(text = "Score ce tour: ${uiState.currentTurnScore}", fontSize = 16.sp)
            Spacer(modifier = Modifier.height(16.dp))

            // Display the current dice roll.
            // TODO: Enhance dice display (e.g., actual dice images, selection visuals).
            Row {
                uiState.currentDiceRoll.forEachIndexed { index, dieValue ->
                    // Basic text display for dice.
                    // Needs to be enhanced for selection.
                    Text(
                        text = "[$dieValue]",
                        modifier = Modifier.padding(4.dp),
                        fontSize = 24.sp
                        // TODO: Add onClick to select/deselect dice using 'index'
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons for the current player.
            Button(
                onClick = { gameViewModel.rollDice() },
                enabled = uiState.isRollButtonEnabled
            ) {
                Text("Lancer les dés")
            }
            Spacer(modifier = Modifier.height(8.dp))

            // TODO: Implement a proper UI mechanism for selecting dice.
            // The current selectedDiceIndices in UiState should be populated by user interaction with dice.
            Button(
                onClick = {
                    // For now, this uses a pre-selected list from UiState.
                    // This list should be updated by tapping on the dice visuals.
                    gameViewModel.selectDice(uiState.selectedDiceIndices)
                },
                // Enable this button if dice are rolled and no score has been made this turn yet,
                // or if dice are selectable based on game rules.
                enabled = uiState.currentDiceRoll.isNotEmpty() // && uiState.currentTurnScore == 0 // Example condition
            ) {
                Text("Valider sélection") // Or "Garder les dés"
            }
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { gameViewModel.bankScore() },
                enabled = uiState.isBankButtonEnabled
            ) {
                Text("Banquer le score")
            }

        } else {
            // Display "New Game" button if no game is currently active (e.g., after a win or on first launch).
            // TODO: Allow selection of number of players.
            Button(onClick = { gameViewModel.startGame(1) }) {
                Text("Nouvelle Partie (1 Joueur)")
            }
        }
    }
}

/**
 * Preview Composable for the [GameScreen].
 * Initializes a [GameViewModel] with a default state for previewing purposes.
 * Note: Previews for Composables with ViewModels that use coroutines can sometimes be tricky
 * or require specific test dispatchers if the ViewModel launches coroutines in its init block.
 */
@Preview(showBackground = true)
@Composable
@Suppress("ViewModelConstructorInComposable") // Ajout de cette ligne pour supprimer l'avertissement
fun GameScreenPreview() {
    // For preview, we instantiate a GameManager and then the ViewModel.
    // In a real app, this ViewModel would typically be provided by `by viewModels()`.
    val previewGameManager = GameManager(DefaultDiceRoller())
    val previewViewModel = GameViewModel(previewGameManager) // L'avertissement est ici

    // Optionally, start a game to see a more complete UI state in the preview
    // previewViewModel.startGame(2) // Uncomment to see an active game state

    // Simulate a dice roll for preview if desired
    // previewViewModel.uiState.value = previewViewModel.uiState.value.copy(currentDiceRoll = listOf(1,2,3,4,5)) // Note: _uiState est private

    // Pour simuler un état, il faudrait soit rendre _uiState accessible (non recommandé)
    // soit avoir une méthode dans le ViewModel pour définir un état de test,
    // soit construire un GameUiState manuellement si GameScreen l'acceptait.

    // Exemple de simulation d'état si le ViewModel avait une méthode de "setup" pour le preview :
    // previewViewModel.setPreviewState(GameUiState(currentMessage = "Preview Mode", players = listOf(PlayerUiState(1, 1000, true, true))))


    fr.antoinehory.cinqmille.ui.theme.CinqMilleTheme { // Assuming this is your app's theme
        GameScreen(gameViewModel = previewViewModel)
    }
}
