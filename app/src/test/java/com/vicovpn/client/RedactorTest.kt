package com.vicovpn.client

import com.vicovpn.client.util.Redactor
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactorTest {
    @Test fun removesCredentials() {
        val output = Redactor.redact("vless://11111111-1111-4111-8111-111111111111@example.com:443")
        assertFalse(output.contains("11111111"))
        assertFalse(output.contains("vless://"))
    }
}
