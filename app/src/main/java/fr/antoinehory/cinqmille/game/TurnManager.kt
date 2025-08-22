package fr.antoinehory.cinqmille.game

class TurnManager(
    private val diceRoller: DiceRoller,
    private val gameRules: GameRules = GameRules(),
    private var canPlayerOpen: Boolean,
    private val initialDiceCount: Int = 5
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
        if (diceToRollIndices.isEmpty()) { // All dice were scored ("plein") or start of a segment
            currentSegmentScoredMask.fill(false) // Reset mask for a full new roll
            actualDiceToRollCount = initialDiceCount
            diceToRollIndices.clear()
            for (i in 0 until initialDiceCount) diceToRollIndices.add(i)
        } else {
            actualDiceToRollCount = diceToRollIndices.size
        }

        if (actualDiceToRollCount == 0 && currentSegmentScoredMask.all { it }) {
            val unScoredDice = currentDiceState.filterIndexed { index, _ -> !currentSegmentScoredMask[index] }
            val canMakeAnyScoreFromCurrentUnscored = ScoreCalculator.canScore(unScoredDice)
            return TurnEvent.Rolled(dice = currentDiceState.toList(), canPlayerMakeAnyScore = canMakeAnyScoreFromCurrentUnscored)
        }

        val newlyRolledValues = diceRoller.roll(actualDiceToRollCount)
        var newValueIdx = 0
        diceToRollIndices.forEach { indexToFill ->
            currentDiceState[indexToFill] = if (newValueIdx < newlyRolledValues.size) newlyRolledValues[newValueIdx++] else 0
        }
        hasRolledInSegment = true

        val diceForBustCheck = if (diceToRollIndices.size == initialDiceCount && currentSegmentScoredMask.none { it }) {
            currentDiceState.toList()
        } else {
            newlyRolledValues
        }

        if (diceForBustCheck.isNotEmpty() && !ScoreCalculator.canScore(diceForBustCheck)) {
            val diceAtBustEvent = currentDiceState.toList()
            resetTurnStateAfterBustOrBank() // Called on bust
            return TurnEvent.Busted(diceAtBust = diceAtBustEvent, finalTurnScore = 0)
        }

        val availableDiceForScoring = currentDiceState.filterIndexed { index, _ -> !currentSegmentScoredMask[index] }
        val canMakeAnyScoreFromCurrentState = ScoreCalculator.canScore(availableDiceForScoring)
        return TurnEvent.Rolled(dice = currentDiceState.toList(), canPlayerMakeAnyScore = canMakeAnyScoreFromCurrentState)
    }

    fun selectDice(selectedIndices: List<Int>, processRestOfTurnAutomatically: Boolean = true): TurnEvent {
        if (processRestOfTurnAutomatically && !hasRolledInSegment) {
            return TurnEvent.InvalidAction("Must roll dice before selecting for automatic processing.")
        }
        if (!processRestOfTurnAutomatically && !hasRolledInSegment && currentDiceState.all { it == 0 }) {
            return TurnEvent.InvalidAction("Must roll dice before selecting for banking.")
        }

        val distinctSelectedIndices = selectedIndices.distinct().sorted()

        if (distinctSelectedIndices.isEmpty()) {
            return TurnEvent.InvalidAction("No dice selected.")
        }
        if (distinctSelectedIndices.any { it < 0 || it >= initialDiceCount || currentSegmentScoredMask[it] }) {
            return TurnEvent.InvalidAction("Invalid dice selection (index out of bounds or already scored die selected).")
        }

        val diceSelectedValues = distinctSelectedIndices.map { currentDiceState[it] }
        val scoreFromSelection = ScoreCalculator.calculateScore(diceSelectedValues)

        if (scoreFromSelection == 0) { // Selection itself is a bust
            resetTurnStateAfterBustOrBank()
            return TurnEvent.Busted(diceAtBust = currentDiceState.toList(), finalTurnScore = 0)
        }

        // Tentatively apply selection score and mask
        var scoreToReportInEvent = scoreFromSelection
        currentTurnScore += scoreFromSelection
        distinctSelectedIndices.forEach { currentSegmentScoredMask[it] = true }

        if (!canPlayerOpen && currentTurnScore >= gameRules.openingScoreThreshold) {
            canPlayerOpen = true
        }

        if (processRestOfTurnAutomatically) {
            hasRolledInSegment = false // For the auto-roll segment

            val diceToRollForNextSegmentIndices = mutableListOf<Int>()
            for (i in 0 until initialDiceCount) {
                if (!currentSegmentScoredMask[i]) {
                    diceToRollForNextSegmentIndices.add(i)
                }
            }
            val wasPlein = diceToRollForNextSegmentIndices.isEmpty()
            val actualDiceToRollCountForNextSegment = if (wasPlein) initialDiceCount else diceToRollForNextSegmentIndices.size

            if (actualDiceToRollCountForNextSegment == 0 && !wasPlein) {
                hasRolledInSegment = true
                return TurnEvent.Scored(
                    newTurnTotalScore = scoreToReportInEvent, // Score from selection only
                    diceStateAfterAction = currentDiceState.toList(),
                    scoredDiceMask = currentSegmentScoredMask.toList(),
                    canRollAgain = false
                )
            }

            val newlyRolledValuesForNextSegment = diceRoller.roll(actualDiceToRollCountForNextSegment)
            var tempNewValueIdx = 0

            if (wasPlein) {
                currentSegmentScoredMask.fill(false)
                currentDiceState.indices.forEach { i ->
                    currentDiceState[i] = if (tempNewValueIdx < newlyRolledValuesForNextSegment.size) newlyRolledValuesForNextSegment[tempNewValueIdx++] else 0
                }
            } else {
                diceToRollForNextSegmentIndices.forEach { indexToFill ->
                    currentDiceState[indexToFill] = if (tempNewValueIdx < newlyRolledValuesForNextSegment.size) newlyRolledValuesForNextSegment[tempNewValueIdx++] else 0
                }
            }
            val diceStateAfterAutoRoll = currentDiceState.toList()
            val diceForBustCheckThisSegment = if (wasPlein) diceStateAfterAutoRoll else newlyRolledValuesForNextSegment

            if (diceForBustCheckThisSegment.isNotEmpty() && !ScoreCalculator.canScore(diceForBustCheckThisSegment)) {
                resetTurnStateAfterBustOrBank() // Full reset on auto-roll bust
                return TurnEvent.Busted(diceAtBust = diceStateAfterAutoRoll, finalTurnScore = 0)
            }

            hasRolledInSegment = true
            val scoreFromSuccessfulAutoRoll = ScoreCalculator.calculateScore(newlyRolledValuesForNextSegment)

            if (wasPlein) {
                // Event reports scoreFromSelection (the plein).
                // Internal currentTurnScore is reset for the new segment.
                if (scoreFromSuccessfulAutoRoll > 0) {
                    currentTurnScore = scoreFromSuccessfulAutoRoll
                } else {
                    currentTurnScore = 0 // If roll after plein is not scorable (but not a bust checked above), score for next segment is 0
                }
            } else {
                // Event reports scoreFromSelection.
                // Internal currentTurnScore accumulates auto-roll score.
                currentTurnScore += scoreFromSuccessfulAutoRoll
            }

            if (!canPlayerOpen && currentTurnScore >= gameRules.openingScoreThreshold) {
                canPlayerOpen = true
            }

            val availableDiceAfterAutoRoll = diceStateAfterAutoRoll.filterIndexed { index, _ -> !currentSegmentScoredMask[index] }
            val canMakeScoreFromAvailableAfterAutoRoll = ScoreCalculator.canScore(availableDiceAfterAutoRoll)
            val canEffectivelyRollAgain = gameRules.canRollAfterScoring(
                diceAvailableForNextRoll = availableDiceAfterAutoRoll,
                allDiceFromPreviousRollScored = wasPlein
            ) && canMakeScoreFromAvailableAfterAutoRoll

            return TurnEvent.Scored(
                newTurnTotalScore = scoreToReportInEvent, // Reports score from initial selection
                diceStateAfterAction = diceStateAfterAutoRoll,
                scoredDiceMask = currentSegmentScoredMask.toList(),
                canRollAgain = canEffectivelyRollAgain
            )

        } else { // Not processing automatically
            hasRolledInSegment = true
            val diceAvailableIfPlayerWereToRoll = currentDiceState.filterIndexed { index, _ -> !currentSegmentScoredMask[index] }
            val allDiceEffectivelyScoredInThisSegment = diceAvailableIfPlayerWereToRoll.isEmpty()
            val canMakeScoreIfPlayerWereToRoll = ScoreCalculator.canScore(diceAvailableIfPlayerWereToRoll)
            val couldRollAgain = gameRules.canRollAfterScoring(
                diceAvailableForNextRoll = diceAvailableIfPlayerWereToRoll,
                allDiceFromPreviousRollScored = allDiceEffectivelyScoredInThisSegment
            ) && canMakeScoreIfPlayerWereToRoll

            return TurnEvent.Scored(
                newTurnTotalScore = scoreToReportInEvent, // Reports score from this selection
                diceStateAfterAction = currentDiceState.toList(),
                scoredDiceMask = currentSegmentScoredMask.toList(),
                canRollAgain = couldRollAgain
            )
        }
    }

    fun bankScore(): TurnEvent {
        val finalScoreForBankEvent = currentTurnScore
        resetTurnStateAfterBustOrBank()
        return TurnEvent.TurnEndedBanked(finalTurnScore = finalScoreForBankEvent)
    }

    private fun resetTurnStateAfterBustOrBank() {
        currentTurnScore = 0
        currentDiceState.fill(0)
        currentSegmentScoredMask.fill(false)
        hasRolledInSegment = false
    }

    fun resetForNewTurn(hasPlayerOpened: Boolean) {
        resetTurnStateAfterBustOrBank()
        this.canPlayerOpen = hasPlayerOpened
    }
}
