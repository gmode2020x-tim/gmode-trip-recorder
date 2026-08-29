package ca.gmode.triprecorder.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

internal object HomeAssistantEndpoint {
    fun requiresLocalNetwork(baseUrl: String): Boolean {
        val host = runCatching { URI(baseUrl).host }
            .getOrNull()
            ?.trim('[', ']')
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: return false
        if (host == "localhost" || host.endsWith(".local") || !host.contains('.')) return true
        if (host.contains(':')) {
            if (host == "::1" || host.startsWith("fc") || host.startsWith("fd")) return true
            if (host.matches(Regex("^fe[89ab].*"))) return true
        }

        val octets = host.split('.').map { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it == null || it !in 0..255 }) return false
        val first = octets[0]!!
        val second = octets[1]!!
        return first == 10 ||
            first == 127 ||
            (first == 100 && second in 64..127) ||
            (first == 169 && second == 254) ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }
}

internal class HomeAssistantNetworkClient(context: Context) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun create(baseUrl: String): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        if (!HomeAssistantEndpoint.requiresLocalNetwork(baseUrl)) return builder.build()

        val wifi = findWifiNetwork()
        if (wifi != null) {
            builder.socketFactory(wifi.socketFactory)
            builder.dns(
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        wifi.getAllByName(hostname).toList()
                },
            )
            return builder.build()
        }

        val activeCapabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        if (activeCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
            return builder.build()
        }
        throw LocalNetworkUnavailableException(
            "Connect the S24 to home Wi-Fi before syncing with the local Home Assistant address.",
        )
    }

    @Suppress("DEPRECATION")
    private fun findWifiNetwork(): Network? {
        val active = connectivity.activeNetwork
        if (active != null && isUsableWifi(active)) return active
        return connectivity.allNetworks.firstOrNull(::isUsableWifi)
    }

    private fun isUsableWifi(network: Network): Boolean {
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
    }
}

internal class LocalNetworkUnavailableException(message: String) : IOException(message)
