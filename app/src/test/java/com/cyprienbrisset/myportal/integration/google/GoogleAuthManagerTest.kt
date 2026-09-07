package com.cyprienbrisset.myportal.integration.google

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleAuthManagerTest {

    @Test fun parseDeviceCodeResponse_extractsAllFields() {
        val raw = """
            {
              "device_code": "DEV123",
              "user_code": "ABCD-EFGH",
              "verification_url": "https://www.google.com/device",
              "expires_in": 1800,
              "interval": 5
            }
        """.trimIndent()
        val result = parseDeviceCodeResponse(raw)
        assertEquals("DEV123",                     result.deviceCode)
        assertEquals("ABCD-EFGH",                  result.userCode)
        assertEquals("https://www.google.com/device", result.verificationUrl)
        assertEquals(1800,                          result.expiresIn)
        assertEquals(5,                             result.interval)
    }
}
