package com.hisabak.core.platform.remote

import com.hisabak.core.domain.remote.ServiceConfig
import com.hisabak.core.domain.remote.ServiceTransport
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [ServiceTransport] over `HttpURLConnection`, mirroring `GoogleDriveBackupRemote` — the app has no
 * HTTP client dependency and a couple of JSON POSTs do not justify adding one.
 *
 * Every failure is `null`, never an exception: the callers degrade (regex templates, the
 * deterministic review), and a service outage must not break capture or show an error screen.
 */
class HttpServiceTransport(
    private val config: ServiceConfig,
) : ServiceTransport {

    override val isConfigured: Boolean get() = config.isConfigured

    override suspend fun postJson(path: String, body: String, timeoutMs: Int, headers: Map<String, String>): String? =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext null
            runCatching { post(path, body, timeoutMs, headers) }.getOrNull()
        }

    private fun post(path: String, body: String, timeoutMs: Int, headers: Map<String, String>): String? {
        val connection = (URL("${config.baseUrl.trimEnd('/')}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${config.token}")
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
