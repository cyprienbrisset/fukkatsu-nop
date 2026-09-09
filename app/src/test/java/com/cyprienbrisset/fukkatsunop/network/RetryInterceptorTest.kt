package com.cyprienbrisset.fukkatsunop.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RetryInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor(maxRetries = 2, initialDelayMs = 0))
            .build()
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `success on first attempt`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val resp = client.newCall(Request.Builder().url(server.url("/")).build()).execute()
        assertEquals(200, resp.code)
        assertEquals(1, server.requestCount)
    }

    @Test fun `retries on 503 and succeeds`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200))
        val resp = client.newCall(Request.Builder().url(server.url("/")).build()).execute()
        assertEquals(200, resp.code)
        assertEquals(2, server.requestCount)
    }

    @Test fun `exhausts retries and returns last 5xx`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) }
        val resp = client.newCall(Request.Builder().url(server.url("/")).build()).execute()
        assertEquals(500, resp.code)
        assertEquals(3, server.requestCount)
    }

    @Test fun `does not retry on 4xx`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val resp = client.newCall(Request.Builder().url(server.url("/")).build()).execute()
        assertEquals(404, resp.code)
        assertEquals(1, server.requestCount)
    }
}
