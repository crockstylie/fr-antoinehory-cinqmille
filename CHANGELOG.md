# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Documentation KDoc pour `GameViewModel`, `GameScreen`, `Player`, `GameManager`, `MainActivity`, et les Composables de `GameScreen.kt`.
- Tests unitaires pour `GameViewModel` (`GameViewModelTest.kt`) couvrant l'état initial, `startGame`, et les scénarios de `rollDice` (scorable, bust) avec `FakeDiceRoller`.
- Tests unitaires pour la data class `Player` (`PlayerTest.kt`).
- Test unitaire `rollDice_afterBust_returnsInvalidAction` à `TurnManagerTest.kt`.
- Dépendances de test `kotlinx-coroutines-test`, `turbine` et `lifecycle-viewmodel-compose` au `build.gradle.kts`.
- Fonction `@Preview` pour `GameScreen.kt`.
- `GameViewModelFactory` dans `GameViewModel.kt`.
- Champ `scoredDiceMask` et constante `INITIAL_DICE_COUNT` à `GameUiState`.
- Fichier `PlayerUiState.kt` pour la data class `PlayerUiState` (précédemment imbriquée ou implicite).
- Fichier `GameUiState.kt` pour la data class `GameUiState` (précédemment imbriquée ou implicite).
- Champ `previewSelectionScore` à `GameUiState` pour afficher le score de la sélection de dés en cours.
- Paramètre `processRestOfTurnAutomatically` à `TurnManager.selectDice` pour distinguer la validation d'une sélection pour banque versus pour continuer le tour.

### Changed
- Refactorisation de `GameViewModel` pour permettre l'injection de `GameManager` (amélioration de la testabilité).
- La classe `Player` a été déplacée de `GameManager.kt` vers son propre fichier `Player.kt`.
- `GameManager.kt` utilise maintenant la classe `Player` externe.
- La gestion de l'état du joueur actuel est centralisée (suppression de `isCurrentPlayer` de la classe `Player`).
- Revue et confirmation de la KDoc et des tests pour `ScoreCalculator.kt` et `TurnManager.kt`.
- `GameViewModelTest.kt` utilise `TestDispatcher` et `advanceUntilIdle()` pour une meilleure gestion des coroutines.
- Simplification de la logique de `prepareTurnForCurrentPlayer` dans `GameManager.kt`.
- Modification de la signature de `getNextPlayerId` dans `GameViewModel.kt` pour passer `playersList` en paramètre.
- Utilisation de `GameUiState.INITIAL_DICE_COUNT` dans `GameScreen.kt` pour la cohérence.
- Amélioration de la KDoc pour les fichiers de thème (`Color.kt`, `Theme.kt`, `Type.kt`).
- `GameUiState.currentTurnScore` renommé en `accumulatedTurnScore` pour plus de clarté.
- `GameScreen.kt` affiche désormais `accumulatedTurnScore + previewSelectionScore` pour le score du tour.
- `GameViewModel.bankScore` transmet les dés sélectionnés au `GameManager`.
- `GameManager.currentTurnBankScore` traite les dés sélectionnés avant de banquer, utilisant la nouvelle logique de `TurnManager.selectDice`.
- Logique d'activation du bouton "Banquer" dans `GameViewModel.toggleDieSelection` affinée pour considérer le score d'ouverture et le score potentiel du tour.

### Fixed
- Correction d'une expression `when` non exhaustive dans `GameViewModel` pour gérer `GameEvent.PlayerTurnStarted`.
- L'avertissement `ViewModelConstructorInComposable` dans la preview de `GameScreen.kt` est supprimé avec `@Suppress("ViewModelConstructorCall")`.
- Correction des erreurs de compilation liées aux références non résolues (`scoredDiceMask`, `INITIAL_DICE_COUNT`) dans `GameViewModel.kt` et `GameScreen.kt` suite à la mise à jour de `GameUiState`.
- Le bouton "Banquer" s'active/se désactive correctement en fonction du score d'ouverture et du score de la sélection en cours.
- Banquer une sélection avant l'ouverture avec un score insuffisant ne résulte plus en un "bust" incorrect mais en "PlayerFailedToOpen" ou une action invalide.
- Clarification de la KDoc de `GameViewModel`.
- `GameManager` et `TurnManager` gèrent plus finement les busts lors des sélections et des lancers.

### Removed
- Propriété `isCurrentPlayer` de la data class `Player`.
- Définition imbriquée de la classe `Player` depuis `GameManager.kt`.
- Couleurs `LofiCyber...` inutilisées et `NeonLuminousFrame` de `Color.kt`.
- `LofiCyberColorScheme` (commenté) de `Theme.kt`.
- Constante locale `INITIAL_DICE_DISPLAY_COUNT` de `GameScreen.kt` (remplacée par `GameUiState.INITIAL_DICE_COUNT`).

## [0.2.0] - 2025-08-10

### Added
- `DiceRoller` interface and `RandomDiceRoller` implementation to abstract dice rolling logic.
- `FakeDiceRoller` in `TurnManagerTest.kt` for deterministic dice roll control in tests.
- Comprehensive unit tests for `TurnManager` covering various game scenarios (roll, score, bust, bank, re-roll all dice).
- Unit tests for `selectDiceFromRoll` and `getRemainingDice` in `GameTurnTest.kt`.

### Changed
- Refactored `TurnManager` to use `DiceRoller` via dependency injection, improving testability.
- Made all tests in `TurnManagerTest.kt` deterministic.
- Cleaned up `GameTurnTest.kt` by removing obsolete tests related to the old global `rollDice` function.

### Fixed
- Ensured `TurnManager` correctly handles scenarios like busting after a partial score, and correctly resets dice count when all dice score.

### Removed
- Global `rollDice()` function from `GameTurn.kt` (logic moved to `RandomDiceRoller`).

## [0.1.0] - 2025-08-10 

### Added
- Initial project setup with Android Studio.
- Implemented `ScoreCalculator.kt` for calculating dice scores based on Cinq Mille rules (singles, brelans, fulls, straights, five-of-a-kind with priorities).
- Developed a comprehensive suite of unit tests in `ScoreCalculatorTest.kt` to validate the scoring logic.
- Created `README.md` with project description, features, setup instructions, and game rules.
- Added `LICENSE` file (MIT License).

