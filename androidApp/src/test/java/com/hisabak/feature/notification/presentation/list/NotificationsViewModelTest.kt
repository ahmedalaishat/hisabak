package com.hisabak.feature.notification.presentation.list

import com.hisabak.feature.notification.domain.NotificationId
import com.hisabak.testutil.FakeNotificationRepository
import com.hisabak.testutil.MainDispatcherRule
import com.hisabak.testutil.notification
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeNotificationRepository()

    private suspend fun seed() {
        repo.create(notification(id = "n1", isRead = false))
        repo.create(notification(id = "n2", isRead = true))
    }

    @Test
    fun `maps notifications to rows and clears loading`() = runTest {
        seed()
        val vm = NotificationsViewModel(repo)
        advanceUntilIdle()

        assertEquals(2, vm.state.value.rows.size)
        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.hasUnread)
    }

    @Test
    fun `rows carry the notification type for per-type styling`() = runTest {
        repo.create(notification(id = "limit", type = com.hisabak.feature.notification.domain.Notification.TYPE_CATEGORY_LIMIT))
        repo.create(notification(id = "tx", type = com.hisabak.feature.notification.domain.Notification.TYPE_TRANSACTION_RECORDED))
        val vm = NotificationsViewModel(repo)
        advanceUntilIdle()

        val byId = vm.state.value.rows.associateBy { it.id.value }
        assertEquals(com.hisabak.feature.notification.domain.Notification.TYPE_CATEGORY_LIMIT, byId.getValue("limit").type)
        assertEquals(com.hisabak.feature.notification.domain.Notification.TYPE_TRANSACTION_RECORDED, byId.getValue("tx").type)
    }

    @Test
    fun `marking one as read updates that row`() = runTest {
        seed()
        val vm = NotificationsViewModel(repo)
        advanceUntilIdle()

        vm.onIntent(NotificationsIntent.MarkRead(NotificationId("n1")))
        advanceUntilIdle()

        assertTrue(vm.state.value.rows.first { it.id == NotificationId("n1") }.isRead)
    }

    @Test
    fun `mark all read clears the unread flag`() = runTest {
        seed()
        val vm = NotificationsViewModel(repo)
        advanceUntilIdle()

        vm.onIntent(NotificationsIntent.MarkAllRead)
        advanceUntilIdle()

        assertFalse(vm.state.value.hasUnread)
    }

    @Test
    fun `dismiss removes the row`() = runTest {
        seed()
        val vm = NotificationsViewModel(repo)
        advanceUntilIdle()

        vm.onIntent(NotificationsIntent.Dismiss(NotificationId("n1")))
        advanceUntilIdle()

        assertEquals(listOf(NotificationId("n2")), vm.state.value.rows.map { it.id })
    }
}
