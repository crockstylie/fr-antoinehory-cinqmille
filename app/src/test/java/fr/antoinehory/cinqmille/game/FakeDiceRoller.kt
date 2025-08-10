package fr.antoinehory.cinqmille.game

// Assuming DiceRoll and DiceRoller are accessible from fr.antoinehory.cinqmille.game in main sourceset

class FakeDiceRoller : DiceRoller {
    private val rollsToReturn = mutableListOf<DiceRoll>()
    private var defaultRollFunction: (Int) -> DiceRoll = { numDice -> List(numDice) { 1 } }

    // Method to set specific rolls to be returned in sequence
    fun setNextRoll(vararg rolls: DiceRoll) {
        rollsToReturn.clear()
        rollsToReturn.addAll(rolls)
    }

    // Compatibility for existing tests if they used setRolls
    fun setRolls(vararg rolls: DiceRoll) {
        setNextRoll(*rolls)
    }

    fun setDefaultRoll(rollFunction: (Int) -> DiceRoll) {
        defaultRollFunction = rollFunction
    }

    override fun roll(numberOfDice: Int): DiceRoll {
        return if (rollsToReturn.isNotEmpty()) {
            rollsToReturn.removeAt(0) // Return and remove the first predefined roll
        } else {
            defaultRollFunction(numberOfDice) // Otherwise, use the default roll function
        }
    }
}