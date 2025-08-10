package fr.antoinehory.cinqmille

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels // Import pour by viewModels()
import fr.antoinehory.cinqmille.ui.game.GameScreen
import fr.antoinehory.cinqmille.ui.game.GameViewModel
import fr.antoinehory.cinqmille.ui.theme.CinqMilleTheme // Assure-toi que le thème est correct

class MainActivity : ComponentActivity() {
    private val gameViewModel: GameViewModel by viewModels() // Instance du ViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CinqMilleTheme { // Ton thème Jetpack Compose
                GameScreen(gameViewModel = gameViewModel)
            }
        }
        // Démarrer une partie automatiquement pour tester (optionnel)
        if (savedInstanceState == null) { // Seulement si l'activité est créée pour la première fois
            gameViewModel.startGame(1) // Ou 2 joueurs, etc.
        }
    }
}