# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- (Future features will be listed here)

### Changed
- (Future changes will be listed here)

### Fixed
- (Future bug fixes will be listed here)

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

