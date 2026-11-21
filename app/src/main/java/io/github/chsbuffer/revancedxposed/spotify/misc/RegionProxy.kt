package io.github.chsbuffer.revancedxposed.spotify.misc

import app.revanced.extension.shared.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.settings.ModuleSettings
import java.io.BufferedReader
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket
import java.net.URI
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Experimental region proxy.
 *
 * Spotify decides the account/market mismatch on the server from the client's IP. A client-side
 * country flag cannot change that, so the only honest way to "be" in another country is to send the
 * traffic through an egress point in that country. This object installs a [ProxySelector] that
 * applies to every Spotify host, including the audio/image CDNs used for playback:
 * `*.spotify.com`, `*.spotify.net`, `*.scdn.co`, `*.spotifycdn.com` and the
 * `*.akamaized.net` audio hosts. Non-Spotify traffic stays direct.
 *
 * Two modes:
 *  - **custom** (`host:port`, `http://…` or `socks5://…`): the user's own HTTP/SOCKS proxy. This is
 *    the reliable path — for example a v2rayNG/Clash local port after selecting an exit node.
 *  - **automatic**: a public HTTP proxy from proxyscrape's per-country list, health-checked with a
 *    CONNECT to `spclient.wg.spotify.com:443`. Public proxies are unreliable and can be slow; this
 *    is why the feature is experimental, off by default, and clearly labelled in the UI.
 *
 * All candidates are followed by [Proxy.NO_PROXY], so a dead proxy degrades to a direct connection
 * instead of breaking Spotify entirely. A slow-but-alive proxy will slow playback too; this is
 * inherent to routing audio through it.
 */
private const val LIST_URL =
    "https://api.proxyscrape.com/v4/free-proxy-list/get" +
            "?request=display_proxies&country=%s&protocol=http" +
            "&proxy_format=ipport&format=text"
private const val REFRESH_INTERVAL_MS = 10 * 60 * 1000L
private const val MAX_CANDIDATES = 5

object RegionProxy {
    @Volatile
    private var selector: SpotifyRegionProxySelector? = null

    @Volatile
    private var installed = false

    fun install(appContext: android.content.Context) {
        if (installed) return
        if (!ModuleSettings.isEnabled(ModuleSettings.REGION_PROXY_ENABLED)) {
            Logger.printInfo { "Region proxy: disabled" }
            return
        }

        val country = ModuleSettings.getString(ModuleSettings.REGION_PROXY_COUNTRY)
            .ifBlank { "NL" }
            .uppercase()
        val custom = ModuleSettings.getString(ModuleSettings.REGION_PROXY_CUSTOM).trim()

        val state = RegionProxyState(country, custom)
        val newSelector = SpotifyRegionProxySelector(state, ProxySelector.getDefault())
        selector = newSelector
        installed = true

        runCatching { ProxySelector.setDefault(newSelector) }
            .onFailure { Logger.printException({ "Region proxy: cannot set default selector" }, it) }

        // Some HTTP stacks call setDefault() explicitly during startup. Keep ours in place for the
        // lifetime of the process; restoring the original is only relevant if the feature is ever
        // made toggleable without a restart.
        runCatching {
            XposedHelpers.findAndHookMethod(
                "java.net.ProxySelector", appContext.classLoader, "setDefault",
                ProxySelector::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val current = selector ?: return
                        if (param.args.getOrNull(0) !== current) {
                            param.args[0] = current
                        }
                    }
                },
            )
        }.onFailure {
            Logger.printDebug { "Region proxy: setDefault override hook not installed: ${it.javaClass.simpleName}" }
        }

        Logger.printInfo {
            "Region proxy: enabled country=$country custom=${custom.ifEmpty { "auto" }}"
        }

        // Give the first API calls a chance to already use the proxy. The wait is bounded and only
        // happens when the user explicitly enabled the experimental feature.
        state.refreshBlocking(3_500)
        state.refreshAsyncIfNeeded()
    }
}

private class SpotifyRegionProxySelector(
    private val state: RegionProxyState,
    private val original: ProxySelector?,
) : ProxySelector() {

    @Volatile
    private var loggedSelection = false

    override fun select(uri: URI): List<Proxy> {
        val host = uri.host?.lowercase() ?: return original?.select(uri) ?: listOf(Proxy.NO_PROXY)
        if (!isSpotifyHost(host)) {
            return original?.select(uri) ?: listOf(Proxy.NO_PROXY)
        }

        state.refreshAsyncIfNeeded()
        val proxies = state.candidates
        if (proxies.isNotEmpty() && !loggedSelection) {
            loggedSelection = true
            Logger.printInfo { "Region proxy: routing $host via ${proxies.first()}" }
        }
        return if (proxies.isEmpty()) {
            listOf(Proxy.NO_PROXY)
        } else {
            proxies + Proxy.NO_PROXY
        }
    }

    override fun connectFailed(uri: URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {
        Logger.printInfo { "Region proxy: connect failed for $uri: ${ioe?.message}" }
        state.invalidate()
    }

    private fun isSpotifyHost(host: String): Boolean = SPOTIFY_HOST_SUFFIXES.any { suffix ->
        host == suffix || host.endsWith(".$suffix")
    }

    private companion object {
        /**
         * Playback manifests and audio segments live on `*.scdn.co` and the Spotify-branded
         * `*.akamaized.net` hosts, so they must be included for region proxying to affect
         * playback, not only API calls.
         */
        val SPOTIFY_HOST_SUFFIXES = listOf(
            "spotify.com",
            "spotify.net",
            "spotify.cloud",
            "scdn.co",
            "spotifycdn.com",
            "spotifycdn.net",
            "akamaized.net",
        )
    }
}

private class RegionProxyState(
    private val country: String,
    private val custom: String,
) {
    @Volatile
    var candidates: List<Proxy> = emptyList()
        private set

    @Volatile
    private var lastRefreshAt = 0L

    @Volatile
    private var refreshing = false

    private val firstResult = CountDownLatch(1)

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "femify-region-proxy").apply { isDaemon = true }
    }

    fun invalidate() {
        candidates = emptyList()
        lastRefreshAt = 0
    }

    fun refreshAsyncIfNeeded() {
        val now = System.currentTimeMillis()
        if (refreshing) return
        if (now - lastRefreshAt < REFRESH_INTERVAL_MS) return
        refreshing = true
        lastRefreshAt = now
        executor.execute {
            try {
                candidates = loadCandidates()
            } catch (t: Throwable) {
                Logger.printException({ "Region proxy: refresh failed" }, t)
                candidates = emptyList()
            } finally {
                refreshing = false
                firstResult.countDown()
                Logger.printInfo {
                    "Region proxy: refresh finished, ${candidates.size} candidate(s)" +
                            if (custom.isNotEmpty()) " for custom $custom" else " for $country"
                }
            }
        }
    }

    fun refreshBlocking(timeoutMs: Long) {
        refreshAsyncIfNeeded()
        runCatching { firstResult.await(timeoutMs, TimeUnit.MILLISECONDS) }
        Logger.printInfo {
            "Region proxy: ${candidates.size} usable candidate(s) for $country" +
                    if (custom.isNotEmpty()) " (custom $custom)" else ""
        }
    }

    private fun loadCandidates(): List<Proxy> {
        customProxy()?.let { return listOf(it) }

        val endpoints = fetchPublicEndpoints()
            .asSequence()
            .filter { it != null }
            .take(24)
            .toList()

        val healthy = ArrayList<Proxy>()
        for (endpoint in endpoints) {
            if (healthy.size >= MAX_CANDIDATES) break
            if (checkProxy(endpoint!!)) {
                healthy.add(Proxy(Proxy.Type.HTTP, InetSocketAddress(endpoint.first, endpoint.second)))
            }
        }
        return healthy
    }

    private fun customProxy(): Proxy? {
        if (custom.isEmpty()) return null

        val value = if ("://" in custom) custom else "http://$custom"
        val uri = runCatching { URI(value) }.getOrNull()
        val host = uri?.host
        val port = uri?.port ?: -1
        if (host.isNullOrBlank() || port !in 1..65535) {
            Logger.printInfo { "Region proxy: invalid custom endpoint '$custom', using automatic" }
            return null
        }

        val type = when {
            value.startsWith("socks5://", ignoreCase = true) ||
                    value.startsWith("socks://", ignoreCase = true) -> Proxy.Type.SOCKS

            else -> Proxy.Type.HTTP
        }
        Logger.printInfo { "Region proxy: using custom $type proxy $host:$port" }
        return Proxy(type, InetSocketAddress(host, port))
    }

    private fun fetchPublicEndpoints(): List<Pair<String, Int>?> {
        val connection = URL(String.format(LIST_URL, country))
            .openConnection(Proxy.NO_PROXY)
            .apply {
                connectTimeout = 4_000
                readTimeout = 4_000
                setRequestProperty("User-Agent", "Femify")
            }
        return connection.getInputStream().bufferedReader().useLines { lines ->
            lines.mapNotNull { parseEndpoint(it.trim()) }.toList()
        }
    }

    private fun parseEndpoint(value: String): Pair<String, Int>? {
        val host: String
        val portText: String
        if (value.startsWith("[")) {
            val end = value.indexOf(']')
            if (end < 0 || end + 2 > value.length || value[end + 1] != ':') return null
            host = value.substring(1, end)
            portText = value.substring(end + 2)
        } else {
            val colon = value.lastIndexOf(':')
            if (colon <= 0 || colon == value.lastIndex) return null
            host = value.substring(0, colon)
            portText = value.substring(colon + 1)
        }
        val port = portText.toIntOrNull() ?: return null
        if (host.isBlank() || port !in 1..65535) return null
        return host to port
    }

    /**
     * Verify that the proxy speaks HTTP CONNECT and can reach Spotify's control plane. This is a
     * stronger check than `Socket.connect`: thousands of public proxies accept TCP but fail CONNECT.
     */
    private fun checkProxy(endpoint: Pair<String, Int>): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(endpoint.first, endpoint.second), 1_500)
                socket.soTimeout = 1_500
                socket.getOutputStream().write(
                    ("CONNECT spclient.wg.spotify.com:443 HTTP/1.1\r\n" +
                            "Host: spclient.wg.spotify.com:443\r\n" +
                            "Proxy-Connection: keep-alive\r\n\r\n").toByteArray()
                )
                socket.getOutputStream().flush()
                val reader = BufferedReader(socket.getInputStream().reader())
                val status = reader.readLine() ?: return false
                status.contains(" 200 ")
            }
        }.getOrDefault(false)
    }
}
