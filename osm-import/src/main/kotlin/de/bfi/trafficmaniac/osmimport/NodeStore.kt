package de.bfi.trafficmaniac.osmimport

/**
 * Speichert Knotenkoordinaten in primitiven Arrays statt in einer Map –
 * bei über einer Million Knoten spart das viel Speicher. Knoten müssen nach ID sortiert ankommen.
 */
internal class NodeStore {
    private var ids = LongArray(INITIAL_CAPACITY)
    private var lats = DoubleArray(INITIAL_CAPACITY)
    private var lons = DoubleArray(INITIAL_CAPACITY)

    var size = 0
        private set

    fun add(
        id: Long,
        lat: Double,
        lon: Double,
    ) {
        check(size == 0 || id > ids[size - 1]) { "Nodes must be sorted by id (got $id after ${ids[size - 1]})" }
        if (size == ids.size) grow()
        ids[size] = id
        lats[size] = lat
        lons[size] = lon
        size++
    }

    /** Index des Knotens oder -1, wenn er nicht gespeichert ist (z. B. außerhalb des Ausschnitts). */
    fun indexOf(id: Long): Int = ids.binarySearch(id, 0, size).let { if (it >= 0) it else -1 }

    fun lat(index: Int): Double = lats[index]

    fun lon(index: Int): Double = lons[index]

    private fun grow() {
        val capacity = ids.size * 2
        ids = ids.copyOf(capacity)
        lats = lats.copyOf(capacity)
        lons = lons.copyOf(capacity)
    }

    private companion object {
        const val INITIAL_CAPACITY = 1 shl 16
    }
}
