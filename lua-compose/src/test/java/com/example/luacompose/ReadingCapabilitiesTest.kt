package com.example.luacompose

import java.net.InetAddress
import java.net.UnknownHostException
import java.net.Proxy
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ReadingCapabilitiesTest {
    @Test fun `title fetch logs validation and policy failures without input`() = runTest {
        val logs = mutableListOf<String>()
        val client = ReadingTitleClient(log = { logs += it }, resolve = { error("No network") })
        assertEquals("validation", client.fetch("http://example.com/private?secret=token").error)
        assertEquals(listOf("title: starting", "request: rejected invalid URL", "title: result=validation"), logs)
        logs.clear()
        assertEquals("policy", client.fetch("https://127.0.0.1/private").error)
        assertEquals(listOf("title: starting", "request: rejected unsafe address", "title: result=policy"), logs)
    }

    @Test fun `favicon links resolve safely with default fallback`() {
        val client = ReadingTitleClient { error("No network") }
        assertEquals("https://example.com/icons/site.png", client.faviconUrl("https://example.com/articles/a", "<head><link rel='shortcut icon' href='../icons/site.png'></head>"))
        assertEquals("https://cdn.example.com/icon.png", client.faviconUrl("https://example.com/a", "<LINK REL=icon HREF=//cdn.example.com/icon.png>"))
        assertEquals("https://example.com/favicon.ico", client.faviconUrl("https://example.com/a", "<!--<link rel='icon' href='/fake'>--><link rel='icon' href='http://unsafe.example/a'>"))
        assertEquals("https://example.com/favicon.ico", client.faviconUrl("https://example.com/a", "<link rel='' href=''>"))
    }

    @Test fun `saved favicon downloads are bounded and reject unsupported content`() {
        val client = ReadingTitleClient { error("No network") }
        val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        fun response(bytes: ByteArray, code: Int = 200) = Response.Builder()
            .request(Request.Builder().url("https://example.com/favicon.ico").build())
            .protocol(Protocol.HTTP_1_1).code(code).message("test")
            .body(bytes.toResponseBody()).build()
        assertArrayEquals(png, SavedFavicon.decode(client.faviconResponse(response(png)).body))
        assertEquals("unsupported", client.faviconResponse(response("<html>".toByteArray())).error)
        assertEquals("response_size", client.faviconResponse(response(ByteArray(32769))).error)
        assertEquals("http", client.faviconResponse(response(png, 302)).error)
        assertNull(SavedFavicon.decode("bad base64"))
        assertNull(SavedFavicon.decode("A".repeat(43693)))
        val ico = java.nio.ByteBuffer.allocate(22 + png.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        ico.putShort(0).putShort(1).putShort(1).putInt(0).putShort(1).putShort(32).putInt(png.size).putInt(22).put(png)
        assertArrayEquals(png, SavedFavicon.image(ico.array()))
        ico.putInt(18, Int.MAX_VALUE)
        assertNull(SavedFavicon.image(ico.array()))
    }

    @Test fun `normalization removes fragments credentials and default port without altering path or query`() {
        assertEquals("https://example.com/a/?x=%2F&x=2", ReadingCapabilities.normalize(" HTTPS://EXAMPLE.COM:443/a/?x=%2F&x=2#top "))
        for (path in listOf("", "/", "/a", "/a/", "/a%2fb", "/a/../b", "/?q=1", "/?q=2", "/?q=+", "/?q=%20", "/?", "//a")) {
            assertEquals("https://example.com$path", ReadingCapabilities.normalize("https://example.com$path"))
        }
        assertEquals("example.com", ReadingCapabilities.hostname("https://EXAMPLE.COM:443/path"))
        assertEquals("https://[2606:4700::1111]/", ReadingCapabilities.normalize("https://[2606:4700::1111]:443/"))
    }

    @Test fun `invalid URLs cannot reach fetch or browser`() = runTest {
        val client = ReadingTitleClient { error("Must not resolve invalid URL") }
        for (url in listOf("", "http://example.com", "https://example.com:8443", "https://user@example.com", "https://user:secret@example.com", "https://example.com\\bad", "https:///path", "https://example.com/a b", "https://example.com/" + "a".repeat(2049))) {
            assertThrows(Exception::class.java) { ReadingCapabilities.normalize(url) }
            assertEquals("validation", client.fetch(url).error)
        }
    }

    @Test fun `private reserved mapped and local addresses are never public destinations`() {
        val unsafe = listOf(
            "0.0.0.0", "0.1.2.3", "10.1.2.3", "127.0.0.1", "100.64.0.1", "100.127.255.255", "169.254.1.2",
            "172.16.0.1", "172.31.255.255", "192.168.1.1", "192.0.0.1", "192.0.2.1", "192.88.99.1",
            "198.18.0.1", "198.19.255.255", "198.51.100.1", "203.0.113.1", "224.0.0.1", "255.255.255.255",
            "::", "::1", "::ffff:127.0.0.1", "fc00::1", "fe80::1", "ff00::1", "2001:db8::1", "2001::1", "2002::1", "3fff::1",
        )
        for (address in unsafe) assertFalse(address, ReadingCapabilities.publicAddress(InetAddress.getByName(address)))
        for (address in listOf("8.8.8.8", "1.1.1.1", "172.15.0.1", "172.32.0.1", "100.63.0.1", "100.128.0.1", "2606:4700::1111", "2001:4860:4860::8888")) {
            assertTrue(address, ReadingCapabilities.publicAddress(InetAddress.getByName(address)))
        }
    }

    @Test fun `literal private targets fail before opening a connection`() = runTest {
        val client = ReadingTitleClient { error("No DNS for a literal") }
        for (host in listOf("127.0.0.1", "10.0.0.1", "[::1]", "[::ffff:127.0.0.1]", "[fe80::1]")) {
            assertEquals("policy", client.fetch("https://$host/").error)
        }
    }

    @Test fun `DNS validates every resolved address and revalidates changed answers`() {
        var answers = listOf(InetAddress.getByName("8.8.8.8"))
        var calls = 0
        val titleClient = ReadingTitleClient { calls++; answers }
        assertEquals(answers, titleClient.client.dns.lookup("example.com"))
        assertEquals(1, calls)
        answers = listOf(InetAddress.getByName("127.0.0.1"))
        assertThrows(UnknownHostException::class.java) { titleClient.client.dns.lookup("example.com") }
        answers = listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("10.0.0.1"))
        assertThrows(UnknownHostException::class.java) { titleClient.client.dns.lookup("example.com") }
        answers = emptyList()
        assertThrows(UnknownHostException::class.java) { titleClient.client.dns.lookup("example.com") }
        assertFalse(titleClient.client.followRedirects)
        assertFalse(titleClient.client.followSslRedirects)
        assertEquals(Proxy.NO_PROXY, titleClient.client.proxy)
        assertSame(CookieJar.NO_COOKIES, titleClient.client.cookieJar)
        assertEquals(30_000, titleClient.client.callTimeoutMillis)
        assertEquals(30_000, titleClient.client.connectTimeoutMillis)
        assertEquals(30_000, titleClient.client.readTimeoutMillis)
        assertEquals(1, titleClient.client.networkInterceptors.size)
    }

    @Test fun `actual connection destination is checked before HTTP proceeds`() {
        val client = ReadingTitleClient { error("No real DNS") }.client
        val request = Request.Builder().url("https://example.com/").build()
        val address = Address("example.com", 443, client.dns, client.socketFactory, client.sslSocketFactory,
            client.hostnameVerifier, client.certificatePinner, client.proxyAuthenticator, Proxy.NO_PROXY,
            client.protocols, client.connectionSpecs, client.proxySelector)
        for ((ip, peer) in listOf("127.0.0.1" to "127.0.0.1", "10.0.0.1" to "10.0.0.1", "8.8.8.8" to "127.0.0.1", "8.8.8.8" to "8.8.8.8")) {
            var proceeded = false
            val connection = object : Connection {
                override fun route() = Route(address, Proxy.NO_PROXY, java.net.InetSocketAddress(InetAddress.getByName(ip), 443))
                override fun socket() = object : java.net.Socket() {
                    override fun getInetAddress(): InetAddress = InetAddress.getByName(peer)
                }
                override fun handshake(): Handshake? = null
                override fun protocol() = Protocol.HTTP_1_1
            }
            val chain = object : Interceptor.Chain {
                override fun request() = request
                override fun connection() = connection
                override fun call() = client.newCall(request)
                override fun proceed(request: Request): Response {
                    proceeded = true
                    return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("test")
                        .body("".toResponseBody()).build()
                }
                override fun connectTimeoutMillis() = 30_000
                override fun readTimeoutMillis() = 30_000
                override fun writeTimeoutMillis() = 30_000
                override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
                override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
                override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            }
            if (ip == "8.8.8.8" && peer == "8.8.8.8") client.networkInterceptors.single().intercept(chain).close()
            else assertThrows(java.io.IOException::class.java) { client.networkInterceptors.single().intercept(chain) }
            assertEquals(ip == "8.8.8.8" && peer == "8.8.8.8", proceeded)
        }
    }

    @Test fun `storage JSON preserves null records and distinguishes arrays from objects`() {
        val json = LuaJson()
        val document = json.decode("{\"articles\":[null,{},[]]}")
        val array = document.get("articles")
        assertTrue(array is JsonArrayTable)
        assertEquals(3, array.length())
        assertFalse(array.get(1).isnil())
        assertFalse(array.get(2) is JsonArrayTable)
        assertTrue(array.get(3) is JsonArrayTable)
        assertTrue(json.encode(document).contains("[null,"))
    }

    @Test fun `bounded HTML title decoding collapses whitespace without fetching linked content`() {
        val response = HtmlTitle.parse("<html><head><!--<title>fake</title>--><script>var x='<title>fake</title>';</script><TITLE>  A &amp; B\n &nbsp; &#x1F600; &#65; &quot;test&quot; </TITLE></head><body>Body ignored<img src='secret'></body></html>")
        assertNull(response.error)
        assertEquals("A & B 😀 A \"test\"", response.body)
        assertEquals("İ Title", HtmlTitle.parse("<head><title>İ Title</title></head>").body)
        assertEquals("< > '\"", HtmlTitle.parse("<head><title>&lt; &gt; &apos;&quot;</title></head>").body)
        assertNull(HtmlTitle.parse("<head><title>${"😀".repeat(300)}</title></head>").error)
    }

    @Test fun `title attributes and large heads do not hide a valid title`() {
        for (attributes in listOf("data-rh=\"true\"", "class='page-title'", "data-label='a > b'", "DATA-RH=\"true\" lang=en")) {
            val html = "<html><head><title $attributes>Android Developers – Medium</title>" +
                "<style>${" ".repeat(70 * 1024)}</style></head><body>Ignored</body></html>"
            val result = HtmlTitle.parse(html)
            assertNull(result.error)
            assertEquals("Android Developers – Medium", result.body)
        }
        assertEquals("Late title", HtmlTitle.parse("<head><style>${" ".repeat(70 * 1024)}</style><title data-rh='true'>Late title</title></head>").body)
        assertNotNull(HtmlTitle.parse("<head><title data-rh='true>Broken</title></head>").error)
        assertNotNull(HtmlTitle.parse("<head><title data-rh='true'>First</title><title>Second</title></head>").error)
    }

    @Test fun `missing title diagnostics identify parser rejection without page text`() {
        val cases = mapOf(
            "<head></head>" to "head_without_title",
            "<head><title>Private title</title><body>Private note" to "body_before_head_closed",
            "<head><script>private data" to "unclosed_script",
            "<head><!--private data" to "unclosed_comment",
            "<title>Private title</title>" to "title_outside_head",
            "<head><title data-label='private data" to "unclosed_tag",
            "<head><title>Private title</title>" to "head_not_closed",
        )
        for ((html, reason) in cases) {
            val logs = mutableListOf<String>()
            assertEquals("missing_title", HtmlTitle.parse(html) { logs += it }.error)
            assertTrue(logs.any { it.contains("rejected=$reason;") })
            assertTrue(logs.none { it.contains("Private") || it.contains("private") })
        }
    }

    @Test fun `missing malformed unknown entities and oversized titles retain fallback`() {
        for (html in listOf("", "<head></head>", "<head><title>Unclosed</head>", "<head><title></title></head>", "<head><title><b>nested</b></title></head>", "<head><title>A</title><title>B</title></head>", "<head><title>&unknown;</title></head>", "<head><title>&#0;</title></head>", "<head><title>&#xD800;</title></head>", "<head><title>${"x".repeat(301)}</title></head>", "<title>Outside head</title>", "<head><script><title>Fake</title></head>", "<head><!--<title>Fake</title></head>")) {
            assertNotNull(html.take(60), HtmlTitle.parse(html).error)
        }
        assertEquals("response_size", HtmlTitle.parse("x".repeat(LuaHttpClient.MAX_RESPONSE_BYTES + 1)).error)
    }

    @Test fun `HTTP errors redirects unsupported encoding and response size are structured failures`() {
        val client = ReadingTitleClient { error("No actual network") }
        val html = "<head><title>Title</title></head>"
        fun response(code: Int = 200, type: String = "text/html; charset=utf-8", text: String = html) = Response.Builder()
            .request(Request.Builder().url("https://example.com").build()).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .body(text.toResponseBody(type.toMediaType())).build()
        assertEquals("Title", client.titleResponse(response()).body)
        for (code in listOf(301, 302, 307, 308)) assertEquals("redirect", client.titleResponse(response(code)).error)
        for (code in listOf(400, 404, 500)) assertEquals("http", client.titleResponse(response(code)).error)
        for (type in listOf("application/json", "image/png", "text/plain", "text/html; charset=iso-8859-1", "text/html; charset=unknown")) {
            assertEquals("unsupported", client.titleResponse(response(type = type)).error)
        }
        assertEquals("response_size", client.titleResponse(response(text = "x".repeat(LuaHttpClient.MAX_RESPONSE_BYTES + 1))).error)
        val boundary = html + " ".repeat(LuaHttpClient.MAX_RESPONSE_BYTES - html.length)
        assertEquals("Title", client.titleResponse(response(text = boundary)).body)
    }
}
