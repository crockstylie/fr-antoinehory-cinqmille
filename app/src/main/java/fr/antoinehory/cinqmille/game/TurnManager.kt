package fr.antoinehory.cinqmille.game

// Assurez-vous que les imports pour ScoreCalculator, DiceRoller, GameRules sont corrects.

/**
 * Manages the state and logic for a single player's turn in the Cinq Mille game.
 * It handles dice rolling, selection, scoring, and determines when a turn ends
 * due to busting, banking, or achieving a winning condition.
 *
 * @property diceRoller The [DiceRoller] instance used to simulate dice rolls.
 * @property gameRules The [GameRules] defining scoring and game progression logic.
 * @property canPlayerOpen A flag indicating if the player has met the opening score requirement.
 * @property initialDiceCount The number of dice to roll at the start of a turn or when all dice have scored.
 */
class TurnManager(
    private val diceRoller: DiceRoller,
    private val gameRules: GameRules = GameRules(),
    private var canPlayerOpen: Boolean,
    private val initialDiceCount: Int = 5 // Typiquement 5 ou 6
) {
    private var currentTurnScore: Int = 0
    private var currentDiceState: MutableList<Int> = MutableList(initialDiceCount) { 0 }
    private var currentSegmentScoredMask: MutableList<Boolean> = MutableList(initialDiceCount) { false }
    private var hasRolledInSegment: Boolean = false


    fun rollDice(): TurnEvent {
        val diceToRollIndices = mutableListOf<Int>()
        for (i in 0 until initialDiceCount) {
            if (!currentSegmentScoredMask[i]) {
                diceToRollIndices.add(i)
            }
        }

        val actualDiceToRollCount: Int
        if (diceToRollIndices.isEmpty()) {
            currentSegmentScoredMask.fill(false)
            actualDiceToRollCount = initialDiceCount
            for (i in 0 until initialDiceCount) diceToRollIndices.add(i)
        } else {
            actualDiceToRollCount = diceToRollIndices.size
        }

        if (actualDiceToRollCount == 0) {
            val canMakeAnyScoreFromCurrentKept = ScoreCalculator.canScore(currentDiceState.filterIndexed { index, _ -> currentSegmentScoredMask[index] })
            return TurnEvent.Rolled(dice = currentDiceState.toList(), canPlayerMakeAnyScore = canMakeAnyScoreFromCurrentKept)
        }

        val newlyRolledValues = diceRoller.roll(actualDiceToRollCount)

        var newValueIdx = 0
        diceToRollIndices.forEach { indexToFill ->
            currentDiceState[indexToFill] = if (newValueIdx < newlyRolledValues.size) newlyRolledValues[newValueIdx++] else 0
        }
        hasRolledInSegment = true

        val diceForBustCheck = if (diceToRollIndices.isEmpty()) { // Case where all dice were re-rolled (plein)
            currentDiceState.toList()
        } else { // Case where only remaining dice were rolled
            newlyRolledValues // Only check newly rolled dice for bust, as others are already "kept"
        }

        if (diceForBustCheck.isNotEmpty() && !ScoreCalculator.canScore(diceForBustCheck)) {
            val diceAtBustEvent = currentDiceState.toList() // Show all dice at bust
            // Do NOT resetTurnStateAfterBustOrBank() here. GameManager decides based on context.
            // If this bust is from a roll *after* a score, the accumulated score might still be bankable by game rules.
            // For now, this Busted event implies the current *roll segment* is bust.
            return TurnEvent.Busted(diceAtBust = diceAtBustEvent)
        }

        // Determine if any score can be made from the dice that are not yet part of the currentSegmentScoredMask
        val availableDiceForScoring = currentDiceState.filterIndexed { index, _ -> !currentSegmentScoredMask[index] }
        val canMakeAnyScoreFromCurrentState = ScoreCalculator.canScore(availableDiceForScoring)

        return TurnEvent.Rolled(dice = currentDiceState.toList(), canPlayerMakeAnyScore = canMakeAnyScoreFromCurrentState)
    }

    // MODIFIED: Added processRestOfTurnAutomatically parameter
    fun selectDice(selectedIndices: List<Int>, processRestOfTurnAutomatically: Boolean = true): TurnEvent {
        // Allow selectDice if !hasRolledInSegment AND !processRestOfTurnAutomatically.
        // This covers the case where player rolls, gets a result, and immediately wants to bank that specific selection.
        // The check for hasRolledInSegment is more critical when processRestOfTurnAutomatically is true,
        // to ensure there's a valid dice state to auto-process from.
        if (!hasRolledInSegment && processRestOfTurnAutomatically) {
            return TurnEvent.InvalidAction("Must roll dice before selecting for automatic processing.")
        }
        // If !processRestOfTurnAutomatically, it's assumed currentDiceState is the one to select from,
        // even if hasRolledInSegment is false (e.g. first roll, then bank selection).
        // However, an empty/invalid currentDiceState would still be an issue.
        // A robust check might ensure currentDiceState contains a valid roll if !hasRolledInSegment.
        // For now, let's assume currentDiceState is valid for selection if hasRolledInSegment is false
        // and processRestOfTurnAutomatically is false.

        val distinctSelectedIndices = selectedIndices.distinct().sorted()

        if (distinctSelectedIndices.isEmpty()) {
            return TurnEvent.InvalidAction("No dice selected.")
        }
        if (distinctSelectedIndices.any { it < 0 || it >= initialDiceCount || currentSegmentScoredMask[it] }) {
            return TurnEvent.InvalidAction("Invalid dice selection (index out of bounds or already scored die selected).")
        }

        val diceSelectedValues = distinctSelectedIndices.map { currentDiceState[it] }
        val scoreFromSelection = ScoreCalculator.calculateScore(diceSelectedValues)

        if (scoreFromSelection == 0) {
            // Player selected dice that do not score.
            // If processRestOfTurnAutomatically is false (i.e. banking this selection),
            // and this selection itself scores 0, it's an invalid selection to bank.
            // If processRestOfTurnAutomatically is true, this non-scoring selection leads to a bust for the turn.
            // Let's refine this: a non-scoring selection is generally a bust.
            val diceToShowAtBust = currentDiceState.toList()
            // Do NOT resetTurnStateAfterBustOrBank() here.
            // GameManager will handle the consequence of Busted (e.g. if it's during a bank attempt)
            return TurnEvent.Busted(diceAtBust = diceToShowAtBust)
        }

        currentTurnScore += scoreFromSelection
        distinctSelectedIndices.forEach { currentSegmentScoredMask[it] = true }

        if (!canPlayerOpen && currentTurnScore >= gameRules.openingScoreThreshold) {
            canPlayerOpen = true
        }

        if (processRestOfTurnAutomatically) {
            hasRolledInSegment = false // Reset for auto-roll

            val diceToRollForNextSegmentIndices = mutableListOf<Int>()
            for (i in 0 until initialDiceCount) {
                if (!currentSegmentScoredMask[i]) {
                    diceToRollForNextSegmentIndices.add(i)
                }
            }
            val wasPlein = diceToRollForNextSegmentIndices.isEmpty()
            val actualDiceToRollCountForNextSegment = if (wasPlein) initialDiceCount else diceToRollForNextSegmentIndices.size

            if (actualDiceToRollCountForNextSegment == 0 && !wasPlein) {
                hasRolledInSegment = true // No new roll, state is final for this segment
                return TurnEvent.Scored(
                    newTurnTotalScore = currentTurnScore,
                    diceStateAfterAction = currentDiceState.toList(),
                    scoredDiceMask = currentSegmentScoredMask.toList(),
                    canRollAgain = false
                )
            }

            val newlyRolledValuesForNextSegment = diceRoller.roll(actualDiceToRollCountForNextSegment)
            var newValueIdx = 0
            if (wasPlein) {
                currentDiceState.indices.forEach { i ->
                    currentDiceState[i] = if (newValueIdx < newlyRolledValuesForNextSegment.size) newlyRolledValuesForNextSegment[newValueIdx++] else 0
                }
                currentSegmentScoredMask.fill(false)
            } else {
                diceToRollForNextSegmentIndices.forEach { indexToFill ->
                    currentDiceState[indexToFill] = if (newValueIdx < newlyRolledValuesForNextSegment.size) newlyRolledValuesForNextSegment[newValueIdx++] else 0
                }
            }

            val diceForEvent = currentDiceState.toList()
            // Bust check for the *newly rolled dice* in the auto-roll segment
            val diceForBustCheckThisSegment = if (wasPlein) currentDiceState.toList() else newlyRolledValuesForNextSegment

            if (diceForBustCheckThisSegment.isNotEmpty() && !ScoreCalculator.canScore(diceForBustCheckThisSegment)) {
                // If it was a "plein" and the new roll busts, the player keeps the score from the "plein".
                // The turn effectively ends there, score is kept, but cannot roll again.
                if (wasPlein) {
                    hasRolledInSegment = true // State is final from this auto-roll.
                    return TurnEvent.Scored(
                        newTurnTotalScore = currentTurnScore, // Score from plein is kept
                        diceStateAfterAction = diceForEvent,
                        scoredDiceMask = currentSegmentScoredMask.toList(), // Mask reset due to plein
                        canRollAgain = false // Bust on the roll after plein
                    )
                } else {
                    // Bust on remaining dice roll, turn score is lost.
                    // resetTurnStateAfterBustOrBank() // GameManager handles this consequence.
                    return TurnEvent.Busted(diceAtBust = diceForEvent)
                }
            }

            // Determine if player can roll again based on remaining non-kept dice
            val nonKeptDiceAfterAutoRoll = if (wasPlein) {
                currentDiceState.toList() // All dice are new
            } else {
                currentDiceState.filterIndexed { index, _ -> !currentSegmentScoredMask[index] }
            }
            val canMakeScoreFromNonKeptOrNewSet = ScoreCalculator.canScore(nonKeptDiceAfterAutoRoll)

            // GameRules might have specific conditions for canRollAgain (e.g., must clear all dice)
            val canEffectivelyRollAgain = gameRules.canRollAfterScoring(
                diceAvailableForNextRoll = nonKeptDiceAfterAutoRoll,
                allDiceFromPreviousRollScored = wasPlein // True if it was a plein leading to this auto-roll
            ) && canMakeScoreFromNonKeptOrNewSet

            hasRolledInSegment = true
            return TurnEvent.Scored(
                newTurnTotalScore = currentTurnScore,
                diceStateAfterAction = currentDiceState.toList(),
                scoredDiceMask = currentSegmentScoredMask.toList(),
                canRollAgain = canEffectivelyRollAgain
            )
        } else {
            // ADDED: Logic for when processRestOfTurnAutomatically is false (typically for banking a selection)
            // The score is already added, mask is updated. No further automatic roll.
            // Player's intention is to bank this score.
            hasRolledInSegment = true // The segment of selecting dice is done.

            // Determine if player *could* roll again based on remaining dice, even if they choose to bank.
            // This might inform UI, but for banking, canRollAgain is effectively false.
            val diceAvailableIfPlayerWereToRoll = currentDiceState.filterIndexed { index, _ -> !currentSegmentScoredMask[index] }
            val allDiceScoredInThisSegment = diceAvailableIfPlayerWereToRoll.isEmpty()
            val canMakeScoreIfPlayerWereToRoll = ScoreCalculator.canScore(diceAvailableIfPlayerWereToRoll)

            val couldRollAgain = gameRules.canRollAfterScoring(
                diceAvailableForNextRoll = diceAvailableIfPlayerWereToRoll,
                allDiceFromPreviousRollScored = allDiceScoredInThisSegment
            ) && canMakeScoreIfPlayerWereToRoll


            return TurnEvent.Scored(
                newTurnTotalScore = currentTurnScore,
                diceStateAfterAction = currentDiceState.toList(),
                scoredDiceMask = currentSegmentScoredMask.toList(),
                // When banking a selection, canRollAgain is conceptually false because the next action is bank.
                // However, 'couldRollAgain' might be true if they *weren't* banking.
                // For simplicity, let's reflect the state as if the turn *could* continue if not for banking.
                // The GameManager will override this with a TurnEndedBanked event.
                canRollAgain = couldRollAgain
            )
        }
    }

    /**
     * Finalizes the turn by banking the current score.
     * This is called when the player chooses to stop rolling and keep their accumulated score.
     *
     * @return A [TurnEvent.TurnEndedBanked] with the final score for the turn,
     *         or [TurnEvent.InvalidAction] if trying to bank a zero score without being open.
     */
    fun bankScore(): TurnEvent {
        // If player hasn't opened and currentTurnScore is less than opening threshold,
        // they can't bank this score to open.
        // However, if they have opened, they can bank any positive score.
        // Or if they haven't opened, but currentTurnScore >= threshold, they can bank.
        if (!canPlayerOpen && currentTurnScore < gameRules.openingScoreThreshold) {
            // Not resetting score here, as they might try another action or bust.
            // The GameManager will interpret this. If this bank attempt fails,
            // currentTurnScore remains for potential bust or further rolls.
            // For PlayerFailedToOpen, the GameManager will need the score that was attempted.
            // So, let TurnEndedBanked carry this score; GM decides if it's 0 for player's total.
            val scoreAttempted = currentTurnScore
            resetTurnStateAfterBustOrBank() // Reset for next player's turn
            return TurnEvent.TurnEndedBanked(finalTurnScore = scoreAttempted) // Or 0 if rules dictate score is lost
        }

        if (currentTurnScore == 0 && canPlayerOpen) { // Cannot bank 0 if already open
            // No score change, but turn ends.
            resetTurnStateAfterBustOrBank()
            return TurnEvent.TurnEndedBanked(finalTurnScore = 0)
        }
        if (currentTurnScore == 0 && !canPlayerOpen) { // Cannot bank 0 to open
            resetTurnStateAfterBustOrBank()
            return TurnEvent.TurnEndedBanked(finalTurnScore = 0)
        }


        val finalScoreForTurn = currentTurnScore
        resetTurnStateAfterBustOrBank()
        return TurnEvent.TurnEndedBanked(finalTurnScore = finalScoreForTurn)
    }

    /**
     * Resets the turn state, typically after a bust or when a player banks their score.
     * Prepares the manager for a completely new turn (potentially for another player or a fresh start).
     */
    private fun resetTurnStateAfterBustOrBank() {
        currentTurnScore = 0
        currentDiceState.fill(0)
        currentSegmentScoredMask.fill(false)
        hasRolledInSegment = false
        // canPlayerOpen is NOT reset here; it's a player state, not just turn state.
    }

    /**
     * Resets the [TurnManager] for a new player's turn, preserving their 'hasOpened' status.
     * @param hasPlayerOpened The 'hasOpened' status of the player whose turn it is.
     */
    fun resetForNewTurn(hasPlayerOpened: Boolean) {
        resetTurnStateAfterBustOrBank() // Resets score, dice, etc.
        this.canPlayerOpen = hasPlayerOpened // Sets the opening status for the new turn
    }
}