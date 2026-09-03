package com.hisabak.feature.brand.domain.usecase

import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.BrandRepository
import com.hisabak.feature.brand.domain.canonicalizeBrand

/**
 * The single answer to "which brand does this merchant string mean?" — used by every path that
 * resolves text a machine extracted, so a regex template and an AI parse can't disagree.
 *
 * Ladder: a learned alias first (the user confirmed that mapping, so it outranks any guess),
 * then [canonicalizeBrand] over the usage-ordered brand names. Returns null when the string
 * names no brand the user has.
 */
class ResolveBrandUseCase(
    private val repository: BrandRepository,
) {
    suspend operator fun invoke(name: String): Brand? {
        val normalized = name.trim()
        if (normalized.isEmpty()) return null

        repository.findByAlias(normalized)?.let { return it }

        val known = repository.namesByUsage(RESOLUTION_BRAND_LIMIT)
        val canonical = canonicalizeBrand(normalized, known)
        // canonicalizeBrand returns a stored name on a match and the input otherwise, so an
        // unmatched string simply finds nothing here. The lookup must be exact: containment would
        // let "Youtube" land on "Youtube video" and then create a second "Youtube" anyway.
        return repository.findByExactName(canonical)
    }
}

/**
 * Every brand, in practice: resolution has to consider all of them, unlike the AI prompt's
 * top-50 hint list, where the cap is a token budget.
 */
private const val RESOLUTION_BRAND_LIMIT = 10_000
