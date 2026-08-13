package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.domain.HttpRequestSettings
import com.abdulkus.essentialremap.domain.RequestMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRequestExecutorTest {
    @Test
    fun validRequestIsNormalizedAndSentToTransport() {
        var received: HttpRequestSettings? = null
        val executor = HttpRequestExecutor { config ->
            received = config
            HttpResult(204, "No Content")
        }

        val result = executor.execute(HttpRequestSettings(RequestMethod.POST, " http://192.168.1.5/hook "))

        assertEquals(HttpRequestSettings(RequestMethod.POST, "http://192.168.1.5/hook"), received)
        assertEquals(204, result.statusCode)
    }

    @Test
    fun invalidUrlDoesNotReachTransport() {
        var called = false
        val executor = HttpRequestExecutor {
            called = true
            HttpResult(200, "OK")
        }

        val result = executor.execute(HttpRequestSettings(RequestMethod.GET, "ftp://server/file"))

        assertEquals(-1, result.statusCode)
        assertTrue(result.message.contains("http://"))
        assertEquals(false, called)
    }

    @Test
    fun transportExceptionBecomesErrorResult() {
        val executor = HttpRequestExecutor { throw IllegalStateException("network down") }

        val result = executor.execute(HttpRequestSettings(RequestMethod.GET, "http://localhost/test"))

        assertEquals(-1, result.statusCode)
        assertEquals("network down", result.message)
    }
}
