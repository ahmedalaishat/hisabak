package com.hisabak.core.platform.remote

import com.hisabak.core.data.backup.toByteArray
import com.hisabak.core.data.backup.toNSData
import com.hisabak.core.domain.remote.ServiceConfig
import com.hisabak.core.domain.remote.ServiceTransport
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue

/**
 * [ServiceTransport] over `NSURLSession`, mirroring `IosDriveBackupRemote` — same reasoning as the
 * Android transport: no HTTP dependency for a couple of JSON POSTs.
 *
 * This matters more on iOS than on Android: there is no SMS API here, so capture is already manual,
 * and Apple Foundation Models needs an iPhone 15 Pro or newer. On everything older this transport
 * is the only way any AI feature happens at all.
 */
@OptIn(ExperimentalForeignApi::class)
class IosServiceTransport(
    private val config: ServiceConfig,
) : ServiceTransport {

    override val isConfigured: Boolean get() = config.isConfigured

    override suspend fun postJson(path: String, body: String, timeoutMs: Int, headers: Map<String, String>): String? {
        if (!isConfigured) return null
        val url = NSURL(string = "${config.baseUrl.trimEnd('/')}$path")
        val bytes = body.encodeToByteArray()

        return suspendCancellableCoroutine { continuation ->
            val nsRequest = NSMutableURLRequest(uRL = url).apply {
                setHTTPMethod("POST")
                setValue("Bearer ${config.token}", forHTTPHeaderField = "Authorization")
                setValue("application/json", forHTTPHeaderField = "Content-Type")
                headers.forEach { (name, value) -> setValue(value, forHTTPHeaderField = name) }
                setHTTPBody(bytes.toNSData())
                setTimeoutInterval(timeoutMs / 1000.0)
            }
            val task = NSURLSession.sharedSession.dataTaskWithRequest(nsRequest) { data, response, _ ->
                // Any failure is null — the caller degrades to its offline path.
                val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
                val text = data?.toByteArray()?.decodeToString()
                continuation.resume(if (status == 200) text else null)
            }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }
}
