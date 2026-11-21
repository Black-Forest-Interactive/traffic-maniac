package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable

/** Wofür Geld fließt – Einnahmen oben, Ausgaben unten. */
enum class BookingCategory(
    private val labelText: String,
    val income: Boolean,
) {
    FINES(translatable("Blitzer-Bußgelder"), true),
    TICKETS(translatable("Knöllchen"), true),
    PARKING(translatable("Parkgebühren"), true),
    GRANTS(translatable("Aufträge & Prämien"), true),
    ALLOWANCE(translatable("Haushalt"), true),
    PERMITS(translatable("Anwohnerausweise"), true),
    CONSTRUCTION(translatable("Baumaßnahmen"), false),
    REPAIRS(translatable("Reparaturen"), false),
    UPKEEP(translatable("Straßenerhalt"), false),
    STAFF(translatable("Personal"), false),
    PR(translatable("PR & Presse"), false),
    LASER(translatable("Laser-Miete"), false),
    ROADWORKS(translatable("Baustellen"), false),
    WINTER(translatable("Winterdienst"), false),
    POLICE(translatable("Polizeiaktionen"), false),
    LAWSUITS(translatable("Klagen"), false),
    OTHER(translatable("Sonstiges"), false),
    ;

    val label: String get() = tr(labelText)
}

/** Ort einer Buchung: Straßenabschnitt und Straßenname (z. B. der Blitzer, der das Bußgeld eingebracht hat). */
data class BookingPlace(
    val segment: Int,
    val street: String,
)

/**
 * Eine Geldbewegung: Zeit (Simulationssekunden), Betrag (Einnahmen positiv, Ausgaben negativ), Kategorie, Text und
 * Ort. Kleine, häufige Einnahmen (Bußgelder, Knöllchen, Gebühren) werden je Stunde und Ort zu einer Buchung
 * gebündelt – [count] sagt, wie viele es waren.
 */
data class Booking(
    val time: Double,
    val amount: Long,
    val category: BookingCategory,
    val text: String = "",
    val place: BookingPlace? = null,
    val count: Int = 1,
    /** Startausstattung: vor dem ersten Spielschritt gebaut – zählt zur Amtszeit, nicht zu „heute“. */
    val startup: Boolean = false,
)

/** Das Journal der Kämmerei: die letzten Buchungen, Summen je Kategorie und Geldfluss je Stunde. */
data class TreasuryJournal(
    /** Die neuesten Buchungen zuerst (höchstens [Journal.SHOWN]). */
    val bookings: List<Booking> = emptyList(),
    /** Summen je Kategorie über die Amtszeit bzw. seit Tagesbeginn. */
    val totals: Map<BookingCategory, Long> = emptyMap(),
    val today: Map<BookingCategory, Long> = emptyMap(),
    /** Einnahmen und Ausgaben je Stunde der letzten 24 Stunden (Ausgaben positiv), die laufende Stunde zuletzt. */
    val hourlyIncome: List<Long> = emptyList(),
    val hourlyExpenses: List<Long> = emptyList(),
    /** Kassenstand am Ende jeder der letzten 24 Stunden. */
    val hourlyBalance: List<Long> = emptyList(),
    /** Blitzer-Bußgelder der Amtszeit je Straße; „“ = ohne Ort (z. B. Polizeiaktion gegen Warn-Apps). */
    val finesByStreet: Map<String, Long> = emptyMap(),
    /** Ausgaben der Amtszeit je Posten (Blitzer, Schilder, Busspur, Personal …), positiv. */
    val expenseItems: Map<String, Long> = emptyMap(),
    /** Stunde (seit Spielbeginn) des letzten Eintrags in den Stundenlisten – für die Beschriftung der Zeitachse. */
    val lastHour: Int = 0,
)

/** Führt das Journal: bündelt, zählt Summen und hält die letzten 24 Stunden. */
internal class Journal {
    private val entries = ArrayDeque<Booking>()
    private val totals = HashMap<BookingCategory, Long>()
    private var today = HashMap<BookingCategory, Long>()
    private val income = LongArray(HOURS)
    private val expenses = LongArray(HOURS)
    private val balance = LongArray(HOURS)
    private val stamp = IntArray(HOURS) { -1 }
    private val items = HashMap<String, Long>()
    private val fines = HashMap<String, Long>()

    fun book(
        booking: Booking,
        balanceAfter: Long,
    ) {
        if (booking.amount == 0L) return
        totals.merge(booking.category, booking.amount, Long::plus)
        if (booking.amount < 0) items.merge(itemOf(booking), -booking.amount, Long::plus)
        if (booking.category ==
            BookingCategory.FINES
        ) {
            fines.merge(booking.place?.street.orEmpty(), booking.amount, Long::plus)
        }
        val hour = (booking.time / SECONDS_PER_HOUR).toInt()
        // Startausstattung zählt zur Amtszeit, nicht zu „heute“ und nicht zum Geldfluss der letzten Stunden
        if (!booking.startup) {
            today.merge(booking.category, booking.amount, Long::plus)
            val slot = slot(hour)
            if (booking.amount > 0) income[slot] += booking.amount else expenses[slot] -= booking.amount
            balance[slot] = balanceAfter
        }
        val last = entries.firstOrNull()
        if (last != null && bundles(last, booking, hour)) {
            entries[0] = last.copy(amount = last.amount + booking.amount, count = last.count + booking.count)
        } else {
            entries.addFirst(booking)
            while (entries.size > KEPT) entries.removeLast()
        }
    }

    /**
     * Gleiche Posten kommen in eine Buchung („2× Tempo-30-Schild“): gleiche Art, gleicher Text, gleiche Stelle, gleiche
     * Stunde – die Startausstattung als Ganzes.
     */
    private fun bundles(
        last: Booking,
        booking: Booking,
        hour: Int,
    ): Boolean =
        last.category == booking.category &&
            last.text == booking.text &&
            last.place == booking.place &&
            last.startup == booking.startup &&
            (booking.startup || (last.time / SECONDS_PER_HOUR).toInt() == hour)

    /** Platz der Stunde im Ring; eine neue Stunde beginnt bei null. */
    private fun slot(hour: Int): Int {
        val slot = hour.mod(HOURS)
        if (stamp[slot] != hour) {
            stamp[slot] = hour
            income[slot] = 0
            expenses[slot] = 0
            balance[slot] = Long.MIN_VALUE
        }
        return slot
    }

    fun startNewDay() {
        today = HashMap()
    }

    fun state(
        time: Double,
        currentBalance: Long,
    ): TreasuryJournal {
        val now = (time / SECONDS_PER_HOUR).toInt()
        val hours = (now - HOURS + 1..now).toList()
        var carried = currentBalance
        // Kassenstand je Stunde: rückwärts den letzten bekannten Stand übernehmen
        val balances =
            hours
                .reversed()
                .map { hour ->
                    val slot = hour.mod(HOURS)
                    if (stamp[slot] == hour && balance[slot] != Long.MIN_VALUE) carried = balance[slot]
                    carried
                }.reversed()
        return TreasuryJournal(
            bookings = entries.take(SHOWN),
            totals = totals.toMap(),
            today = today.toMap(),
            hourlyIncome = hours.map { if (stamp[it.mod(HOURS)] == it) income[it.mod(HOURS)] else 0L },
            hourlyExpenses = hours.map { if (stamp[it.mod(HOURS)] == it) expenses[it.mod(HOURS)] else 0L },
            hourlyBalance = balances,
            lastHour = now,
            expenseItems = items.toMap(),
            finesByStreet = fines.toMap(),
        )
    }

    companion object {
        const val SHOWN = 200
        private const val KEPT = 400
        private const val HOURS = 24
        private const val SECONDS_PER_HOUR = 3_600.0
    }
}

/** Stichworte, unter denen Baumaßnahmen zu einem Posten zusammengefasst werden (deutsch und übersetzt). */
private val ITEM_GROUPS =
    listOf(
        listOf(translatable("Blitzer"), translatable("Attrappe"), translatable("Anhänger")) to translatable("Blitzer"),
        listOf(translatable("Schild")) to translatable("Schilder"),
        listOf(translatable("Anzeigetafel")) to translatable("Anzeigetafeln"),
    )

/** Posten einer Ausgabe: Baumaßnahmen nach Art (Blitzer, Schilder …), alles andere nach Kategorie. */
internal fun itemOf(booking: Booking): String {
    if (booking.category != BookingCategory.CONSTRUCTION) return booking.category.label
    val text = booking.text
    val group =
        ITEM_GROUPS.firstOrNull { (words, _) ->
            words.any {
                text.contains(it, ignoreCase = true) ||
                    text.contains(tr(it), ignoreCase = true)
            }
        }
    return group?.let { tr(it.second) } ?: text.ifEmpty { booking.category.label }
}
