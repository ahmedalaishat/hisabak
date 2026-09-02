package com.hisabak.feature.sms.domain.template

import com.hisabak.core.common.DomainResult
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.FakeSmsTemplateRepository
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.aed
import com.hisabak.testutil.parserTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class SynthesizeTemplateUseCaseTest {

    private val clock = TestClock()
    private val analytics = FakeAnalytics()
    private val templateRepo = FakeSmsTemplateRepository()
    private val smsRepo = FakeSmsRepository()

    private fun synthesize() = SynthesizeTemplateUseCase(
        repository = templateRepo,
        saveTemplate = SaveSmsTemplateUseCase(templateRepo, clock, analytics),
        previewTemplate = PreviewSmsTemplateUseCase(smsRepo),
        clock = clock,
        analytics = analytics,
    )

    private val body = "Purchase of AED 125.50 with card 1234 at CARREFOUR MALL completed"
    private val pattern = "Purchase of AED {amount} with card {ignore} at {brand} completed"

    @Test
    fun `a valid pattern is installed and flagged as learned`() = runTest {
        val result = synthesize()(body, pattern)

        val template = assertNotNull((result as DomainResult.Success).value)
        assertTrue(template.derivedByAi)
        assertEquals(pattern, template.pattern)
        assertEquals(body, template.sampleBody)
        assertTrue(template.enabled)
        assertTrue(!template.isDefault)
        assertEquals(listOf(template), templateRepo.current)
        assertTrue("sms_template_synthesized" in analytics.names())
    }

    @Test
    fun `a pattern that cannot be rebuilt from the body is declined`() = runTest {
        val result = synthesize()(body, "Totally different {amount} text {brand} here")

        assertNull((result as DomainResult.Success).value)
        assertTrue(templateRepo.current.isEmpty())
        assertTrue("sms_template_synthesis_skipped" in analytics.names())
    }

    @Test
    fun `a pattern with too little anchor text is declined`() = runTest {
        val thin = "{amount} at {brand}"
        val result = synthesize()("125.50 at NOON", thin)

        assertNull((result as DomainResult.Success).value)
        assertTrue(templateRepo.current.isEmpty())
    }

    @Test
    fun `an already stored pattern is not installed twice`() = runTest {
        templateRepo.upsert(parserTemplate(id = "existing", pattern = pattern))

        val result = synthesize()(body, pattern)

        assertNull((result as DomainResult.Success).value)
        assertEquals(1, templateRepo.current.size)
    }

    @Test
    fun `a pattern that would misread an already parsed message is declined`() = runTest {
        // Stored with a known amount that the candidate would extract differently.
        smsRepo.upsert(
            SmsMessage(
                id = SmsMessageId.new(),
                body = "Purchase of AED 999.00 with card 5555 at SPINNEYS completed",
                receivedAt = Instant.fromEpochMilliseconds(0),
                parsed = ParsedSmsData("SPINNEYS", aed(1_00), Instant.fromEpochMilliseconds(0)),
            ),
        )

        val result = synthesize()(body, pattern)

        assertNull((result as DomainResult.Success).value)
        assertTrue(templateRepo.current.isEmpty())
    }
}
