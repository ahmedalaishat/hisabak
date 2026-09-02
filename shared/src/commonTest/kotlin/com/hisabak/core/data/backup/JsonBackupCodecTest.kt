package com.hisabak.core.data.backup

import com.hisabak.core.domain.backup.BackupEnvelope
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupException
import com.hisabak.testutil.sampleBackupData
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.Test

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
        val json = codec.encode(envelope).decodeToString()
            .replaceFirst("{", "{\"futureField\":123,")
        assertEquals(envelope, codec.decode(json.encodeToByteArray()))
    }

    @Test
    fun `a template backed up before the learned flag existed restores as hand-made`() {
        val json = codec.encode(envelope).decodeToString().replace(",\"derivedByAi\":true", "")
            .replace(",\"derivedByAi\":false", "")

        val restored = codec.decode(json.encodeToByteArray())

        assertEquals(envelope.data.smsTemplates.size, restored.data.smsTemplates.size)
        assertTrue(restored.data.smsTemplates.none { it.derivedByAi })
    }

    @Test
    fun `garbage fails with Corrupt`() {
        val e = assertFailsWith<BackupException> { codec.decode("not json".encodeToByteArray()) }
        assertEquals(BackupError.Corrupt, e.error)
    }
}
