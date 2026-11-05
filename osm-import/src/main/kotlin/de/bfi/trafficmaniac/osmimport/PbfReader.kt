package de.bfi.trafficmaniac.osmimport

import crosby.binary.BinaryParser
import crosby.binary.Osmformat
import crosby.binary.file.BlockInputStream
import java.io.File

/** Liest eine `.osm.pbf`-Datei und reicht Knoten und Wege an einen [OsmHandler] weiter. Relationen werden ignoriert. */
object PbfReader {
    fun read(
        file: File,
        handler: OsmHandler,
    ) {
        file.inputStream().buffered().use { input ->
            BlockInputStream(input, Parser(handler)).process()
        }
    }

    private class Parser(
        private val handler: OsmHandler,
    ) : BinaryParser() {
        override fun parseDense(nodes: Osmformat.DenseNodes) {
            var id = 0L
            var lat = 0L
            var lon = 0L
            var keyValueIndex = 0
            val hasTags = nodes.keysValsCount > 0
            for (i in 0 until nodes.idCount) {
                // Dichte Knoten sind delta-kodiert
                id += nodes.getId(i)
                lat += nodes.getLat(i)
                lon += nodes.getLon(i)
                var tags = emptyMap<String, String>()
                if (hasTags) {
                    while (nodes.getKeysVals(keyValueIndex) != 0) {
                        if (tags.isEmpty()) tags = HashMap()
                        val key = getStringById(nodes.getKeysVals(keyValueIndex))
                        val value = getStringById(nodes.getKeysVals(keyValueIndex + 1))
                        (tags as HashMap)[key] = value
                        keyValueIndex += 2
                    }
                    keyValueIndex++
                }
                handler.node(id, parseLat(lat), parseLon(lon), tags)
            }
        }

        override fun parseNodes(nodes: List<Osmformat.Node>) {
            for (node in nodes) {
                val tags = tags(node.keysList, node.valsList)
                handler.node(node.id, parseLat(node.lat), parseLon(node.lon), tags)
            }
        }

        override fun parseWays(ways: List<Osmformat.Way>) {
            for (way in ways) {
                var ref = 0L
                val refs = LongArray(way.refsCount) { i -> (ref + way.getRefs(i)).also { ref = it } }
                handler.way(way.id, refs, tags(way.keysList, way.valsList))
            }
        }

        override fun parseRelations(relations: List<Osmformat.Relation>) = Unit

        override fun parse(header: Osmformat.HeaderBlock) = Unit

        override fun complete() = Unit

        private fun tags(
            keys: List<Int>,
            values: List<Int>,
        ): Map<String, String> =
            if (keys.isEmpty()) {
                emptyMap()
            } else {
                keys.indices.associate { getStringById(keys[it]) to getStringById(values[it]) }
            }
    }
}
