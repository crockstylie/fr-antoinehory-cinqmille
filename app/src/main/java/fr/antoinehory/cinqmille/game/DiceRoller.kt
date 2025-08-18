package fr.antoinehory.cinqmille.game // Package pour le nouveau fichier

/**
 * Defines the contract for a dice rolling mechanism.
 * Implementations of this interface are responsible for generating a list of dice roll results.
 */
interface DiceRoller {
    /**
     * Rolls a specified number of dice.
     *
     * @param numberOfDice The number of dice to roll. Must be a positive integer.
     * @return A list of integers representing the outcome of each die roll.
     *         The size of the list should be equal to [numberOfDice].
     *         Each integer should be between 1 and 6, inclusive.
     *         Returns an empty list if [numberOfDice] is not positive.
     *         (Implementations might choose to throw an IllegalArgumentException for invalid input,
     *         but returning an empty list is a common convention for non-positive counts.)
     */
    fun roll(numberOfDice: Int): List<Int>
}
