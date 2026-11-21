package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.i18n.tr

/** Die Stadtkasse. Jede Geldbewegung landet zusätzlich im [journal] der Kämmerei. */
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

    /** Zuschüsse und Prämien für erledigte Aufträge. */
    var grantIncome = 0L
        private set

    /** Jetzt (Simulationssekunden) – die Uhr für das Journal; setzt das Spiel vor jedem Schritt. */
    var now = 0.0

    val journal = Journal()

    /** Bis zum ersten Spielschritt: alles gehört zur Startausstattung. */
    var startup = true

    private fun book(
        amount: Long,
        category: BookingCategory,
        text: String = "",
        place: BookingPlace? = null,
    ) {
        balance += amount
        journal.book(Booking(now, amount, category, text, place, startup = startup), balance)
    }

    fun earnGrant(euros: Long) {
        grantIncome += euros
        book(euros, BookingCategory.GRANTS)
    }

    fun earnFine(
        euros: Int,
        place: BookingPlace? = null,
    ) {
        fineIncome += euros
        book(euros.toLong(), BookingCategory.FINES, place = place)
    }

    fun earnParking(euros: Long) {
        parkingIncome += euros
        book(euros, BookingCategory.PARKING)
    }

    fun earnTicket(euros: Int) {
        ticketIncome += euros
        book(euros.toLong(), BookingCategory.TICKETS)
    }

    /** Sonstige Einnahmen, z. B. Anwohnerparkausweise. */
    fun earn(
        euros: Long,
        category: BookingCategory = BookingCategory.PERMITS,
    ) {
        book(euros, category)
    }

    fun receiveAllowance(euros: Long) {
        allowances += euros
        book(euros, BookingCategory.ALLOWANCE)
    }

    /** Bucht [cost] ab, auch ins Minus (laufende Verträge). */
    fun charge(
        cost: Long,
        category: BookingCategory = BookingCategory.OTHER,
        text: String = "",
    ) {
        expenses += cost
        book(-cost, category, text)
    }

    /** Bucht [cost] nur ab, wenn genug Geld da ist; sonst Meldung und `false`. */
    fun pay(
        cost: Long,
        what: String,
        category: BookingCategory = BookingCategory.CONSTRUCTION,
        place: BookingPlace? = null,
    ): Boolean {
        if (balance < cost) {
            message(tr("Zu wenig Geld für {what} ({cost})", "what" to what, "cost" to formatEuro(cost)))
            return false
        }
        expenses += cost
        book(-cost, category, what, place)
        return true
    }
}

/** "12.345 €" */
fun formatEuro(amount: Long): String =
    de.bfi.trafficmaniac.i18n
        .formatEuro(amount)
