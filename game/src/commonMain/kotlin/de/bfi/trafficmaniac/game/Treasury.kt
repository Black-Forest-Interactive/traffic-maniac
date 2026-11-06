package de.bfi.trafficmaniac.game

/** Die Stadtkasse. */
internal class Treasury(
    startBudget: Long,
    private val message: (String) -> Unit,
) {
    var balance = startBudget
        private set
    var fineIncome = 0L
        private set
    var expenses = 0L
        private set

    /** Vom Haushalt überwiesenes Geld (zählt nicht als eigener Verdienst). */
    var allowances = 0L
        private set

    fun earnFine(euros: Int) {
        balance += euros
        fineIncome += euros
    }

    fun receiveAllowance(euros: Long) {
        balance += euros
        allowances += euros
    }

    /** Bucht [cost] ab, auch ins Minus (laufende Verträge). */
    fun charge(cost: Long) {
        balance -= cost
        expenses += cost
    }

    /** Bucht [cost] nur ab, wenn genug Geld da ist; sonst Meldung und `false`. */
    fun pay(
        cost: Long,
        what: String,
    ): Boolean {
        if (balance < cost) {
            message("Zu wenig Geld für $what (${formatEuro(cost)})")
            return false
        }
        charge(cost)
        return true
    }
}

/** "12.345 €" */
fun formatEuro(amount: Long): String {
    val digits =
        kotlin.math
            .abs(amount)
            .toString()
            .reversed()
            .chunked(3)
            .joinToString(".")
            .reversed()
    return (if (amount < 0) "-" else "") + "$digits €"
}
