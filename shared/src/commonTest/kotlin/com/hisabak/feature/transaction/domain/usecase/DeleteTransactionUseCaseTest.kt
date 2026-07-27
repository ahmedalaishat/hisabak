package com.hisabak.feature.transaction.domain.usecase

import com.hisabak.core.common.DomainResult
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.smsMessage
import com.hisabak.testutil.transaction
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeleteTransactionUseCaseTest {

    private val txRepo = FakeTransactionRepository()
    private val smsRepo = FakeSmsRepository()
    private val useCase = DeleteTransactionUseCase(txRepo, smsRepo)

    @Test
    fun `deletes the transaction`() = runTest {
        txRepo.emit(listOf(transaction(id = "t1"), transaction(id = "t2")))

        val result = useCase(TransactionId("t1"))

        assertTrue(result is DomainResult.Success)
        assertEquals(listOf("t2"), txRepo.current.map { it.id.value })
    }

    @Test
    fun `clears the link on the source message but keeps the message`() = runTest {
        txRepo.emit(listOf(transaction(id = "t1", sourceSmsId = "s1")))
        smsRepo.upsert(smsMessage(id = "s1", body = "Purchase").copy(transactionId = TransactionId("t1")))

        useCase(TransactionId("t1"))

        val message = smsRepo.current.single()
        assertNull(message.transactionId, "a deleted transaction must leave no dangling link")
        assertEquals("Purchase", message.body)
    }

    @Test
    fun `leaves messages linked to other transactions alone`() = runTest {
        txRepo.emit(listOf(transaction(id = "t1"), transaction(id = "t2")))
        smsRepo.upsert(smsMessage(id = "s1", body = "One").copy(transactionId = TransactionId("t1")))
        smsRepo.upsert(smsMessage(id = "s2", body = "Two").copy(transactionId = TransactionId("t2")))

        useCase(TransactionId("t1"))

        assertNull(smsRepo.current.first { it.id.value == "s1" }.transactionId)
        assertEquals(TransactionId("t2"), smsRepo.current.first { it.id.value == "s2" }.transactionId)
    }

    @Test
    fun `deleting a manual transaction touches no messages`() = runTest {
        txRepo.emit(listOf(transaction(id = "t1")))
        smsRepo.upsert(smsMessage(id = "s1", body = "Other").copy(transactionId = TransactionId("t9")))

        useCase(TransactionId("t1"))

        assertEquals(TransactionId("t9"), smsRepo.current.single().transactionId)
    }
}
