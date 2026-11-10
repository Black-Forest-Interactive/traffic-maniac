package de.bfi.trafficmaniac.game

/** Aktionen des Spielers. Sie werden gesammelt und zwischen zwei Simulationsschritten ausgeführt. */
sealed interface GameCommand {
    /** Neues Tempolimit für einen Straßenabschnitt; `null` stellt das ursprüngliche Limit wieder her. */
    data class SetSpeedLimit(
        val segment: Int,
        val kmh: Int?,
        /** Nur in der Nachtruhe (22–6 Uhr), z. B. "30 nur nachts". */
        val nightOnly: Boolean = false,
    ) : GameCommand

    data class BuildSignal(
        val node: Int,
    ) : GameCommand

    data class RemoveSignal(
        val node: Int,
    ) : GameCommand

    /** Grünzeit der Ampelanlage an [node] aufteilen: [mainShare] für die Hauptrichtung (0,5 = gleich). */
    data class SetSignalProgram(
        val node: Int,
        val mainShare: Double,
    ) : GameCommand

    /** Eigene Baustelle (Tempo 30, verengt) auf [segments] einrichten bzw. abbauen. */
    data class SetRoadworks(
        val segments: List<Int>,
        val active: Boolean,
    ) : GameCommand

    /** Vollsperrung auf [segments] bzw. aufheben. */
    data class SetRoadClosure(
        val segments: List<Int>,
        val closed: Boolean,
    ) : GameCommand

    /** Winterdienst: so viele Räumfahrzeuge sollen unterwegs sein. */
    data class SetPloughs(
        val count: Int,
    ) : GameCommand

    /** Grüne Welle entlang [segments] (zusammenhängend, vom ersten zum letzten) für [kmh]. */
    data class SetGreenWave(
        val segments: List<Int>,
        val kmh: Int,
    ) : GameCommand

    /** Fester Blitzer oder – mit [mobile] – Lasermessung (Personal, Kosten pro Tag, frei umsetzbar). */
    data class PlaceCamera(
        val segment: Int,
        val mobile: Boolean = false,
    ) : GameCommand

    /** Rotlichtblitzer an der Haltelinie der Ampelzufahrt [link]; mit [withSpeed] misst er auch das Tempo. */
    data class PlaceRedLightCamera(
        val link: Int,
        val withSpeed: Boolean,
    ) : GameCommand

    /** Polizeikontrolle an [segment] für einige Stunden. */
    data class PlacePoliceCheck(
        val segment: Int,
    ) : GameCommand

    /** Fahrradstraße auf [segments] einrichten (Tempo 30, Überholverbot für Kfz) bzw. aufheben. */
    data class SetBikeStreet(
        val segments: List<Int>,
        val on: Boolean,
    ) : GameCommand

    /** Geschwindigkeits-Anzeigetafel an [segment]: misst nur, blitzt nicht. */
    data class PlaceDisplay(
        val segment: Int,
    ) : GameCommand

    /** Blitzeranhänger aufstellen: ein freier aus dem Depot (Umsetzgebühr) oder ein neuer. */
    data class PlaceTrailer(
        val segment: Int,
    ) : GameCommand

    data class RemoveCamera(
        val cameraId: Int,
    ) : GameCommand

    /** PR-Vertrag ändern; abgerechnet wird in der Haushaltsrunde. */
    data class SetPrContract(
        val contract: PrContract,
    ) : GameCommand

    /** Abschnitt zur Vorfahrtsstraße erklären oder das zurücknehmen. */
    data class SetPriorityRoad(
        val segment: Int,
        val priority: Boolean,
    ) : GameCommand

    /** Radweg-Querung markieren oder entfernen. */
    data class SetBikeCrossing(
        val segment: Int,
        val crossing: Boolean,
    ) : GameCommand

    data class HireStaff(
        val count: Int,
    ) : GameCommand

    data class FireStaff(
        val count: Int,
    ) : GameCommand

    data class SetStrictness(
        val strictness: Strictness,
    ) : GameCommand

    data class SetUnidentifiedPolicy(
        val policy: UnidentifiedPolicy,
    ) : GameCommand

    /** Bearbeitungsfrist der Bußgeldstelle in Spieltagen (bis zur gesetzlichen Obergrenze). */
    data class SetDeadlineDays(
        val days: Int,
    ) : GameCommand

    /** Kampagne "Totschlagargumente" an- oder abschalten. */
    data class SetArgumentsCampaign(
        val enabled: Boolean,
    ) : GameCommand

    // --- Bauamt ---

    /** Antwort auf eine Schadensmeldung. */
    data class RespondToDamage(
        val reportId: Int,
        val response: DamageResponse,
    ) : GameCommand

    data class SetMaintenanceBudget(
        val budget: MaintenanceBudget,
    ) : GameCommand

    /** Abschnitt in die ausgeschilderte Umleitung aufnehmen oder herausnehmen (auch Nebenstraßen). */
    data class SetDetour(
        val segment: Int,
        val enabled: Boolean,
    ) : GameCommand

    // --- Parken ---

    /** Gebühr in Euro je Stunde für einen Parkplatz ([lot]) oder alle (`null`). */
    data class SetParkingFee(
        val lot: Int?,
        val euros: Double,
    ) : GameCommand

    /** Gebühr am Parkscheinautomaten am Straßenrand (Euro je Stunde). */
    data class SetCurbFee(
        val euros: Double,
    ) : GameCommand

    /** Stellplätze eines Parkplatzes; 0 schafft ihn ab. */
    data class SetParkingCapacity(
        val lot: Int,
        val capacity: Int,
    ) : GameCommand

    /** Ein Schritt Straßenrand-Parkplätze in Radspuren und Blumenkübel umwandeln. */
    data object ConvertCurbParking : GameCommand

    data class SetBreadButton(
        val enabled: Boolean,
    ) : GameCommand

    data class SetResidentParking(
        val enabled: Boolean,
    ) : GameCommand

    data class HireWardens(
        val count: Int,
    ) : GameCommand

    /** Spatenstich oder Eröffnung mit Presse – einmal am Tag. */
    data object HoldCeremony : GameCommand

    data class FireWardens(
        val count: Int,
    ) : GameCommand
}
