package pjs.dogbox.utils

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.Inet4Address
import java.net.NetworkInterface

object ServerUtil {

    const val DEFAULT_PORT = 8080

    var port: Int = DEFAULT_PORT
        private set

    val isRunning: Boolean
        get() = server != null

    @Volatile
    private var server: EmbeddedServer<*, *>? = null

    /** 本机局域网 IPv4 地址，例如 192.168.1.100 */
    val serverIp: String?
        get() = ipAddress()

    /** 客户端可访问的服务地址，例如 http://192.168.1.100:8080 */
    val serverUrl: String?
        get() = serverIp?.let { "http://$it:$port" }

    fun start(
        host: String = "0.0.0.0",
        desiredPort: Int = DEFAULT_PORT,
        configure: Application.() -> Unit = {},
    ): Boolean {
        if (server != null) return true
        val newServer = embeddedServer(CIO, port = desiredPort, host = host) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        prettyPrint = false
                    }
                )
            }
            routing {
                get("/ping") { call.respondText("pong") }
            }
            configure(this)
        }
        newServer.start(wait = false)
        port = desiredPort
        server = newServer
        return true
    }

    fun stop() {
        val running = server ?: return
        server = null
        runBlocking { running.stop(100, 1_000) }
    }

    fun ipAddress(): String? = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching null
        val candidates = mutableListOf<String>()
        while (interfaces.hasMoreElements()) {
            val ni = interfaces.nextElement()
            if (!ni.isUp || ni.isLoopback) continue
            val addrs = ni.inetAddresses
            while (addrs.hasMoreElements()) {
                val address = addrs.nextElement()
                if (address is Inet4Address && address.isSiteLocalAddress) {
                    candidates += address.hostAddress ?: ""
                }
            }
        }
        candidates.firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") }
            ?: candidates.firstOrNull()
    }.getOrNull()
}