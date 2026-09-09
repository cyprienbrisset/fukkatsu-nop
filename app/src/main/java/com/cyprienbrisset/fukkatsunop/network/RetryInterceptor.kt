package com.cyprienbrisset.fukkatsunop.network

import okhttp3.Interceptor
import okhttp3.Response

class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 1_000L,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var delayMs = initialDelayMs
        while (true) {
            val response = chain.proceed(chain.request())
            if (response.code < 500 || attempt >= maxRetries) return response
            response.close()
            attempt++
            if (delayMs > 0) Thread.sleep(delayMs)
            delayMs *= 2
        }
    }
}
