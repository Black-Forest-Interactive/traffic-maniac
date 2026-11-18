package de.bfi.trafficmaniac.android

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StatisticsTest {
    private lateinit var directory: File
    private lateinit var server: HttpServer
    private val received = mutableListOf<String>()
    private var status = 201

    @BeforeTest
    fun start() {
        directory = Files.createTempDirectory("stats").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/games") { exchange ->
            received += exchange.requestBody.readBytes().decodeToString()
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        server.start()
    }

    @AfterTest
    fun stop() {
        server.stop(0)
        directory.deleteRecursively()
    }

    private fun statistics() = Statistics(directory, "http://127.0.0.1:${server.address.port}/api")

    private fun queue(vararg bodies: String) {
        val outbox = File(directory, "outbox").apply { mkdirs() }
        bodies.forEachIndexed { i, body -> File(outbox, "$i.json").writeText(body) }
    }

    @Test
    fun sendsAndDeletesWaitingPackets() {
        queue("""{"a":1}""", """{"b":2}""")
        assertEquals(2, statistics().sendPending())
        assertEquals(listOf("""{"a":1}""", """{"b":2}"""), received)
        assertEquals(0, statistics().pending().size)
    }

    @Test
    fun rejectedPacketsAreDropped() {
        status = 400
        queue("""{"alt":true}""")
        assertEquals(0, statistics().sendPending())
        assertEquals(0, statistics().pending().size)
    }

    @Test
    fun withoutServerPacketsWait() {
        queue("""{"a":1}""")
        server.stop(0)
        assertEquals(0, statistics().sendPending())
        assertEquals(1, statistics().pending().size)
    }

    @Test
    fun withoutAddressNothingIsSent() {
        queue("""{"a":1}""")
        assertEquals(0, Statistics(directory, null).sendPending())
        assertEquals(1, statistics().pending().size)
    }

    @Test
    fun installationIdStaysUntilRenewed() {
        val first = statistics().installation
        assertEquals(first, statistics().installation)
        statistics().newInstallation()
        assert(statistics().installation != first)
    }
}
