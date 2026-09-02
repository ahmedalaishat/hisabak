package com.hisabak.feature.sms.platform

import com.hisabak.feature.sms.domain.ai.AiParsedSms
import com.hisabak.feature.sms.domain.ai.ParseRequestDto
import com.hisabak.feature.sms.domain.ai.ParseResponseDto
import com.hisabak.feature.sms.domain.ai.ParseServiceConfig
import com.hisabak.feature.sms.domain.ai.RemoteParseClient
import com.hisabak.feature.sms.domain.ai.RemoteParseRequest
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * [RemoteParseClient] over `HttpURLConnection`, mirroring `GoogleDriveBackupRemote` — the app has
 * no HTTP client dependency and one JSON POST does not justify adding one.
 *
 * Every failure is `null`, never an exception: the caller degrades to the regex templates, and a
 * parse service outage must not break capture.
 */
class HttpRemoteParseClient(
    private val config: ParseServiceConfig,
) : RemoteParseClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val isConfigured: Boolean get() = config.isConfigured

    override suspend fun parse(request: RemoteParseRequest): AiParsedSms? =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext null
            runCatching { post(request) }.getOrNull()
        }

    private fun post(request: RemoteParseRequest): AiParsedSms? {
        val connection = (URL("${config.baseUrl.trimEnd('/')}/v1/parse").openConnection()
            as HttpURLConnection).apply {
            requestMethod = "POST"
            // Short: this runs while the user watches a spinner on the inbox row.
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${config.token}")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            val payload = ParseRequestDto(
                text = request.text,
                knownBrands = request.knownBrands,
                todayIso = request.todayIso,
                freeText = request.freeText,
            )
            connection.outputStream.use {
                it.write(json.encodeToString(ParseRequestDto.serializer(), payload).toByteArray())
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return json.decodeFromString(ParseResponseDto.serializer(), body).toDomain()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
    }
}
