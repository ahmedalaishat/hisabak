package com.hisabak.feature.sms.domain.template

import com.hisabak.core.common.DomainResult
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.FakeSmsTemplateRepository
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.aed
import com.hisabak.testutil.parserTemplate
import com.hisabak.testutil.smsMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SmsTemplateUseCasesTest {

    private val clock = TestClock()
    private val analytics = FakeAnalytics()
    private val tabby =
        "You spent AED 35.00 at HARDEES-WTC MALL. Your available Tabby Card limit is now AED 8,342.27."

    private fun spanOf(sample: String, text: String, role: TagRole): TagSpan {
        val start = sample.indexOf(text)
        return TagSpan(role, start, start + text.length)
    }

    @Test
    fun `saving a tagged sample stores the derived pattern with the sample kept`() = runTest {
        val repo = FakeSmsTemplateRepository()
        val save = SaveSmsTemplateUseCase(repo, clock, analytics)

        val result = save(
            existingId = null,
            sample = tabby,
            spans = listOf(
                spanOf(tabby, "35.00", TagRole.AMOUNT),
                spanOf(tabby, "HARDEES-WTC MALL", TagRole.BRAND),
                spanOf(tabby, "8,342.27", TagRole.SKIP),
            ),
        )

        assertTrue(result is DomainResult.Success)
        val stored = repo.current.single()
        assertEquals(tabby, stored.sampleBody)
        assertEquals(false, stored.isDefault)
        assertTrue(stored.pattern.contains("You spent AED {amount} at {brand}."))
        assertEquals(listOf("sms_template_created"), analytics.names())
    }

    @Test
    fun `a degenerate sample is rejected for insufficient anchor`() = runTest {
        val save = SaveSmsTemplateUseCase(FakeSmsTemplateRepository(), clock, analytics)
        val sample = "100 at noon"

        val error = save.validate(
            sample,
            listOf(spanOf(sample, "100", TagRole.AMOUNT), spanOf(sample, "noon", TagRole.BRAND)),
        )

        assertEquals(TemplateValidationError.InsufficientAnchor, error)
    }

    @Test
    fun `an untagged amount and a non-numeric amount are rejected`() = runTest {
        val save = SaveSmsTemplateUseCase(FakeSmsTemplateRepository(), clock, analytics)

        assertEquals(
            TemplateValidationError.MissingAmount,
            save.validate(tabby, listOf(spanOf(tabby, "HARDEES-WTC MALL", TagRole.BRAND))),
        )
        assertEquals(
            TemplateValidationError.InvalidAmount,
            save.validate(tabby, listOf(spanOf(tabby, "HARDEES-WTC MALL", TagRole.AMOUNT))),
        )
    }

    @Test
    fun `defaults can be disabled but not edited or deleted`() = runTest {
        val default = parserTemplate(id = "default-0", pattern = "Purchase of AED {amount} at {brand},", isDefault = true)
        val repo = FakeSmsTemplateRepository(listOf(default))

        val save = SaveSmsTemplateUseCase(repo, clock, analytics)
        val editResult = save(SmsTemplateId("default-0"), tabby, listOf(spanOf(tabby, "35.00", TagRole.AMOUNT)))
        assertTrue(editResult is DomainResult.Failure)

        val delete = DeleteSmsTemplateUseCase(repo, analytics)
        assertTrue(delete(SmsTemplateId("default-0")) is DomainResult.Failure)
        assertEquals(1, repo.current.size)

        val toggle = SetSmsTemplateEnabledUseCase(repo, analytics)
        assertTrue(toggle(SmsTemplateId("default-0"), false) is DomainResult.Success)
        assertEquals(false, repo.current.single().enabled)
    }

    @Test
    fun `re-teaching an identical format reuses the stored template instead of duplicating`() = runTest {
        val repo = FakeSmsTemplateRepository()
        val save = SaveSmsTemplateUseCase(repo, clock, analytics)
        val spans = listOf(
            spanOf(tabby, "35.00", TagRole.AMOUNT),
            spanOf(tabby, "HARDEES-WTC MALL", TagRole.BRAND),
            spanOf(tabby, "8,342.27", TagRole.SKIP),
        )
        val first = (save(null, tabby, spans) as DomainResult.Success).value

        // Same format taught again — e.g. "Create template" tapped on a second Tabby message
        // whose tags derive the identical pattern.
        val second = save(null, tabby, spans)

        assertTrue(second is DomainResult.Success)
        assertEquals(first.id, second.value.id) // the existing row, not a new one
        assertEquals(1, repo.current.size)
    }

    @Test
    fun `editing a user template keeps its enabled state and creation time`() = runTest {
        val original = parserTemplate(
            id = "mine",
            pattern = "You spent AED {amount} at {brand}. Your available Tabby",
            sampleBody = tabby,
            enabled = false,
            createdAtMillis = 42L,
        )
        val repo = FakeSmsTemplateRepository(listOf(original))
        val save = SaveSmsTemplateUseCase(repo, clock, analytics)

        val result = save(
            SmsTemplateId("mine"),
            tabby,
            listOf(spanOf(tabby, "35.00", TagRole.AMOUNT), spanOf(tabby, "HARDEES-WTC MALL", TagRole.BRAND)),
        )

        assertTrue(result is DomainResult.Success)
        val updated = repo.current.single()
        assertEquals(false, updated.enabled) // a disabled template doesn't sneak back on
        assertEquals(42L, updated.createdAt.toEpochMilliseconds())
    }

    @Test
    fun `deleting a user template works`() = runTest {
        val repo = FakeSmsTemplateRepository(listOf(parserTemplate(id = "mine", pattern = "x {amount}")))
        val delete = DeleteSmsTemplateUseCase(repo, analytics)

        assertTrue(delete(SmsTemplateId("mine")) is DomainResult.Success)
        assertTrue(repo.current.isEmpty())
        assertEquals(listOf("sms_template_deleted"), analytics.names())
    }

    @Test
    fun `preview counts matches and flags messages that already parse differently`() = runTest {
        val smsRepo = FakeSmsRepository()
        // Two other Tabby messages: one unparsed, one already parsed with the correct amount,
        // one parsed with a DIFFERENT amount than the draft would extract (conflict).
        smsRepo.upsert(smsMessage(id = "m1", body = "You spent AED 6.50 at YANGO. Your available Tabby Cash balance is now AED 59.39."))
        smsRepo.upsert(
            smsMessage(id = "m2", body = "You spent AED 20.00 at LULU. Your available Tabby Card limit is now AED 1.00.")
                .copy(parsed = ParsedSmsData(brandName = "LULU", amount = aed(2_000), occurredAt = clock.now())),
        )
        smsRepo.upsert(
            smsMessage(id = "m3", body = "You spent AED 9.00 at CAFE. Your available Tabby Card limit is now AED 2.00.")
                .copy(parsed = ParsedSmsData(brandName = "CAFE", amount = aed(555), occurredAt = clock.now())),
        )
        // The sample itself is stored too — it must not count.
        smsRepo.upsert(smsMessage(id = "m0", body = tabby))
        val preview = PreviewSmsTemplateUseCase(smsRepo)

        val result = preview("You spent AED {amount} at {brand}. Your available Tabby", tabby)

        assertEquals(3, result.matches)
        assertEquals(1, result.conflicts)
    }
}
