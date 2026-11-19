package de.bfi.trafficmaniac.game

import kotlinx.serialization.Serializable

/**
 * Was eine Stadt in Texten ausmacht: Zeitung, Anrede, Spitzname. Kommt aus dem Stadtpaket (`cities/<id>/city.json`);
 * die Standards gehören zu Pforzheim, der ersten Stadt.
 */
@Serializable
data class CityTexts(
    val name: String = "Pforzheim",
    /** „Pforzheims Straßen“. */
    val genitive: String = "Pforzheims",
    /** Spitzname für Schlagzeilen, z. B. „Goldstadt“. */
    val nickname: String = "Goldstadt",
    val newspaper: String = "PFORZHEIMER MORGENPOST",
    /** Erste Zeile der Abschiedsrede. */
    val greeting: String = "Liebe Pforzheimerinnen und Pforzheimer,",
    /** Unterzeile im Startmenü. */
    val tagline: String =
        "Die Verkehrs„beruhigung“ für Pforzheim – reich werden, gut dastehen, nicht abgewählt werden.",
)
