package com.hisabak.feature.sms.presentation.templates

import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.feature.sms.domain.template.DeleteSmsTemplateUseCase
import com.hisabak.feature.sms.domain.template.ObserveSmsTemplatesUseCase
import com.hisabak.feature.sms.domain.template.SetSmsTemplateEnabledUseCase
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeSmsTemplateRepository
import com.hisabak.testutil.MainDispatcherTest
import com.hisabak.testutil.parserTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SmsTemplatesViewModelTest : MainDispatcherTest() {

    private val analytics = FakeAnalytics()
    private val repo = FakeSmsTemplateRepository(
        listOf(
            parserTemplate(id = "generic", pattern = "spent {amount} at {brand}"),
            parserTemplate(id = "default-0", pattern = "Purchase of AED {amount} with {card} at {brand},", isDefault = true),
        ),
    )

    private fun viewModel() = SmsTemplatesViewModel(
        observeTemplates = ObserveSmsTemplatesUseCase(repo),
        setEnabled = SetSmsTemplateEnabledUseCase(repo, analytics),
        deleteTemplate = DeleteSmsTemplateUseCase(repo, analytics),
    )

    @Test
    fun `rows are specificity-ranked so the list shows matching precedence`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        // The default has far more literal anchor text than the generic user template.
        assertEquals(listOf("default-0", "generic"), vm.state.value.templates.map { it.id.value })
    }

    @Test
    fun `toggling updates the stored template`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsTemplatesIntent.ToggleEnabled(SmsTemplateId("default-0"), false))
        advanceUntilIdle()

        assertEquals(false, repo.current.first { it.id.value == "default-0" }.enabled)
    }

    @Test
    fun `delete is confirmed first and only removes on confirm`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsTemplatesIntent.DeleteRequested(SmsTemplateId("generic")))
        assertEquals(SmsTemplateId("generic"), vm.state.value.pendingDelete)
        assertEquals(2, repo.current.size)

        vm.onIntent(SmsTemplatesIntent.DeleteDismissed)
        assertNull(vm.state.value.pendingDelete)
        assertEquals(2, repo.current.size)

        vm.onIntent(SmsTemplatesIntent.DeleteRequested(SmsTemplateId("generic")))
        vm.onIntent(SmsTemplatesIntent.DeleteConfirmed)
        advanceUntilIdle()
        assertTrue(repo.current.none { it.id.value == "generic" })
    }

    @Test
    fun `deleting a default surfaces an error instead of removing it`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsTemplatesIntent.DeleteRequested(SmsTemplateId("default-0")))
        vm.onIntent(SmsTemplatesIntent.DeleteConfirmed)
        advanceUntilIdle()

        assertEquals(2, repo.current.size)
        assertTrue(vm.effect.value is SmsTemplatesEffect.Error)
    }
}
