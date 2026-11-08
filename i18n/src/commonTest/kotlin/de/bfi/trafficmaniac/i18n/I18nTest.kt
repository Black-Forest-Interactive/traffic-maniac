package de.bfi.trafficmaniac.i18n

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class I18nTest {
    @AfterTest
    fun backToGerman() = I18n.use(Language.DE)

    @Test
    fun germanIsTheSourceAndTheFallback() {
        assertEquals("Tempo 30: Hauptstraße", tr("Tempo {kmh}: {street}", "kmh" to 30, "street" to "Hauptstraße"))
        I18n.use(Language.EN, mapOf("Tempo {kmh}: {street}" to "Speed limit {kmh}: {street}"))
        assertEquals("Speed limit 30: Hauptstraße", tr("Tempo {kmh}: {street}", "kmh" to 30, "street" to "Hauptstraße"))
        assertEquals("Noch nicht übersetzt", tr("Noch nicht übersetzt"))
    }

    @Test
    fun numbersAndMoneyFollowTheLanguage() {
        assertEquals("158.000 €", formatEuro(158_000))
        assertEquals("-1.500 €", formatEuro(-1_500))
        assertEquals("19,5", formatDecimal(19.46))
        assertEquals("1.234,57", formatDecimal(1234.567, 2))
        assertEquals("2,50 €", formatEuro(2.5))
        assertEquals("3 €", formatEuro(3.0))
        I18n.use(Language.EN)
        assertEquals("€158,000", formatEuro(158_000))
        assertEquals("19.5", formatDecimal(19.46))
        assertEquals("0", formatDecimal(-0.2, 0))
        assertEquals("€2.50", formatEuro(2.5))
    }
}
