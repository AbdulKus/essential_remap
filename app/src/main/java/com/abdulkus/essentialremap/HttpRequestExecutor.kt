package com.abdulkus.essentialremap

import com.abdulkus.essentialremap.domain.HttpRequestSettings
import com.abdulkus.essentialremap.domain.RequestMethod
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class HttpResult(val statusCode: Int, val message: String)

fun interface HttpTransport {
    fun execute(config: HttpRequestSettings): HttpResult
}

class HttpRequestExecutor(private val transport: HttpTransport = UrlConnectionTransport()) {
    fun execute(config: HttpRequestSettings): HttpResult {
        val normalized = config.copy(url = config.url.trim())
        validate(normalized)?.let { return HttpResult(-1, it) }
        return try {
            transport.execute(normalized)
        } catch (error: Exception) {
            HttpResult(-1, error.message ?: error.javaClass.simpleName)
        }
    }

    internal fun validate(config: HttpRequestSettings): String? {
        if (config.url.isBlank()) return "URL is empty"
        return try {
            val uri = URI(config.url)
            if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
                "URL must use http:// or https:// and include a host"
            } else {
                null
            }
        } catch (_: Exception) {
            "Invalid URL"
        }
    }
}

class UrlConnectionTransport : HttpTransport {
    override fun execute(config: HttpRequestSettings): HttpResult {
        val connection = URL(config.url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = config.method.name
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            if (config.method == RequestMethod.POST) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(0)
                connection.outputStream.use { }
            }
            val status = connection.responseCode
            HttpResult(status, connection.responseMessage.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}
