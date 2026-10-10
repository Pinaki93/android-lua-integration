package com.example.luaplayground

import com.example.luacompose.LuaHttpClient
import io.ktor.client.HttpClient
import io.ktor.http.*

internal fun httpForScript(script: String, client: HttpClient?): LuaHttpClient? {
    if (script != "okhttp-contributors.luac" || client == null) return null
    return LuaHttpClient(client, ::contributorsRequest)
}

internal fun contributorsRequest(method: HttpMethod, url: Url): Boolean {
    if (method != HttpMethod.Get || url.protocol != URLProtocol.HTTPS || url.host != "api.github.com" ||
        url.port != 443 || url.encodedPath != "/repos/lysine-dev/okhttp/contributors") return false
    val parameters = url.parameters
    if (parameters.names() != setOf("page", "per_page", "anon")) return false
    if (parameters.names().any { parameters.getAll(it)?.size != 1 }) return false
    val page = parameters["page"] ?: return false
    return Regex("[1-9][0-9]*").matches(page) && page.toIntOrNull() != null &&
        parameters["per_page"] == "100" && parameters["anon"] == "1"
}
