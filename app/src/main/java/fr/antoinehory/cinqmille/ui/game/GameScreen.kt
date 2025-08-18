package fr.antoinehory.cinqmille.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Needed for Color.Transparent
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
            // TODO: Enhance dice display (e.g., actual dice images).
            Row {
                uiState.currentDiceRoll.forEachIndexed { index, dieValue ->
                    val isSelected = uiState.selectedDiceVisual.getOrElse(index) { false }
                    Text(
                        text = "[$dieValue]",
                        modifier = Modifier
                            .clickable { gameViewModel.toggleDieSelection(index) }
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .padding(8.dp),
                        fontSize = 24.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
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

            Button(
                onClick = { gameViewModel.selectDice() },
                enabled = uiState.currentDiceRoll.isNotEmpty() && uiState.selectedDiceVisual.any { it }
            ) {
                Text("Valider sélection")
            }
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { gameViewModel.bankScore() },
                enabled = uiState.isBankButtonEnabled
            ) {
                Text("Banquer le score")
            }

        } else {
            // Display "New Game" options if no game is currently active.
            Text("Choisissez le nombre de joueurs :", fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { gameViewModel.startGame(1) },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("1 Joueur")
                }
                Button(onClick = { gameViewModel.startGame(2) }) {
                    Text("2 Joueurs")
                }
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
@Suppress("ViewModelConstructorInComposable")
fun GameScreenPreview() {
    val previewGameManager = GameManager(DefaultDiceRoller())
    val previewViewModel = GameViewModel(previewGameManager)

    // Example: Simulate a dice roll and selection for preview
    // To make this work, GameViewModel would need a way to set a specific UiState for preview,
    // or you'd call its methods and rely on coroutine dispatchers if using viewModelScope.
    // For a simple preview, you might construct GameUiState directly if GameScreen accepted it.
    // previewViewModel.startGame(1) // Start a game
    // Manually setting uiState for preview is tricky as _uiState is private.
    // A better approach for complex previews is to have a @Preview GameScreen variant
    // that accepts GameUiState directly.
    //
    // val sampleUiState = GameUiState(
    //     currentPlayerId = 1,
    //     currentMessage = "Preview: Joueur 1, sélectionnez vos dés.",
    //     currentDiceRoll = listOf(1, 5, 3, 5, 2),
    //     selectedDiceVisual = listOf(false, true, false, true, false), // e.g. two 5s selected
    //     isRollButtonEnabled = true,
    //     players = listOf(PlayerUiState(1,0,false,true))
    // )

    fr.antoinehory.cinqmille.ui.theme.CinqMilleTheme {
        GameScreen(gameViewModel = previewViewModel)
        // GameScreen(uiState = sampleUiState, onAction = {}) // If GameScreen was refactored for preview
    }
}

