package fr.antoinehory.cinqmille.game

import kotlin.random.Random

/**
 * A standard dice roller that generates random numbers for dice rolls in the game.
 * It uses [kotlin.random.Random] to produce pseudo-random outcomes for each die.
 */
class DefaultDiceRoller : DiceRoller {

    /**
     * Rolls a specified number of dice and returns their results.
     * Each die will have a value between 1 and 6, inclusive.
     *
     * @param numberOfDice The number of dice to roll. Must be a positive integer.
     *                     If zero or negative, an empty list is returned.
     * @return A list of integers representing the outcome of each die roll.
     *         Returns an empty list if [numberOfDice] is not positive.
     */
    override fun roll(numberOfDice: Int): DiceRoll {
        if (numberOfDice <= 0) return emptyList()
        return List(numberOfDice) { Random.nextInt(1, 7) } // Generates numbers from 1 to 6
    }
}