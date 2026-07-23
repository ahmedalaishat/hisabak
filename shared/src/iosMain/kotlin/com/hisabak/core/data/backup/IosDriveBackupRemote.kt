package com.hisabak.core.data.backup

import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupException
import com.hisabak.core.domain.backup.BackupRemote
import com.hisabak.core.domain.backup.RemoteBackup
import kotlin.coroutines.resume
import kotlin.time.Instant
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURLResponse
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue

/**
 * [BackupRemote] over the Drive v3 REST API via NSURLSession — the iOS counterpart of
 * androidApp's `GoogleDriveBackupRemote` (same App Data Folder file, same error mapping:
 * 401/403 → AuthRequired, anything else → Network).
 */
class IosDriveBackupRemote(
    private val authorizer: DriveAuthorizer,
) : BackupRemote {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun findLatest(): RemoteBackup? {
        val url = "$FILES_URL?spaces=appDataFolder&pageSize=1&orderBy=modifiedTime%20desc" +
            "&fields=files(id,modifiedTime,size)"
        val body = request("GET", url).decodeUtf8()
        return json.decodeFromString(FileListDto.serializer(), body).files.firstOrNull()?.let {
            RemoteBackup(
                id = it.id,
                modifiedAtMillis = it.modifiedTime?.let(Instant::parse)?.toEpochMilliseconds() ?: 0L,
                sizeBytes = it.size?.toLongOrNull() ?: 0L,
            )
        }
    }

    override suspend fun upload(bytes: ByteArray) {
        val id = findLatest()?.id ?: createFile()
        request("PATCH", "$UPLOAD_URL/$id?uploadType=media", bytes, "application/octet-stream")
    }

    override suspend fun download(id: String): ByteArray =
        request("GET", "$FILES_URL/$id?alt=media")

    private suspend fun createFile(): String {
        val metadata = """{"name":"$FILE_NAME","parents":["appDataFolder"]}"""
        val body = request(
            "POST", FILES_URL, metadata.encodeToByteArray(), "application/json; charset=UTF-8",
        ).decodeUtf8()
        return json.decodeFromString(CreatedFileDto.serializer(), body).id
    }

    private suspend fun request(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null,
    ): ByteArray {
        val token = authorizer.accessToken()
        val request = NSMutableURLRequest(uRL = NSURL(string = url)).apply {
            setHTTPMethod(method)
            setValue("Bearer $token", forHTTPHeaderField = "Authorization")
            setTimeoutInterval(30.0)
            if (body != null) {
                contentType?.let { setValue(it, forHTTPHeaderField = "Content-Type") }
                setHTTPBody(body.toNSData())
            }
        }
        return suspendCancellableCoroutine { cont ->
            val task = NSURLSession.sharedSession.dataTaskWithRequest(
                request,
            ) { data: NSData?, response: NSURLResponse?, error: NSError? ->
                val code = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: -1
                when {
                    error != null || code < 200 || code >= 300 -> cont.resume(
                        Result.failure(
                            BackupException(
                                if (code == 401 || code == 403) BackupError.AuthRequired else BackupError.Network,
                            ),
                        ),
                    )
                    else -> cont.resume(Result.success(data?.toByteArray() ?: ByteArray(0)))
                }
            }
            cont.invokeOnCancellation { task.cancel() }
            task.resume()
        }.getOrThrow()
    }

    private fun ByteArray.decodeUtf8(): String = decodeToString()

    @Serializable
    private data class FileListDto(val files: List<DriveFileDto> = emptyList())

    @Serializable
    private data class DriveFileDto(val id: String, val modifiedTime: String? = null, val size: String? = null)

    @Serializable
    private data class CreatedFileDto(val id: String)

    private companion object {
        const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        const val FILE_NAME = "hisabak-backup.bak"
    }
}
