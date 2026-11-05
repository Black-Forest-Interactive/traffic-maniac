package de.bfi.trafficmaniac.city

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor

/** Liest und schreibt Stadtdateien (CBOR). */
@OptIn(ExperimentalSerializationApi::class)
object CityMapCodec {
    private val cbor = Cbor { ignoreUnknownKeys = true }

    fun encode(map: CityMap): ByteArray = cbor.encodeToByteArray(CityMap.serializer(), map)

    fun decode(bytes: ByteArray): CityMap {
        val map = cbor.decodeFromByteArray(CityMap.serializer(), bytes)
        require(map.formatVersion == CityMap.FORMAT_VERSION) {
            "Unsupported city file format ${map.formatVersion}, expected ${CityMap.FORMAT_VERSION}"
        }
        return map
    }
}
