package com.hisabak.core.data.backup

import com.hisabak.core.domain.backup.BackupEnvelope
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupException
import com.hisabak.testutil.sampleBackupData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonBackupCodecTest {

    private val codec = JsonBackupCodec()
    private val envelope = BackupEnvelope(
        formatVersion = 1,
        schemaVersion = 2,
        appVersionCode = 8,
        createdAtMillis = 1_700_000_000_000,
        data = sampleBackupData(),
    )

    @Test
    fun `encode then decode round-trips`() {
        assertEquals(envelope, codec.decode(codec.encode(envelope)))
    }

    @Test
    fun `unknown fields are tolerated for forward-compat`() {
        val json = codec.encode(envelope).toString(Charsets.UTF_8)
            .replaceFirst("{", "{\"futureField\":123,")
        assertEquals(envelope, codec.decode(json.toByteArray()))
    }

    @Test
    fun `garbage fails with Corrupt`() {
        val e = assertThrows(BackupException::class.java) { codec.decode("not json".toByteArray()) }
        assertEquals(BackupError.Corrupt, e.error)
    }
}
