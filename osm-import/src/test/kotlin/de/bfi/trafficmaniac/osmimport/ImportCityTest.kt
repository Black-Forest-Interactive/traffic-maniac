package de.bfi.trafficmaniac.osmimport

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ImportCityTest {
    @Test
    fun readsNameExtractAndBoxFromTheCityPack() {
        val info = CityImportInfo.parse(File("../cities/pforzheim/city.json").readText())
        assertEquals("Pforzheim", info.texts.name)
        assertEquals("karlsruhe-regbez-latest.osm.pbf", info.import.extract.substringAfterLast('/'))
        GeoBox.parse(info.import.bbox)
    }

    @Test
    fun downloadWritesTheFileAndLeavesNoPartBehind() {
        val payload = ByteArray(200_000) { (it % 251).toByte() }
        serve(payload) { url ->
            val target = File(createTempDirectory("raw").toFile(), "extract.osm.pbf")
            download(url, target)
            assertContentEquals(payload, target.readBytes())
            assertFalse(File(target.path + ".part").exists())
        }
    }

    @Test
    fun aFailedDownloadKeepsTheOldExtract() {
        serve(ByteArray(0), status = 404) { url ->
            val target = File(createTempDirectory("raw").toFile(), "extract.osm.pbf").apply { writeText("alt") }
            assertFailsWith<IllegalStateException> { download(url, target) }
            assertEquals("alt", target.readText())
        }
    }

    private fun serve(
        body: ByteArray,
        status: Int = 200,
        block: (String) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/extract.osm.pbf") { exchange ->
            exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/extract.osm.pbf")
        } finally {
            server.stop(0)
        }
    }
}
