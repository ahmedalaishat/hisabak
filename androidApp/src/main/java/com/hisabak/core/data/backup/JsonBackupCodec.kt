package com.hisabak.core.data.backup

import com.hisabak.core.domain.backup.BackupCodec
import com.hisabak.core.domain.backup.BackupEnvelope
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class JsonBackupCodec : BackupCodec {
    // ignoreUnknownKeys: a newer backup's extra fields decode without crashing the older app.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun encode(envelope: BackupEnvelope): ByteArray =
        json.encodeToString(BackupEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8)

    override fun decode(bytes: ByteArray): BackupEnvelope =
        try {
            json.decodeFromString(BackupEnvelope.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw BackupException(BackupError.Corrupt)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupError.Corrupt)
        }
}
