package com.hisabak.nav

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationStateTest {

    private fun state() = NavigationState(
        startRoute = DashboardKey,
        topLevelRoutes = listOf(DashboardKey, TransactionsKey, SettingsKey),
    )

    private fun NavigationState.stack(key: NavKey): List<NavKey> =
        backStacks.getValue(key).toList()

    @Test
    fun `navigating to a top-level route switches the tab without touching stacks`() {
        val state = state()
        val navigator = Navigator(state)

        navigator.navigate(TransactionsKey)

        assertEquals(TransactionsKey, state.topLevelRoute)
        assertEquals(listOf(TransactionsKey), state.stack(TransactionsKey))
    }

    @Test
    fun `navigating to a child pushes onto the current tab's stack`() {
        val state = state()
        val navigator = Navigator(state)

        navigator.navigate(TransactionsKey)
        navigator.navigate(TransactionEditKey(id = null))

        assertEquals(
            listOf(TransactionsKey, TransactionEditKey(id = null)),
            state.stack(TransactionsKey),
        )
    }

    @Test
    fun `back from a child pops it and stays on the tab`() {
        val state = state()
        val navigator = Navigator(state)
        navigator.navigate(TransactionsKey)
        navigator.navigate(TransactionEditKey(id = null))

        navigator.goBack()

        assertEquals(TransactionsKey, state.topLevelRoute)
        assertEquals(listOf(TransactionsKey), state.stack(TransactionsKey))
    }

    @Test
    fun `back from the base of a non-home tab falls back to home`() {
        val state = state()
        val navigator = Navigator(state)
        navigator.navigate(SettingsKey)

        navigator.goBack()

        assertEquals(DashboardKey, state.topLevelRoute)
    }

    @Test
    fun `tab history is kept when switching tabs`() {
        val state = state()
        val navigator = Navigator(state)
        navigator.navigate(TransactionsKey)
        navigator.navigate(TransactionEditKey(id = "t1"))

        navigator.navigate(SettingsKey)
        navigator.navigate(TransactionsKey)

        assertEquals(
            listOf(TransactionsKey, TransactionEditKey(id = "t1")),
            state.stack(TransactionsKey),
        )
    }

    @Test
    fun `stacksInUse is home only on the home tab, home plus current elsewhere`() {
        val state = state()
        val navigator = Navigator(state)

        assertEquals(listOf(DashboardKey), state.stacksInUse)

        navigator.navigate(SettingsKey)
        assertEquals(listOf(DashboardKey, SettingsKey), state.stacksInUse)
    }
}
