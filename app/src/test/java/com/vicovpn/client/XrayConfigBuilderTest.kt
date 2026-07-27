package com.vicovpn.client

import com.vicovpn.client.model.ProxyProfile
import com.vicovpn.client.model.TransportSettings
import com.vicovpn.client.util.JsonCodec
import com.vicovpn.client.xray.XrayConfigBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigBuilderTest {
    @Test fun emitsTunAndFullProxyRoute() {
        val profile = ProxyProfile.Vmess("Test", "example.com", 443, "11111111-1111-4111-8111-111111111111", transport = TransportSettings(network="ws", security="tls", path="/ws"))
        val json = XrayConfigBuilder.build(profile, 10808)
        val root = JsonCodec.parseObject(json)
        val inbounds = root["inbounds"] as List<*>
        assertEquals("tun", (inbounds[0] as Map<*, *>)["protocol"])
        assertTrue(json.contains("\"outboundTag\":\"proxy\""))
        assertTrue(json.contains("\"protocol\":\"vmess\""))
    }
}
