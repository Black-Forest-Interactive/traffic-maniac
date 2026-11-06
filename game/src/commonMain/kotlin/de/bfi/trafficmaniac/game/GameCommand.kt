package de.bfi.trafficmaniac.game

/** Aktionen des Spielers. Sie werden gesammelt und zwischen zwei Simulationsschritten ausgeführt. */
sealed interface GameCommand {
    /** Neues Tempolimit für einen Straßenabschnitt; `null` stellt das ursprüngliche Limit wieder her. */
    data class SetSpeedLimit(
        val segment: Int,
        val kmh: Int?,
    ) : GameCommand

    data class BuildSignal(
        val node: Int,
    ) : GameCommand

    data class RemoveSignal(
        val node: Int,
    ) : GameCommand

    data class PlaceCamera(
        val segment: Int,
    ) : GameCommand

    data class RemoveCamera(
        val cameraId: Int,
    ) : GameCommand

    /** PR-Vertrag ändern; abgerechnet wird in der Haushaltsrunde. */
    data class SetPrContract(
        val contract: PrContract,
    ) : GameCommand

    /** Kampagne "Totschlagargumente" an- oder abschalten. */
    data class SetArgumentsCampaign(
        val enabled: Boolean,
    ) : GameCommand
}
