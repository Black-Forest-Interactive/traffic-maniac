package de.bfi.trafficmaniac.osmimport

/** Empfänger für OSM-Elemente. Knoten kommen vor Wegen (Sortierung der Geofabrik-Extrakte). */
interface OsmHandler {
    fun node(
        id: Long,
        lat: Double,
        lon: Double,
        tags: Map<String, String>,
    )

    fun way(
        id: Long,
        refs: LongArray,
        tags: Map<String, String>,
    )
}
