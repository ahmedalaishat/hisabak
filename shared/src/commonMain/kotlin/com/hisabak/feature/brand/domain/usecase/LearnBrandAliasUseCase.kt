package com.hisabak.feature.brand.domain.usecase

import com.hisabak.core.common.DomainResult
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.BrandRepository

/**
 * Remembers that a bank's merchant string means the brand the user just linked it to.
 *
 * The counterpart to template synthesis: confirming an AI parse teaches the regex engine how to
 * *extract* the merchant, and this teaches it how to *resolve* what it extracted. Without it the
 * learned template captures the raw text ("GOOGLEYOUTUBE,US") while the transaction points at the
 * canonical brand ("Youtube video"), and the next match — silent, on the background path —
 * creates a duplicate brand with no category.
 *
 * A no-op when [rawName] already resolves to [brandId] on its own: most merchant strings do, and
 * an alias that only restates the existing rule is noise in a table the user may one day see.
 */
class LearnBrandAliasUseCase(
    private val repository: BrandRepository,
    private val resolve: ResolveBrandUseCase,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(rawName: String?, brandId: BrandId) {
        val raw = rawName?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (resolve(raw)?.id == brandId) return

        if (repository.linkAlias(raw, brandId) is DomainResult.Success) {
            analytics.log(AnalyticsEvent.BrandAliasLearned)
        }
    }
}
