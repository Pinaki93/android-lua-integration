package com.example.luacompose

import java.net.InetAddress
import java.net.URI
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import okio.ByteString.Companion.toByteString
import kotlin.coroutines.resume

/** Only installed for the bundled reading-list script. No headers or raw HTML cross into Lua. */
class ReadingCapabilities(
    val fetchTitle: suspend (String) -> LuaHttpClient.Response,
    val openOriginal: (String) -> Unit,
    val identifier: () -> String = { UUID.randomUUID().toString() },
    val now: () -> Long = { System.currentTimeMillis() },
    val clipboardText: () -> String? = { null },
    val log: (String) -> Unit = {},
) {
    companion object {
        fun normalize(input: String): String {
            val raw = input.trim()
            require(raw.codePointCount(0, raw.length) <= 2048 && raw.none { it.isWhitespace() || it.isISOControl() })
            val uri = URI(raw)
            require(uri.scheme.equals("https", true) && uri.host != null && uri.rawUserInfo == null && uri.port in listOf(-1, 443))
            val host = uri.host.lowercase()
            require(host.isNotEmpty())
            return "https://$host" + (uri.rawPath ?: "") + (uri.rawQuery?.let { "?$it" } ?: "")
        }

        fun hostname(url: String): String = URI(normalize(url)).host

        internal fun publicAddress(address: InetAddress): Boolean {
            val bytes = address.address.map { it.toInt() and 255 }
            if (bytes.size == 16) {
                // Only global unicast; exclude special-use ranges within 2000::/3.
                return bytes[0] in 0x20..0x3f &&
                    !(bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] < 2) &&
                    !(bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == 0xb8) &&
                    !(bytes[0] == 0x20 && bytes[1] == 0x02) &&
                    !(bytes[0] == 0x3f && bytes[1] == 0xff)
            }
            val a = bytes[0]; val b = bytes[1]; val c = bytes[2]
            return a !in listOf(0, 10, 127) && a < 224 &&
                !(a == 100 && b in 64..127) && !(a == 169 && b == 254) &&
                !(a == 172 && b in 16..31) && !(a == 192 && (b == 168 || b == 0 || b == 88 && c == 99 || b == 2)) &&
                !(a == 198 && (b in 18..19 || b == 51 && c == 100)) && !(a == 203 && b == 0 && c == 113)
        }
    }
}

class ReadingTitleClient(
    private val log: (String) -> Unit = {},
    resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
) {
    internal val client = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false).followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = resolve(hostname)
                if (addresses.isEmpty() || !addresses.all(ReadingCapabilities::publicAddress)) throw java.net.UnknownHostException("Unsafe destination")
                // OkHttp connects to these very addresses, without a second DNS lookup.
                return addresses
            }
        })
        .addNetworkInterceptor { chain ->
            val connection = chain.connection() ?: throw IOException("Missing connection")
            val routed = connection.route().socketAddress.address
            val connected = connection.socket().inetAddress
            if (routed?.let(ReadingCapabilities::publicAddress) != true || connected?.let(ReadingCapabilities::publicAddress) != true) throw IOException("Unsafe destination")
            chain.proceed(chain.request())
        }.build()

    suspend fun fetch(input: String): LuaHttpClient.Response {
        log("title: starting")
        val title = request(input, ::titleResponse)
        log("title: result=${title.error ?: "success"}")
        val iconUrl = title.headers["favicon-url"] ?: return title
        log("favicon: starting; title delivery waits for favicon")
        val icon = request(iconUrl, ::faviconResponse).body.takeIf { it.isNotEmpty() }
        log("favicon: available=${icon != null}; delivering title")
        return title.copy(headers = emptyMap(), favicon = icon)
    }

    private suspend fun request(input: String, parse: (Response) -> LuaHttpClient.Response): LuaHttpClient.Response {
        val url = try { ReadingCapabilities.normalize(input) } catch (_: Exception) {
            log("request: rejected invalid URL")
            return LuaHttpClient.Response(error = "validation")
        }
        // Numeric literals may bypass OkHttp's DNS adapter.
        val host = ReadingCapabilities.hostname(url).removeSurrounding("[", "]")
        if (host.contains(':') || Regex("[0-9.]+").matches(host)) {
            val safe = runCatching { ReadingCapabilities.publicAddress(InetAddress.getByName(host)) }.getOrDefault(false)
            if (!safe) {
                log("request: rejected unsafe address")
                return LuaHttpClient.Response(error = "policy")
            }
        }
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url).get().build())
            continuation.invokeOnCancellation { log("request: cancelled"); call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    log("request: failure=${e.javaClass.simpleName}")
                    continuation.resume(LuaHttpClient.Response(error = if (e is java.io.InterruptedIOException) "timeout" else "transport"))
                }
                override fun onResponse(call: Call, response: Response) {
                    log("request: HTTP status=${response.code}")
                    val result = try { parse(response) } catch (_: Exception) {
                        response.close()
                        LuaHttpClient.Response(error = "transport")
                    }
                    log("request: parsed=${result.error ?: "success"}")
                    continuation.resume(result)
                }
            })
        }
    }
    internal fun faviconUrl(page: String, html: String): String {
        val head = html.take(65536).substringBefore("</head>", html.take(65536))
            .replace(Regex("<!--.*?-->|<script\\b.*?</script>|<style\\b.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        for (tag in Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(head)) {
            val attributes = Regex("([a-zA-Z]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))").findAll(tag.value)
                .associate { it.groupValues[1].lowercase() to it.groupValues.drop(2).firstOrNull { value -> value.isNotEmpty() }.orEmpty() }
            if (attributes["rel"]?.lowercase()?.split(Regex("\\s+"))?.contains("icon") != true) continue
            val href = attributes["href"] ?: continue
            val resolved = runCatching { ReadingCapabilities.normalize(URI(page).resolve(href.replace("&amp;", "&")).toString()) }.getOrNull()
            if (resolved != null) return resolved
        }
        return URI(page).resolve("/favicon.ico").toString()
    }

    internal fun faviconResponse(response: Response): LuaHttpClient.Response = try {
        response.use {
            if (!it.isSuccessful) return LuaHttpClient.Response(error = "http")
            val source = it.body?.source() ?: return LuaHttpClient.Response(error = "unsupported")
            source.request(32769)
            if (source.buffer.size > 32768) return LuaHttpClient.Response(error = "response_size")
            val bytes = source.readByteArray()
            val image = SavedFavicon.image(bytes) ?: return LuaHttpClient.Response(error = "unsupported")
            LuaHttpClient.Response(body = image.toByteString().base64())
        }
    } catch (_: Exception) { LuaHttpClient.Response(error = "transport") }

    internal fun titleResponse(response: Response): LuaHttpClient.Response = try {
        response.use {
            when {
                it.code in 300..399 -> LuaHttpClient.Response(error = "redirect")
                !it.isSuccessful -> LuaHttpClient.Response(error = "http")
                else -> {
                    val body = it.body ?: throw IOException("Missing body")
                    val type = body.contentType()
                    val declaredCharset = type?.toString()?.contains(Regex("charset\\s*=", RegexOption.IGNORE_CASE)) == true
                    val charset = if (declaredCharset) type?.charset() else Charsets.UTF_8
                    if (type?.type != "text" || type.subtype != "html" || charset !in listOf(Charsets.UTF_8, Charsets.US_ASCII)) {
                        LuaHttpClient.Response(error = "unsupported")
                    } else {
                        val source = body.source()
                        source.request((LuaHttpClient.MAX_RESPONSE_BYTES + 1).toLong())
                        if (source.buffer.size > LuaHttpClient.MAX_RESPONSE_BYTES) LuaHttpClient.Response(error = "response_size")
                        else {
                            val bytes = source.readByteArray()
                            val html = try {
                                charset!!.newDecoder()
                                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                            } catch (_: java.nio.charset.CharacterCodingException) {
                                return LuaHttpClient.Response(error = "malformed")
                            }
                            HtmlTitle.parse(html, log).copy(headers = mapOf("favicon-url" to faviconUrl(it.request.url.toString(), html)))
                        }
                    }
                }
            }
        }
    } catch (_: Exception) { LuaHttpClient.Response(error = "transport") }

}

internal object HtmlTitle {
    private fun tagEnd(html: String, start: Int): Int {
        var quote: Char? = null
        for (index in start until html.length) {
            val character = html[index]
            if (quote != null) {
                if (character == quote) quote = null
            } else when (character) {
                '\'', '"' -> quote = character
                '>' -> return index
            }
        }
        return -1
    }

    private fun titleText(html: String, log: (String) -> Unit): String? {
        fun rejected(reason: String, position: Int): String? {
            log("title parser: rejected=$reason; offset=$position; characters=${html.length}")
            return null
        }
        // The response is already bounded to 1 MiB; styles can push the head past 64 KiB.
        val head = html
        val lower = buildString(head.length) { head.forEach { append(if (it in 'A'..'Z') it.lowercaseChar() else it) } }
        var position = 0
        var inHead = false
        var title: String? = null
        while (position < head.length) {
            val start = head.indexOf('<', position)
            if (start < 0) return rejected("no_more_tags", position)
            if (head.startsWith("<!--", start)) {
                val end = head.indexOf("-->", start + 4)
                if (end < 0) return rejected("unclosed_comment", start)
                position = end + 3
                continue
            }
            val end = tagEnd(head, start + 1)
            if (end < 0) return rejected("unclosed_tag", start)
            val tag = head.substring(start + 1, end).trim().lowercase(java.util.Locale.ROOT)
            val name = tag.takeWhile { !it.isWhitespace() }
            position = end + 1
            when (name) {
                "head" -> { if (inHead) return rejected("duplicate_head", start); inHead = true }
                "/head" -> return if (inHead && title != null) title else rejected("head_without_title", start)
                "body", "/html" -> return rejected("body_before_head_closed", start)
                "script", "style" -> {
                    val close = lower.indexOf("</$name", position)
                    if (close < 0) return rejected("unclosed_$name", start)
                    val closeEnd = head.indexOf('>', close)
                    if (closeEnd < 0 || head.substring(close + 2, closeEnd).trim().lowercase(java.util.Locale.ROOT) != name) return rejected("invalid_${name}_closing_tag", close)
                    position = closeEnd + 1
                }
                "title" -> {
                    if (!inHead) return rejected("title_outside_head", start)
                    if (title != null) return rejected("duplicate_title", start)
                    val close = lower.indexOf("</title", position)
                    if (close < 0) return rejected("unclosed_$name", start)
                    val closeEnd = head.indexOf('>', close)
                    if (closeEnd < 0 || head.substring(close + 2, closeEnd).trim().lowercase(java.util.Locale.ROOT) != "title") return rejected("invalid_title_closing_tag", close)
                    title = head.substring(position, close)
                    position = closeEnd + 1
                }
            }
        }
        return rejected("head_not_closed", position)
    }

    fun parse(html: String, log: (String) -> Unit = {}): LuaHttpClient.Response {
        if (html.toByteArray(Charsets.UTF_8).size > LuaHttpClient.MAX_RESPONSE_BYTES) return LuaHttpClient.Response(error = "response_size")
        log("title parser: version=2; characters=${html.length}")
        val raw = titleText(html, log) ?: return LuaHttpClient.Response(error = "missing_title")
        if ('<' in raw || raw.length > 4096) return LuaHttpClient.Response(error = "malformed")
        var unsupported = false
        val decoded = raw.replace(Regex("&([^;\\s]+);")) { match ->
            val entity = match.groupValues[1]
            val named = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")
            named[entity] ?: run {
                val point = when {
                    entity.startsWith("#x", true) -> entity.drop(2).toIntOrNull(16)
                    entity.startsWith('#') -> entity.drop(1).toIntOrNull()
                    else -> null
                }
                if (point == null || point !in 1..0x10ffff || point in 0xd800..0xdfff) {
                    unsupported = true; ""
                } else String(Character.toChars(point))
            }
        }.replace(Regex("[\\s\\u00a0]+"), " ").trim()
        return if (unsupported || decoded.isEmpty() || decoded.codePointCount(0, decoded.length) > 300)
            LuaHttpClient.Response(error = "malformed") else LuaHttpClient.Response(body = decoded)
    }
}
