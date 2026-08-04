package com.hisabak.feature.sms.data.parser

import com.hisabak.feature.sms.domain.DefaultSmsTemplates
import com.hisabak.feature.sms.domain.SmsTemplate
import com.hisabak.feature.sms.domain.SmsTemplateDetector
import com.hisabak.feature.sms.domain.SmsTemplateRepository
import com.hisabak.feature.sms.domain.rankTemplates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * DB-backed [SmsTemplateDetector]: keeps a compiled snapshot of the enabled templates,
 * specificity-ranked, refreshed whenever the table changes — template edits take effect
 * immediately, while [detect] stays synchronous for the per-keystroke inbox draft preview.
 * Until the first emission the shipped defaults answer, so early captures never see an empty
 * template set — but capture paths that can race process start (the iOS Shortcut intent) call
 * [awaitReady] so user templates are in the snapshot before they detect.
 */
class ObservingSmsTemplateDetector(
    repository: SmsTemplateRepository,
    scope: CoroutineScope,
) : SmsTemplateDetector {

    private val compiled = MutableStateFlow(RegexSmsTemplateDetector(DefaultSmsTemplates.patterns))
    private val firstLoad = CompletableDeferred<Unit>()

    init {
        scope.launch {
            repository.observeAll().collect { templates ->
                val ranked = rankTemplates(templates.filter { it.enabled })
                compiled.value = RegexSmsTemplateDetector(ranked.map { it.pattern })
                firstLoad.complete(Unit)
            }
        }
    }

    override fun detect(body: String): SmsTemplate? = compiled.value.detect(body)

    override suspend fun awaitReady() = firstLoad.await()
}
