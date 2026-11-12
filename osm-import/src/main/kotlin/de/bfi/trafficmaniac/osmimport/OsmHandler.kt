package de.bfi.trafficmaniac.osmimport

/** Mitglied einer Relation: Art (`node`, `way`, `relation`), OSM-ID und Rolle (z. B. `stop`, `platform`). */
class RelationMember(
    val type: String,
    val ref: Long,
    val role: String,
)

/** Empfänger für OSM-Elemente. Knoten kommen vor Wegen, Wege vor Relationen (Sortierung der Geofabrik-Extrakte). */
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

    /** Relationen (z. B. Buslinien); wer sie nicht braucht, ignoriert sie. */
    fun relation(
        id: Long,
        members: List<RelationMember>,
        tags: Map<String, String>,
    ) = Unit
}
