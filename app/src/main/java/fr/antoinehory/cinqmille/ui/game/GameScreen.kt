package fr.antoinehory.cinqmille.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
// import androidx.compose.ui.geometry.Size // Not directly used, Offset and CornerRadius are. Size is used by DrawScope.
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.antoinehory.cinqmille.game.GameManager
import fr.antoinehory.cinqmille.game.DefaultDiceRoller
import fr.antoinehory.cinqmille.ui.theme.CinqMilleTheme
// Ensure GameUiState and PlayerUiState are imported if they are in a separate file
// import fr.antoinehory.cinqmille.ui.game.GameUiState
// import fr.antoinehory.cinqmille.ui.game.PlayerUiState


/** Defines the corner size for UI elements to achieve a pixel art look. */
val PixelArtCornerSize = 0.dp
/** Shape used for neon-style buttons, based on [PixelArtCornerSize]. */
val NeonButtonShape = RoundedCornerShape(PixelArtCornerSize)
/** Shape used for neon-style frames, based on [PixelArtCornerSize]. */
val NeonFrameShape = RoundedCornerShape(PixelArtCornerSize)

/** Default size for a single die composable. */
val DieSize: Dp = 48.dp
/** Ratio of the die dot size relative to the die's overall size. */
val DieDotSizeRatio = 0.18f

// This constant is now defined in GameUiState.Companion as INITIAL_DICE_COUNT
// const val INITIAL_DICE_DISPLAY_COUNT = 5

/**
 * The main composable for the game screen.
 * It observes [GameUiState] from the [gameViewModel] and displays the game interface,
 * including player scores, current turn information, dice area, and action buttons.
 *
 * @param gameViewModel The ViewModel providing UI state and handling game actions.
 */
@Composable
fun GameScreen(gameViewModel: GameViewModel) {
    val uiState by gameViewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(8.dp)
    ) {
        PlayerScores(
            players = uiState.players,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = uiState.currentMessage,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
            )

            if (uiState.currentPlayerId != null) {
                CurrentTurnInfo(
                    currentPlayerId = uiState.currentPlayerId,
                    currentTurnScore = uiState.currentTurnScore,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                DiceArea(
                    diceRoll = uiState.currentDiceRoll,
                    selectedDiceVisual = uiState.selectedDiceVisual,
                    scoredDiceMask = uiState.scoredDiceMask, // This should resolve if GameUiState is correct
                    onDieClick = { index -> gameViewModel.toggleDieSelection(index) },
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ActionButtons(
                    onRollDice = { gameViewModel.rollDice() },
                    isRollEnabled = uiState.isRollButtonEnabled,
                    onBankScore = { gameViewModel.bankScore() },
                    isBankEnabled = uiState.isBankButtonEnabled
                )
            } else {
                NewGameOptions(
                    onStartGame = { numPlayers -> gameViewModel.startGame(numPlayers) },
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

/**
 * Displays the scores for all players in the game.
 * Highlights the current player.
 *
 * @param players The list of [PlayerUiState] to display.
 * @param modifier The [Modifier] to be applied to this composable.
 */
@Composable
fun PlayerScores(players: List<PlayerUiState>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .padding(8.dp)
            .border(
                BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary),
                shape = NeonFrameShape
            ),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        shape = NeonFrameShape
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "SCORES",
                style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.padding(bottom = 4.dp).align(Alignment.CenterHorizontally)
            )
            players.forEach { player ->
                Text(
                    text = "P${player.id}: ${player.totalScore} ${if (player.hasOpened) "[O]" else ""} ${if (player.isCurrentPlayer) "<=" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (player.isCurrentPlayer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Displays information about the current turn, including the current player's ID
 * and their accumulated score for this turn.
 *
 * @param currentPlayerId The ID of the player whose turn it is, or null if no turn is active.
 * @param currentTurnScore The score accumulated by the current player in this turn.
 * @param modifier The [Modifier] to be applied to this composable.
 */
@Composable
fun CurrentTurnInfo(currentPlayerId: Int?, currentTurnScore: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "JOUEUR ${currentPlayerId ?: "-"}",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "TOUR: $currentTurnScore",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Displays the area where dice are shown, either the current roll or placeholders.
 * Allows dice to be clicked to toggle their selection.
 *
 * @param diceRoll The list of face values for the currently rolled dice. Empty if no roll yet.
 * @param selectedDiceVisual A list indicating which dice are visually selected by the user.
 *                           Its size should correspond to the number of dice on display.
 * @param scoredDiceMask A list indicating which dice have already scored in the current turn segment
 *                       and cannot be selected again. Its size should correspond to the number of dice on display.
 * @param onDieClick Callback invoked when a die is clicked, passing its index.
 * @param modifier The [Modifier] to be applied to this composable.
 */
@Composable
fun DiceArea(
    diceRoll: List<Int>,
    selectedDiceVisual: List<Boolean>,
    scoredDiceMask: List<Boolean>,
    onDieClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(
                BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary),
                shape = NeonFrameShape
            )
            .padding(16.dp)
            .height(DieSize + 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (diceRoll.isEmpty()) {
                // Use GameUiState.INITIAL_DICE_COUNT for consistency
                repeat(GameUiState.INITIAL_DICE_COUNT) { index ->
                    SingleDie(
                        value = 0,
                        isSelected = selectedDiceVisual.getOrElse(index) { false },
                        isScored = scoredDiceMask.getOrElse(index) { false },
                        onClick = { onDieClick(index) }
                    )
                }
            } else {
                diceRoll.forEachIndexed { index, dieValue ->
                    val isSelected = selectedDiceVisual.getOrElse(index) { false }
                    val isScored = scoredDiceMask.getOrElse(index) { false }
                    SingleDie(
                        value = dieValue,
                        isSelected = isSelected,
                        isScored = isScored,
                        onClick = { onDieClick(index) }
                    )
                }
            }
        }
    }
}

/**
 * Displays a single die with its face value, selection state, and scored state.
 * Handles click events for selection.
 *
 * @param value The face value of the die (1-6). A value of 0 typically indicates a placeholder.
 * @param isSelected True if the die is currently visually selected by the user.
 * @param isScored True if the die has already been used to score points in the current turn segment.
 *                 Scored dice are typically not clickable and may appear differently.
 * @param onClick Callback invoked when the die is clicked.
 */
@Composable
fun SingleDie(
    value: Int,
    isSelected: Boolean,
    isScored: Boolean,
    onClick: () -> Unit
) {
    val isEffectivelySelected = isSelected && !isScored
    val isEffectivelyScored = isScored
    val isPlaceholder = value == 0

    val dieFaceColor = when {
        isEffectivelyScored -> MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
        isEffectivelySelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        isPlaceholder -> MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surface
    }

    val dieBorderColor = when {
        isEffectivelyScored -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        isEffectivelySelected -> MaterialTheme.colorScheme.primary
        isPlaceholder -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline
    }

    val dotColor = when {
        isEffectivelyScored -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        isEffectivelySelected -> MaterialTheme.colorScheme.primary
        isPlaceholder -> Color.Transparent
        else -> MaterialTheme.colorScheme.onSurface
    }

    val clickableEnabled = !isPlaceholder && !isEffectivelyScored

    Box(
        modifier = Modifier
            .size(DieSize)
            .clip(NeonFrameShape)
            .clickable(enabled = clickableEnabled) { onClick() }
            .border(BorderStroke(1.dp, dieBorderColor), shape = NeonFrameShape)
            .padding(1.dp)
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val cornerRadius = CornerRadius(PixelArtCornerSize.toPx(), PixelArtCornerSize.toPx())

            drawRoundRect(
                color = dieFaceColor,
                size = this.size, // this.size refers to DrawScope's size
                cornerRadius = cornerRadius
            )

            if (!isPlaceholder) {
                drawDieDots(value, dotColor)
            }
        }
    }
}

/**
 * Draws the dots on a die face within a [DrawScope].
 *
 * @param value The face value of the die (1-6) determining the dot pattern.
 * @param color The color of the dots.
 */
private fun DrawScope.drawDieDots(value: Int, color: Color) {
    val dieWidth = size.width
    val dieHeight = size.height
    val dotRadius = (minOf(dieWidth, dieHeight) * DieDotSizeRatio) / 2f

    val center = Pair(0.5f, 0.5f)
    val topLeft = Pair(0.25f, 0.25f)
    // val topCenter = Pair(0.5f, 0.25f) // Not used in typical 1-6 patterns
    val topRight = Pair(0.75f, 0.25f)
    val middleLeft = Pair(0.25f, 0.5f)
    val middleRight = Pair(0.75f, 0.5f)
    val bottomLeft = Pair(0.25f, 0.75f)
    // val bottomCenter = Pair(0.5f, 0.75f) // Not used
    val bottomRight = Pair(0.75f, 0.75f)

    val dotPositions = when (value) {
        1 -> listOf(center)
        2 -> listOf(topLeft, bottomRight)
        3 -> listOf(topLeft, center, bottomRight)
        4 -> listOf(topLeft, topRight, bottomLeft, bottomRight)
        5 -> listOf(topLeft, topRight, center, bottomLeft, bottomRight)
        6 -> listOf(topLeft, topRight, middleLeft, middleRight, bottomLeft, bottomRight)
        else -> emptyList()
    }

    dotPositions.forEach { pos ->
        drawCircle(
            color = color,
            radius = dotRadius,
            center = Offset(dieWidth * pos.first, dieHeight * pos.second)
        )
    }
}

/**
 * Displays the main action buttons for the game: "LANCER" (Roll) and "BANQUER" (Bank).
 *
 * @param onRollDice Callback invoked when the roll button is clicked.
 * @param isRollEnabled True if the roll button should be enabled, false otherwise.
 * @param onBankScore Callback invoked when the bank button is clicked.
 * @param isBankEnabled True if the bank button should be enabled, false otherwise.
 */
@Composable
fun ActionButtons(
    onRollDice: () -> Unit,
    isRollEnabled: Boolean,
    onBankScore: () -> Unit,
    isBankEnabled: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        NeonButton(
            text = "LANCER",
            onClick = onRollDice,
            enabled = isRollEnabled,
            modifier = Modifier.fillMaxWidth(0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        NeonButton(
            text = "BANQUER",
            onClick = onBankScore,
            enabled = isBankEnabled,
            modifier = Modifier.fillMaxWidth(0.6f)
        )
    }
}

/**
 * Displays options to start a new game, allowing selection of the number of players.
 *
 * @param onStartGame Callback invoked when a new game option is selected, passing the number of players.
 * @param modifier The [Modifier] to be applied to this composable.
 */
@Composable
fun NewGameOptions(onStartGame: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "NOUVELLE PARTIE",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton(text = "1 JOUEUR", onClick = { onStartGame(1) }, modifier = Modifier.width(120.dp))
            NeonButton(text = "2 JOUEURS", onClick = { onStartGame(2) }, modifier = Modifier.width(120.dp))
        }
    }
}

/**
 * A styled button with a "neon" look and feel, consistent with the game's theme.
 *
 * @param text The text to display on the button.
 * @param onClick Callback invoked when the button is clicked.
 * @param modifier The [Modifier] to be applied to this button.
 * @param enabled True if the button should be enabled and clickable, false otherwise.
 */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = NeonButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    ) {
        val textColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        Text(text, style = MaterialTheme.typography.labelLarge.copy(color = textColor))
    }
}

/**
 * Preview for the [GameScreen] using the RetroNeon theme.
 */
@Preview(showBackground = true, widthDp = 380, heightDp = 720)
@Suppress("ViewModelConstructorCall") // Suppress lint warning for direct ViewModel instantiation in Preview
@Composable
fun GameScreenPreview_RetroNeon() {
    CinqMilleTheme(darkTheme = true, dynamicColor = false) {
        val previewGameManager = GameManager(DefaultDiceRoller())
        // ViewModel is instantiated directly for preview purposes. This is acceptable.
        val previewViewModel = GameViewModel(previewGameManager)
        GameScreen(gameViewModel = previewViewModel)
    }
}
