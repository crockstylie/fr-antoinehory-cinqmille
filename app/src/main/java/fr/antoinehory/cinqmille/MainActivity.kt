package fr.antoinehory.cinqmille

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import fr.antoinehory.cinqmille.ui.game.GameScreen
import fr.antoinehory.cinqmille.ui.game.GameViewModel
import fr.antoinehory.cinqmille.ui.theme.CinqMilleTheme

/**
 * The main entry point of the Cinq Mille application.
 * This activity hosts the Jetpack Compose UI, primarily the [GameScreen].
 */
class MainActivity : ComponentActivity() {
    /** The ViewModel responsible for the game logic and UI state, scoped to this Activity. */
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CinqMilleTheme {
                GameScreen(gameViewModel = gameViewModel)
            }
        }

    }
}
