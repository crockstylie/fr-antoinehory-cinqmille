package fr.antoinehory.cinqmille.game

/**
 * Defines the contract for a dice rolling mechanism.
 * Implementations of this interface are responsible for generating a [DiceRoll].
 */
interface DiceRoller {
    /**
     * Rolls a specified number of dice.
     *
     * @param numberOfDice The number of dice to roll. Must be a positive integer.
     * @return A [DiceRoll] representing the outcome of each die roll.
     *         The size of the [DiceRoll] should be equal to [numberOfDice].
     *         Each integer in the [DiceRoll] should be between 1 and 6, inclusive.
     *         Returns an empty [DiceRoll] (an empty list) if [numberOfDice] is not positive.
     *         (Implementations might choose to throw an IllegalArgumentException for invalid input,
     *         but returning an empty list is a common convention for non-positive counts.)
     */
    fun roll(numberOfDice: Int): DiceRoll
}
