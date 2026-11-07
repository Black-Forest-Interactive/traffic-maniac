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

    /** Fester Blitzer oder – mit [mobile] – Lasermessung (Personal, Kosten pro Tag, frei umsetzbar). */
    data class PlaceCamera(
        val segment: Int,
        val mobile: Boolean = false,
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

    data class FireWardens(
        val count: Int,
    ) : GameCommand
}
