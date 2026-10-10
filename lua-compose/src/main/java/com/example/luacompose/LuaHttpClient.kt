package com.example.luacompose

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.luaj.vm2.*

/** Native policy sees the final URL, including query parameters. It must opt in explicitly. */
class LuaHttpClient(client: HttpClient, private val permits: (HttpMethod, Url) -> Boolean) {
    private val client = client.config { followRedirects = false; expectSuccess = false }
    internal val nullValue: LuaValue = LuaValue.userdataOf(Any())
    private val json = LuaJson(nullValue)

    internal data class Request(val url: Url, val method: HttpMethod, val headers: Map<String, String>, val body: String?)
    data class Response(
        val status: Int? = null,
        val headers: Map<String, String> = emptyMap(),
        val body: String = "",
        val error: String? = null,
    )
    internal class Failure(val code: String) : RuntimeException(code)

    internal fun parse(value: LuaValue): Request {
        try {
            if (!value.istable()) throw Failure("validation")
            val table = value.checktable()
            val allowed = setOf("url", "method", "query", "headers", "body", "json")
            if (table.keys().any { it.type() != LuaValue.TSTRING || it.tojstring() !in allowed }) throw Failure("validation")
            fun string(field: String, default: String? = null): String {
                val item = table.get(field)
                if (item.isnil() && default != null) return default
                if (item.type() != LuaValue.TSTRING) throw Failure("validation")
                return item.tojstring()
            }
            val method = HttpMethod(string("method", "GET"))
            if (method.value !in setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")) throw Failure("validation")
            val raw = string("url")
            val uri = java.net.URI(raw)
            if (!uri.isAbsolute || uri.rawUserInfo != null || uri.rawFragment != null || uri.rawPath.contains('%') || uri.rawPath.split('/').any { it == "." || it == ".." }) throw Failure("policy")
            val builder = URLBuilder(raw)
            if (builder.protocol != URLProtocol.HTTP && builder.protocol != URLProtocol.HTTPS) throw Failure("policy")
            val query = pairs(table.get("query"))
            query.forEach { (key, item) -> builder.parameters.append(key, item) }
            val headers = pairs(table.get("headers"))
            headers.forEach { (key, item) ->
                if (!Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+").matches(key) || item.any { it.code < 32 || it.code == 127 }) throw Failure("validation")
                if (key.lowercase() in setOf("host", "connection", "content-length", "transfer-encoding", "upgrade", "proxy-authorization", "proxy-connection", "trailer", "te")) throw Failure("policy")
            }
            if (!table.get("body").isnil() && !table.get("json").isnil()) throw Failure("validation")
            val body = when {
                !table.get("json").isnil() -> json.encode(table.get("json"))
                !table.get("body").isnil() -> string("body")
                else -> null
            }
            // HttpURLConnection can turn GET with an output body into POST.
            if (body != null && method in setOf(HttpMethod.Get, HttpMethod.Head)) throw Failure("validation")
            if (body != null && body.toByteArray(Charsets.UTF_8).size > MAX_REQUEST_BYTES) throw Failure("request_size")
            val url = builder.build()
            if (!permits(method, url)) throw Failure("policy")
            return Request(url, method, if (!table.get("json").isnil()) headers + ("Content-Type" to "application/json") else headers, body)
        } catch (failure: Failure) { throw failure }
        catch (_: Exception) { throw Failure("validation") }
    }

    private fun pairs(value: LuaValue): Map<String, String> {
        if (value.isnil()) return emptyMap()
        if (!value.istable()) throw Failure("validation")
        return value.checktable().keys().associate { key ->
            val item = value.get(key)
            if (key.type() != LuaValue.TSTRING || item.type() != LuaValue.TSTRING) throw Failure("validation")
            key.tojstring() to item.tojstring()
        }
    }

    internal suspend fun execute(request: Request): Response = try {
        withTimeout(30_000) {
            client.prepareRequest(request.url) {
                method = request.method
                request.headers.forEach { (key, value) -> headers.append(key, value) }
                request.body?.let { setBody(it) }
            }.execute { response ->
                val headers = response.headers.entries().associate { it.key.lowercase() to it.value.joinToString(", ") }
                val channel = response.bodyAsChannel()
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = channel.readAvailable(buffer, 0, minOf(buffer.size, MAX_RESPONSE_BYTES - output.size() + 1))
                    if (count < 0) break
                    if (output.size() + count > MAX_RESPONSE_BYTES) return@execute Response(response.status.value, headers, error = "response_size")
                    output.write(buffer, 0, count)
                }
                val body = output.toString("UTF-8")
                val isJson = headers["content-type"]?.substringBefore(';')?.trim()?.lowercase()?.let { it == "application/json" || it.endsWith("+json") } == true
                val error = if (isJson && response.status.value != 204) try { json.decode(body); null } catch (_: Exception) { "invalid_json" } else null
                Response(response.status.value, headers, body, error)
            }
        }
    } catch (_: TimeoutCancellationException) { Response(error = "timeout") }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: io.ktor.client.plugins.HttpRequestTimeoutException) { Response(error = "timeout") }
    catch (_: java.net.SocketTimeoutException) { Response(error = "timeout") }
    catch (_: io.ktor.client.network.sockets.ConnectTimeoutException) { Response(error = "timeout") }
    catch (_: io.ktor.client.network.sockets.SocketTimeoutException) { Response(error = "timeout") }
    catch (_: Exception) { Response(error = "transport") }

    internal fun table(response: Response): LuaTable = LuaTable().apply {
        response.status?.let { set("status", it) }
        set("body", response.body)
        set("headers", LuaTable().apply { response.headers.forEach { (key, value) -> set(key, value) } })
        response.error?.let { set("error", LuaTable().apply { set("code", it) }) }
        val contentType = response.headers["content-type"]?.substringBefore(';')?.trim()?.lowercase()
        if (response.error == null && response.status != 204 && (contentType == "application/json" || contentType?.endsWith("+json") == true)) set("json", json.decode(response.body))
    }

    companion object {
        const val MAX_REQUEST_BYTES = 256 * 1024
        const val MAX_RESPONSE_BYTES = 1024 * 1024
    }
}
