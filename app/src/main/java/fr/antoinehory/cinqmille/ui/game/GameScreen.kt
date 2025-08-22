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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.antoinehory.cinqmille.ui.theme.CinqMilleTheme

/** Defines the corner size for UI elements aiming for a pixel-art or sharp-edged neon look. */
val PixelArtCornerSize = 0.dp
/** A [RoundedCornerShape] using [PixelArtCornerSize] for buttons, giving a sharp-edged appearance. */
val NeonButtonShape = RoundedCornerShape(PixelArtCornerSize)
/** A [RoundedCornerShape] using [PixelArtCornerSize] for frames or borders, maintaining a sharp-edged style. */
val NeonFrameShape = RoundedCornerShape(PixelArtCornerSize)
/** The standard size (width and height) for a single die Composable. */
val DieSize: Dp = 48.dp
/** The ratio of the die's dot size relative to the die's overall size. Used for drawing dots on a die. */
val DieDotSizeRatio = 0.18f
/** The fixed height for the row containing action buttons like "Roll" and "Bank". */
val ActionButtonRowHeight: Dp = 80.dp

/**
 * The main screen for the Cinq Mille game.
 * It observes [GameUiState] from the [gameViewModel] and displays the game board,
 * player scores, dice, and action buttons.
 *
 * @param gameViewModel The [GameViewModel] providing the [GameUiState] and handling game actions.
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
                    accumulatedTurnScore = uiState.accumulatedTurnScore,
                    previewSelectionScore = uiState.previewSelectionScore,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                DiceArea(
                    diceRoll = uiState.currentDiceRoll,
                    selectedDiceVisual = uiState.selectedDiceVisual,
                    scoredDiceMask = uiState.scoredDiceMask,
                    onDieClick = { index -> gameViewModel.toggleDieSelection(index) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                ActionButtons(
                    onRollDice = { gameViewModel.rollDice() },
                    isRollEnabled = uiState.isRollButtonEnabled,
                    onBankScore = { gameViewModel.bankScore() },
                    isBankEnabled = uiState.isBankButtonEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ActionButtonRowHeight)
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
 * Displays the scores of all players in a styled surface.
 *
 * @param players A list of [PlayerUiState] objects representing each player's status.
 * @param modifier The [Modifier] for this composable.
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
 * Displays information about the current turn.
 * @param currentPlayerId The ID of the current player, or null.
 * @param accumulatedTurnScore The score already banked or validated in the current turn.
 * @param previewSelectionScore The potential score from the dice currently selected.
 * @param modifier The [Modifier] for this composable.
 */
@Composable
fun CurrentTurnInfo(
    currentPlayerId: Int?,
    accumulatedTurnScore: Int,
    previewSelectionScore: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "JOUEUR ${currentPlayerId ?: "-"}",
            style = MaterialTheme.typography.titleLarge
        )
        val scoreText = if (previewSelectionScore > 0) {
            "$accumulatedTurnScore + $previewSelectionScore"
        } else {
            accumulatedTurnScore.toString()
        }
        Text(
            text = "TOUR: $scoreText",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Displays the area where dice are shown, allowing interaction.
 *
 * @param diceRoll The current list of dice values (0 for placeholder).
 * @param selectedDiceVisual A list indicating which dice are visually selected.
 * @param scoredDiceMask A list indicating which dice have been scored in the current segment.
 * @param onDieClick Lambda called when a die is clicked, passing its index.
 * @param modifier The [Modifier] for this composable.
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
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayCount = GameUiState.INITIAL_DICE_COUNT
            if (diceRoll.isEmpty()) {
                repeat(displayCount) { index ->
                    SingleDie(
                        value = 0,
                        isSelected = selectedDiceVisual.getOrElse(index) { false },
                        isScored = scoredDiceMask.getOrElse(index) { false },
                        onClick = { onDieClick(index) }
                    )
                }
            } else {
                val currentDiceToDisplay = diceRoll.take(displayCount)
                currentDiceToDisplay.forEachIndexed { index, dieValue ->
                    SingleDie(
                        value = dieValue,
                        isSelected = selectedDiceVisual.getOrElse(index) { false },
                        isScored = scoredDiceMask.getOrElse(index) { false },
                        onClick = { onDieClick(index) }
                    )
                }
                if (currentDiceToDisplay.size < displayCount) {
                    repeat(displayCount - currentDiceToDisplay.size) { idxOffset ->
                        val actualIndex = currentDiceToDisplay.size + idxOffset
                        SingleDie(
                            value = 0,
                            isSelected = selectedDiceVisual.getOrElse(actualIndex) { false },
                            isScored = scoredDiceMask.getOrElse(actualIndex) { false },
                            onClick = { onDieClick(actualIndex) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Displays action buttons for the game, such as "Roll" and "Bank".
 *
 * @param onRollDice Lambda called when the roll button is clicked.
 * @param isRollEnabled Boolean indicating if the roll button should be enabled.
 * @param onBankScore Lambda called when the bank button is clicked.
 * @param isBankEnabled Boolean indicating if the bank button should be enabled.
 * @param modifier The [Modifier] for this composable.
 */
@Composable
fun ActionButtons(
    onRollDice: () -> Unit,
    isRollEnabled: Boolean,
    onBankScore: () -> Unit,
    isBankEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val buttonBorderThickness = 2.dp
    val disabledAlpha = 0.4f

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onRollDice,
            enabled = isRollEnabled,
            shape = NeonButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = disabledAlpha)
            ),
            border = BorderStroke(
                buttonBorderThickness,
                if (isRollEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = disabledAlpha)
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Text("Lancer")
        }

        Button(
            onClick = onBankScore,
            enabled = isBankEnabled,
            shape = NeonButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.secondary,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.secondary.copy(alpha = disabledAlpha)
            ),
            border = BorderStroke(
                buttonBorderThickness,
                if (isBankEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondary.copy(alpha = disabledAlpha)
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Text("Banquer")
        }
    }
}

/**
 * Displays a single die with its current value and state (selected, scored).
 *
 * @param value The face value of the die (1-6, or 0 for placeholder).
 * @param isSelected Boolean indicating if the die is currently selected by the player.
 * @param isScored Boolean indicating if the die has been scored in the current turn segment.
 * @param onClick Lambda called when the die is clicked.
 */
@Composable
fun SingleDie(
    value: Int,
    isSelected: Boolean,
    isScored: Boolean,
    onClick: () -> Unit
) {
    val isEffectivelySelected = isSelected && !isScored
    val isPlaceholder = value == 0

    val dieFaceColor = when {
        isScored -> MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
        isEffectivelySelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        isPlaceholder -> MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surface
    }
    val dieBorderColor = when {
        isScored -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        isEffectivelySelected -> MaterialTheme.colorScheme.primary
        isPlaceholder -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline
    }
    val dotColor = when {
        isScored -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        isEffectivelySelected -> MaterialTheme.colorScheme.primary
        isPlaceholder -> Color.Transparent
        else -> MaterialTheme.colorScheme.onSurface
    }
    val clickableEnabled = !isPlaceholder && !isScored

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
                size = this.size,
                cornerRadius = cornerRadius
            )
            if (!isPlaceholder) {
                drawDieDots(value, dotColor, DieDotSizeRatio)
            }
        }
    }
}

/**
 * A [DrawScope] extension function to draw the dots for a given die value.
 *
 * @param value The face value of the die (1-6).
 * @param dotColor The [Color] to use for the dots.
 * @param dotSizeRatio The ratio of the dot's diameter to the smaller dimension of the die face.
 */
fun DrawScope.drawDieDots(value: Int, dotColor: Color, dotSizeRatio: Float) {
    val dieWidth = size.width
    val dieHeight = size.height
    val dotRadius = (minOf(dieWidth, dieHeight) * dotSizeRatio) / 2f

    val center = Offset(dieWidth / 2, dieHeight / 2)
    val left = dieWidth / 4
    val top = dieHeight / 4
    val right = dieWidth * 3 / 4
    val bottom = dieHeight * 3 / 4

    val positions = when (value) {
        1 -> listOf(center)
        2 -> listOf(Offset(left, top), Offset(right, bottom))
        3 -> listOf(Offset(left, top), center, Offset(right, bottom))
        4 -> listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom))
        5 -> listOf(Offset(left, top), Offset(right, top), center, Offset(left, bottom), Offset(right, bottom))
        6 -> listOf(Offset(left, top), Offset(right, top), Offset(left, center.y), Offset(right, center.y), Offset(left, bottom), Offset(right, bottom))
        else -> emptyList() // Should not happen for valid die values 1-6
    }
    positions.forEach { pos ->
        drawCircle(color = dotColor, radius = dotRadius, center = pos)
    }
}

/**
 * Displays options for starting a new game, allowing selection of the number of players.
 *
 * @param onStartGame Lambda called when a new game option is selected, passing the number of players.
 * @param modifier The [Modifier] for this composable.
 */
@Composable
fun NewGameOptions(onStartGame: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Nouvelle Partie", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { onStartGame(1) }, shape = NeonButtonShape) { Text("1 Joueur") }
        Button(onClick = { onStartGame(2) }, shape = NeonButtonShape) { Text("2 Joueurs") }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 720)
@Composable
fun GameScreenPreview_NewGame() {
    CinqMilleTheme {
        val viewModel = GameViewModel() // Uses default constructor
        GameScreen(viewModel)
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 720)
@Composable
fun GameScreenPreview_GameInProgress() {
    CinqMilleTheme {
        val viewModel = GameViewModel() // Uses default constructor
        // Simulate an ongoing game state for the preview.
        // This might require exposing methods on the ViewModel to populate it
        // or having a constructor that takes an initial GameUiState.
        // For now, we can just start a simple game.
        viewModel.startGame(2) // Start a simple 2-player game
        // For a richer preview, a mechanism to set a specific UiState would be needed.
        GameScreen(viewModel)
    }
}

@Preview
@Composable
fun SingleDiePreview() {
    CinqMilleTheme {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SingleDie(value = 1, isSelected = false, isScored = false, onClick = {})
                SingleDie(value = 2, isSelected = true, isScored = false, onClick = {})
                SingleDie(value = 3, isSelected = false, isScored = true, onClick = {})
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SingleDie(value = 4, isSelected = false, isScored = false, onClick = {})
                SingleDie(value = 5, isSelected = true, isScored = true, onClick = {}) // Unrealistic case but okay for preview
                SingleDie(value = 6, isSelected = false, isScored = false, onClick = {})
            }
            Spacer(Modifier.height(4.dp))
            SingleDie(value = 0, isSelected = false, isScored = false, onClick = {}) // Placeholder
        }
    }
}
