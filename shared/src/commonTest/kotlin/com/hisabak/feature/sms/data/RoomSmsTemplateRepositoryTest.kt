package com.hisabak.feature.sms.data

import com.hisabak.feature.sms.data.local.SmsTemplateDao
import com.hisabak.feature.sms.data.local.SmsTemplateEntity
import com.hisabak.testutil.TestClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoomSmsTemplateRepositoryTest {

    private class InMemoryDao : SmsTemplateDao {
        val items = MutableStateFlow<List<SmsTemplateEntity>>(emptyList())
        override fun observeAll(): Flow<List<SmsTemplateEntity>> = items
        override suspend fun count(): Long = items.value.size.toLong()
        override suspend fun upsert(template: SmsTemplateEntity) {
            items.value = items.value.filterNot { it.id == template.id } + template
        }
        override suspend fun upsertAll(templates: List<SmsTemplateEntity>) = templates.forEach { upsert(it) }
        override suspend fun deleteById(id: String) {
            items.value = items.value.filterNot { it.id == id }
        }
        override suspend fun getAllForBackup(): List<SmsTemplateEntity> = items.value
        override suspend fun deleteAll() {
            items.value = emptyList()
        }
    }

    @Test
    fun `an empty table is seeded with the 10 defaults on first read`() = runTest {
        val dao = InMemoryDao()
        val repo = RoomSmsTemplateRepository(dao, TestClock())

        val templates = repo.observeAll().first()

        assertEquals(10, templates.size)
        assertTrue(templates.all { it.isDefault && it.enabled })
        assertEquals("default-0", templates.first().id.value)
    }

    @Test
    fun `wiping the table under a live collection re-seeds the defaults`() = runTest {
        // A restore of a pre-template backup runs deleteAll while the detector's collection is
        // live — parsing must come back with the defaults, not go dark until restart.
        val dao = InMemoryDao()
        val repo = RoomSmsTemplateRepository(dao, TestClock())
        val emissions = mutableListOf<Int>()
        val job = launch { repo.observeAll().collect { emissions.add(it.size) } }
        runCurrent()
        assertEquals(10, emissions.last())

        dao.deleteAll()
        runCurrent()

        assertEquals(10, dao.items.value.size)
        assertEquals(10, emissions.last())
        job.cancel()
    }

    @Test
    fun `seeding is idempotent across collections`() = runTest {
        val dao = InMemoryDao()
        val repo = RoomSmsTemplateRepository(dao, TestClock())

        repo.observeAll().first()
        repo.observeAll().first()

        assertEquals(10, dao.items.value.size)
    }
}
