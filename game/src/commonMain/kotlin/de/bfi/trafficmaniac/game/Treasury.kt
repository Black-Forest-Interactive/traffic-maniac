package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.i18n.tr

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
    var parkingIncome = 0L
        private set
    var ticketIncome = 0L
        private set

    fun earnFine(euros: Int) {
        balance += euros
        fineIncome += euros
    }

    fun earnParking(euros: Long) {
        balance += euros
        parkingIncome += euros
    }

    fun earnTicket(euros: Int) {
        balance += euros
        ticketIncome += euros
    }

    /** Sonstige Einnahmen, z. B. Anwohnerparkausweise. */
    fun earn(euros: Long) {
        balance += euros
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
            message(tr("Zu wenig Geld für {what} ({cost})", "what" to what, "cost" to formatEuro(cost)))
            return false
        }
        charge(cost)
        return true
    }
}

/** "12.345 €" */
fun formatEuro(amount: Long): String =
    de.bfi.trafficmaniac.i18n
        .formatEuro(amount)
