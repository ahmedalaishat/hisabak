package com.hisabak.feature.sms.data.parser

import com.hisabak.core.common.DomainResult
import com.hisabak.feature.sms.domain.SmsParserTemplate
import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.feature.sms.domain.SmsTemplateRepository
import com.hisabak.testutil.FakeSmsTemplateRepository
import com.hisabak.testutil.parserTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest

class ObservingSmsTemplateDetectorTest {

    private val tabbyBody = "You spent AED 35.00 at HARDEES. Your available Tabby Card limit is now AED 8,342.27."

    // Same pattern the other app-scope collaborators use in tests: Unconfined makes the
    // repository flow collect (and every re-emission) synchronous.
    private val appScope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun `defaults answer before the first emission`() = runTest {
        // A repo whose flow genuinely never emits — only the eagerly-compiled defaults can
        // answer. (A FakeSmsTemplateRepository emits an empty list immediately, which on a
        // loaded CI runner raced this test's detect() and wiped the snapshot — flaky.)
        val silent = object : SmsTemplateRepository {
            override fun observeAll(): Flow<List<SmsParserTemplate>> = flow { awaitCancellation() }
            override suspend fun upsert(template: SmsParserTemplate) = DomainResult.Success(Unit)
            override suspend fun delete(id: SmsTemplateId) = DomainResult.Success(Unit)
        }
        val detector = ObservingSmsTemplateDetector(silent, CoroutineScope(Dispatchers.Default))

        // The 10 shipped defaults are compiled eagerly — no template gap on cold start.
        assertNotNull(detector.detect("Purchase of AED 12.00 with card ending 1234 at CAFE, DUBAI"))
    }

    @Test
    fun `a stored template takes effect once observed`() = runTest {
        val repo = FakeSmsTemplateRepository(
            listOf(parserTemplate(pattern = "You spent AED {amount} at {brand}. Your available Tabby")),
        )
        val detector = ObservingSmsTemplateDetector(repo, appScope)

        val template = detector.detect(tabbyBody)

        assertNotNull(template)
        assertEquals("35.00", template.fields["amount"])
        assertEquals("HARDEES", template.fields["brand"])
    }

    @Test
    fun `disabling a template excludes it immediately`() = runTest {
        val stored = parserTemplate(pattern = "You spent AED {amount} at {brand}. Your available Tabby")
        val repo = FakeSmsTemplateRepository(listOf(stored))
        val detector = ObservingSmsTemplateDetector(repo, appScope)
        assertNotNull(detector.detect(tabbyBody))

        repo.upsert(stored.copy(enabled = false))

        assertNull(detector.detect(tabbyBody))
    }

    @Test
    fun `a generic template with a junk amount capture falls through instead of poisoning the parse`() = runTest {
        // The over-generic template would capture amount="You spent AED 35.00" (not a number);
        // the specific one must still win the message even though both are stored.
        val repo = FakeSmsTemplateRepository(
            listOf(
                parserTemplate(id = "generic", pattern = "{amount} at {brand}", createdAtMillis = 0L),
                parserTemplate(
                    id = "specific",
                    pattern = "You spent AED {amount} at {brand}. Your available Tabby",
                    createdAtMillis = 1L,
                ),
            ),
        )
        val detector = ObservingSmsTemplateDetector(repo, appScope)

        val template = detector.detect(tabbyBody)

        assertEquals("35.00", template?.fields?.get("amount"))

        // With ONLY the generic template stored, a mis-anchored match yields no parse at all
        // rather than junk fields.
        repo.delete(SmsTemplateId("specific"))
        assertNull(detector.detect(tabbyBody))
    }
}
