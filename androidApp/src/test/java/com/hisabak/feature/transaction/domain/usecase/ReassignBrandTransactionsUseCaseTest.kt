package com.hisabak.feature.transaction.domain.usecase

import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.transaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReassignBrandTransactionsUseCaseTest {

    @Test
    fun `moves every transaction from one brand onto another`() = runTest {
        val repo = FakeTransactionRepository(
            listOf(
                transaction(id = "t1", brandId = "old"),
                transaction(id = "t2", brandId = "old"),
                transaction(id = "t3", brandId = "keep"),
            ),
        )
        val useCase = ReassignBrandTransactionsUseCase(repo)

        useCase(from = BrandId("old"), to = BrandId("new"))

        assertEquals(2, repo.current.count { it.brandId == BrandId("new") })
        assertEquals(1, repo.current.count { it.brandId == BrandId("keep") })
        assertEquals(0, repo.current.count { it.brandId == BrandId("old") })
    }
}
