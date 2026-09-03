package com.hisabak.feature.sms.platform

import com.hisabak.core.data.backup.toByteArray
import com.hisabak.core.data.backup.toNSData
import com.hisabak.feature.sms.domain.ai.AiParsedSms
import com.hisabak.feature.sms.domain.ai.ParseRequestDto
import com.hisabak.feature.sms.domain.ai.ParseResponseDto
import com.hisabak.feature.sms.domain.ai.ParseServiceConfig
import com.hisabak.feature.sms.domain.ai.RemoteParseClient
import com.hisabak.feature.sms.domain.ai.RemoteParseRequest
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue

/**
 * [RemoteParseClient] over `NSURLSession`, mirroring `IosDriveBackupRemote` — same reasoning as the
 * Android client: no HTTP dependency for one JSON POST.
 *
 * This matters more on iOS than on Android: there is no SMS API here, so capture is already manual,
 * and Apple Foundation Models needs an iPhone 15 Pro or newer. On everything older this client is
 * the only way any AI parsing happens at all.
 */
@OptIn(ExperimentalForeignApi::class)
class IosRemoteParseClient(
    private val config: ParseServiceConfig,
) : RemoteParseClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val isConfigured: Boolean get() = config.isConfigured

    override suspend fun parse(request: RemoteParseRequest): AiParsedSms? {
        if (!isConfigured) return null
        val url = NSURL(string = "${config.baseUrl.trimEnd('/')}/v1/parse")
        val payload = json.encodeToString(
            ParseRequestDto.serializer(),
            ParseRequestDto(
                text = request.text,
                knownBrands = request.knownBrands,
                todayIso = request.todayIso,
                freeText = request.freeText,
            ),
        )
        val body = payload.encodeToByteArray()

        return suspendCancellableCoroutine { continuation ->
            val nsRequest = NSMutableURLRequest(uRL = url).apply {
                setHTTPMethod("POST")
                setValue("Bearer ${config.token}", forHTTPHeaderField = "Authorization")
                setValue("application/json", forHTTPHeaderField = "Content-Type")
                setHTTPBody(body.toNSData())
                setTimeoutInterval(TIMEOUT_SECONDS)
            }
            val task = NSURLSession.sharedSession.dataTaskWithRequest(nsRequest) { data, response, _ ->
                // Any failure is null — the caller falls back to the regex templates.
                val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
                val text = data?.toByteArray()?.decodeToString()
                val parsed = if (status == 200 && text != null) {
                    runCatching {
                        json.decodeFromString(ParseResponseDto.serializer(), text).toDomain()
                    }.getOrNull()
                } else {
                    null
                }
                continuation.resume(parsed)
            }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 20.0
    }
}
