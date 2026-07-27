package com.vicovpn.client

import com.vicovpn.client.model.ProxyProfile
import com.vicovpn.client.parser.ShareLinkParser
import com.vicovpn.client.util.JsonCodec
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLinkParserTest {
    @Test fun parsesVmessWebSocketTls() {
        val json = JsonCodec.stringify(mapOf(
            "v" to "2", "ps" to "Test", "add" to "example.com", "port" to "443",
            "id" to "11111111-1111-4111-8111-111111111111", "aid" to "0", "scy" to "auto",
            "net" to "ws", "type" to "none", "host" to "cdn.example.com", "path" to "/ray",
            "tls" to "tls", "sni" to "example.com", "fp" to "chrome"
        ))
        val uri = "vmess://" + Base64.getEncoder().withoutPadding().encodeToString(json.toByteArray())
        val profile = ShareLinkParser.parse(uri) as ProxyProfile.Vmess
        assertEquals("example.com", profile.address)
        assertEquals(443, profile.port)
        assertEquals("ws", profile.transport.network)
        assertEquals("tls", profile.transport.security)
    }

    @Test fun parsesVless() {
        val profile = ShareLinkParser.parse("vless://11111111-1111-4111-8111-111111111111@example.com:443?encryption=none&security=tls&type=ws&path=%2Fws&host=cdn.example.com#Germany") as ProxyProfile.Vless
        assertEquals("Germany", profile.name)
        assertEquals("/ws", profile.transport.path)
    }

    @Test fun rejectsInvalidPort() {
        assertTrue(runCatching { ShareLinkParser.parse("vless://11111111-1111-4111-8111-111111111111@example.com:70000") }.isFailure)
    }
    @Test fun rejectsUnsupportedTransport() {
        val uri = "vless://11111111-1111-4111-8111-111111111111@example.com:443?type=kcp"
        assertTrue(runCatching { ShareLinkParser.parse(uri) }.isFailure)
    }

    @Test fun rejectsShadowsocksPluginInsteadOfIgnoringIt() {
        val uri = "ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ@example.com:8388?plugin=v2ray-plugin#Test"
        assertTrue(runCatching { ShareLinkParser.parse(uri) }.isFailure)
    }

}
