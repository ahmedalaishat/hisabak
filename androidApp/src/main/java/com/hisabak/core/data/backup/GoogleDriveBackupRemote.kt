package com.hisabak.core.data.backup

import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupException
import com.hisabak.core.domain.backup.BackupRemote
import com.hisabak.core.domain.backup.RemoteBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * [BackupRemote] over the Drive v3 REST API, storing a single file in the **App Data Folder**
 * (hidden, app-private, per-account). Uses [GoogleDriveAuthorizer] for a per-call bearer token; a
 * 401/403 surfaces as [BackupError.AuthRequired], other failures as [BackupError.Network].
 */
class GoogleDriveBackupRemote(
    private val authorizer: DriveAuthorizer,
) : BackupRemote {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun findLatest(): RemoteBackup? = withContext(Dispatchers.IO) {
        val url = "$FILES_URL?spaces=appDataFolder&pageSize=1&orderBy=modifiedTime%20desc" +
            "&fields=files(id,modifiedTime,size)"
        val body = request("GET", URL(url))
        json.decodeFromString(FileListDto.serializer(), body).files.firstOrNull()?.let {
            RemoteBackup(
                id = it.id,
                modifiedAtMillis = it.modifiedTime?.let(Instant::parse)?.toEpochMilli() ?: 0L,
                sizeBytes = it.size?.toLongOrNull() ?: 0L,
            )
        }
    }

    override suspend fun upload(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val existingId = findLatest()?.id
        val id = existingId ?: createFile()
        request("PATCH", URL("$UPLOAD_URL/$id?uploadType=media"), bytes, "application/octet-stream")
        Unit
    }

    override suspend fun download(id: String): ByteArray = withContext(Dispatchers.IO) {
        requestBytes(URL("$FILES_URL/$id?alt=media"))
    }

    private fun createFile(): String {
        val metadata = """{"name":"$FILE_NAME","parents":["appDataFolder"]}"""
        val body = request("POST", URL(FILES_URL), metadata.toByteArray(), "application/json; charset=UTF-8")
        return json.decodeFromString(CreatedFileDto.serializer(), body).id
    }

    private fun request(method: String, url: URL, body: ByteArray? = null, contentType: String? = null): String {
        val conn = open(method, url, body, contentType)
        return try {
            conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: IOException) {
            throw mapError(conn)
        } finally {
            conn.disconnect()
        }
    }

    private fun requestBytes(url: URL): ByteArray {
        val conn = open("GET", url, null, null)
        return try {
            conn.inputStream.use { it.readBytes() }
        } catch (e: IOException) {
            throw mapError(conn)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(method: String, url: URL, body: ByteArray?, contentType: String?): HttpURLConnection {
        val token = kotlinx.coroutines.runBlocking { authorizer.accessToken() }
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 30_000
            readTimeout = 30_000
            if (body != null) {
                doOutput = true
                contentType?.let { setRequestProperty("Content-Type", it) }
                outputStream.use { it.write(body) }
            }
        }
    }

    private fun mapError(conn: HttpURLConnection): BackupException {
        val code = runCatching { conn.responseCode }.getOrDefault(-1)
        return BackupException(
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                BackupError.AuthRequired
            } else {
                BackupError.Network
            },
        )
    }

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
