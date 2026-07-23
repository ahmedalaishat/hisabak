package com.hisabak.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * Holds one back stack per top-level route. The user always exits the app through
 * [startRoute]; switching tabs keeps each tab's history intact.
 */
class NavigationState(
    val startRoute: NavKey,
    topLevelRoutes: List<NavKey>,
) {
    var topLevelRoute: NavKey by mutableStateOf(startRoute)

    val backStacks: Map<NavKey, SnapshotStateList<NavKey>> =
        topLevelRoutes.associateWith { mutableStateListOf(it) }

    val stacksInUse: List<NavKey>
        get() = if (topLevelRoute == startRoute) {
            listOf(startRoute)
        } else {
            listOf(startRoute, topLevelRoute)
        }
}

/**
 * Translates navigation events into [NavigationState] changes (unidirectional data flow).
 */
class Navigator(val state: NavigationState) {
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
        } else {
            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    fun goBack() {
        val currentStack = state.backStacks[state.topLevelRoute] ?: return
        if (currentStack.last() == state.topLevelRoute) {
            // At the base of a non-home tab: fall back to the home tab.
            state.topLevelRoute = state.startRoute
        } else {
            currentStack.removeLastOrNull()
        }
    }
}

@Composable
fun rememberNavigationState(
    startRoute: NavKey,
    topLevelRoutes: List<NavKey>,
): NavigationState = remember(startRoute, topLevelRoutes) {
    NavigationState(startRoute, topLevelRoutes)
}

/**
 * Flattens the in-use back stacks into the entries [NavDisplay] renders, decorating
 * each entry so it keeps its saveable state and gets a ViewModel store scoped to the
 * destination (cleared when the entry is popped → no stale state on re-entry).
 */
@Composable
fun NavigationState.toEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>,
): SnapshotStateList<NavEntry<NavKey>> {
    // Screens are content-only layers over the Scaffold's background; slide-type transitions
    // (e.g. the iOS-default push) move that layer at full opacity, so without an opaque
    // per-entry background the previous destination shows through until the animation ends.
    // Sheet entries keep the ModalBottomSheet's own surface.
    val opaqueBackgroundDecorator = remember {
        NavEntryDecorator<NavKey> { entry ->
            if (isBottomSheet(entry.metadata)) {
                entry.Content()
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    entry.Content()
                }
            }
        }
    }
    val decoratedEntries = backStacks.mapValues { (_, stack) ->
        rememberDecoratedNavEntries(
            backStack = stack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
                opaqueBackgroundDecorator,
            ),
            entryProvider = entryProvider,
        )
    }

    return stacksInUse
        .flatMap { decoratedEntries[it] ?: emptyList() }
        .toMutableStateList()
}
