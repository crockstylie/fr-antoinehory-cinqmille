package fr.antoinehory.cinqmille.game

import kotlin.random.Random

/**
 * Represents the outcome of a dice roll.
 * It's a type alias for a list of integers, where each integer is the face value of a die.
 */
typealias DiceRoll = List<Int>

/**
 * Interface for a dice rolling mechanism.
 * This allows for different implementations, e.g., random or for testing.
 */
interface DiceRoller {
    /**
     * Simulates rolling a specified number of dice.
     *
     * @param numberOfDice The number of dice to roll. Must be positive.
     * @return A [DiceRoll] (List<Int>) representing the outcomes of the dice,
     *         each value between 1 and 6 (inclusive).
     *         Returns an empty list if numberOfDice is not positive.
     */
    fun roll(numberOfDice: Int): DiceRoll
}

/**
 * A standard dice roller that uses [kotlin.random.Random] to generate dice values.
 */
class RandomDiceRoller : DiceRoller {
    override fun roll(numberOfDice: Int): DiceRoll {
        if (numberOfDice <= 0) {
            return emptyList()
        }
        // Ensures values are between 1 and 6.
        return List(numberOfDice) { Random.nextInt(1, 7) }
    }
}

/**
 * Selects specific dice from a given roll based on their indices.
 *
 * @param currentRoll The [DiceRoll] from which to select dice.
 * @param indicesToKeep A list of 0-based indices representing the dice to keep.
 *                      Invalid indices (out of bounds) will be ignored.
 * @return A new [DiceRoll] containing only the dice at the specified valid indices.
 *         The order of dice in the returned list will match the order of valid indices provided.
 */
fun selectDiceFromRoll(currentRoll: DiceRoll, indicesToKeep: List<Int>): DiceRoll {
    if (currentRoll.isEmpty() || indicesToKeep.isEmpty()) {
        return emptyList()
    }
    // Filter valid indices and map them to dice values
    return indicesToKeep
        .filter { it >= 0 && it < currentRoll.size }
        .distinct() // Ensure each die is selected at most once if indices are duplicated
        .map { currentRoll[it] }
}

/**
 * Gets the dice that were not selected to be kept from a given roll.
 *
 * @param currentRoll The [DiceRoll] from which dice were selected.
 * @param indicesKept A list of 0-based indices representing the dice that were kept.
 * @return A new [DiceRoll] containing the dice that were not at the specified kept indices.
 *         The order of the remaining dice will be preserved from the original roll.
 */
fun getRemainingDice(currentRoll: DiceRoll, indicesKept: List<Int>): DiceRoll {
    if (currentRoll.isEmpty()) {
        return emptyList()
    }
    if (indicesKept.isEmpty()) {
        return currentRoll.toList() // Return a copy of the original roll if no dice were kept
    }

    val keptIndicesSet = indicesKept
        .filter { it >= 0 && it < currentRoll.size }
        .toSet() // Use a set for efficient lookup

    return currentRoll.filterIndexed { index, _ -> index !in keptIndicesSet }
}


// We can add more game turn related logic here later.
// For example:
// - Calculating score for the current turn segment (using ScoreCalculator and selectedDice)
// - Determining if a player can roll again (e.g., all dice scored, or player chooses to continue)
