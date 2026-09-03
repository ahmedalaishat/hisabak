package com.hisabak

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.hisabak.ui.theme.Spacing
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Text
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.ThemeMode
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.presentation.BrandCreatedBus
import com.hisabak.feature.brand.presentation.BrandEditBus
import com.hisabak.feature.brand.presentation.edit.BrandEditRoute
import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.presentation.CategoryCreatedBus
import com.hisabak.feature.category.presentation.edit.CategoryEditPrefill
import com.hisabak.feature.category.presentation.edit.CategoryEditRoute
import com.hisabak.feature.dashboard.presentation.CategoryFocusBus
import com.hisabak.feature.insights.presentation.InsightsPeriodBus
import com.hisabak.feature.insights.presentation.InsightsRoute
import com.hisabak.feature.insights.presentation.ask.AskRoute
import com.hisabak.feature.dashboard.presentation.DashboardRoute
import com.hisabak.feature.notification.domain.NotificationRepository
import com.hisabak.feature.notification.presentation.list.NotificationsRoute
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.feature.transaction.presentation.edit.TransactionEditRoute
import com.hisabak.feature.transaction.presentation.list.TransactionListFilterBus
import com.hisabak.feature.transaction.presentation.list.TransactionListFilterRequest
import com.hisabak.feature.transaction.presentation.list.TransactionListRoute
import com.hisabak.nav.BackupKey
import com.hisabak.nav.BottomSheetSceneStrategy
import com.hisabak.nav.BrandEditKey
import com.hisabak.nav.CategoryEditKey
import com.hisabak.nav.DashboardKey
import com.hisabak.nav.ManageKey
import com.hisabak.nav.Navigator
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.nav.InsightsAskKey
import com.hisabak.nav.InsightsKey
import com.hisabak.nav.LedgerTab
import com.hisabak.nav.NotificationsKey
import com.hisabak.nav.SettingsKey
import com.hisabak.nav.SmsTemplateEditKey
import com.hisabak.nav.SmsTemplatesKey
import com.hisabak.nav.TransactionEditKey
import com.hisabak.nav.TransactionsKey
import com.hisabak.nav.fullScreenTransition
import com.hisabak.nav.rememberNavigationState
import com.hisabak.nav.toEntries
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.feature.sms.presentation.templates.SmsTemplateEditRoute
import com.hisabak.feature.sms.presentation.templates.SmsTemplatesRoute
import com.hisabak.shared.resources.Res
import com.hisabak.shared.resources.app_brand_name
import com.hisabak.shared.resources.backup_title
import com.hisabak.shared.resources.sms_template_edit_title
import com.hisabak.shared.resources.sms_template_new_title
import com.hisabak.shared.resources.sms_templates_title
import com.hisabak.shared.resources.brand_edit_title
import com.hisabak.shared.resources.brand_new_title
import com.hisabak.shared.resources.category_edit_title
import com.hisabak.shared.resources.category_new_title
import com.hisabak.shared.resources.nav_dashboard
import com.hisabak.shared.resources.nav_insights
import com.hisabak.shared.resources.nav_manage
import com.hisabak.shared.resources.nav_settings
import com.hisabak.shared.resources.nav_sms
import com.hisabak.shared.resources.nav_transactions
import com.hisabak.shared.resources.insights_ask_title_sheet
import com.hisabak.shared.resources.insights_title
import com.hisabak.shared.resources.notifications_title
import com.hisabak.shared.resources.sms_inbox_title
import com.hisabak.shared.resources.transaction_add
import com.hisabak.ui.components.BottomNavTab
import com.hisabak.ui.components.DetailTopBar
import com.hisabak.ui.components.HisabakBottomNav
import com.hisabak.ui.components.HisabakTopBar
import com.hisabak.ui.components.clearFocusOnScroll
import com.hisabak.ui.components.clearFocusOnTap
import com.hisabak.ui.icons.HugeIcons
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.Motion
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The platform seams of the shared app shell. Each slot is the small piece a platform must
 * provide natively: the five Routes that need launchers/consent flows, the app-lock gate,
 * a one-shot notification-permission effect, and a system-bar styler fed the resolved theme.
 */
class PlatformSlots(
    val onboarding: @Composable () -> Unit,
    val restore: @Composable () -> Unit,
    val smsInbox: @Composable (onCreateTemplate: (String) -> Unit, onReviewTransaction: (String) -> Unit, Modifier) -> Unit,
    val settings: @Composable (onOpenBackup: () -> Unit, onOpenSmsTemplates: () -> Unit, Modifier) -> Unit,
    val backup: @Composable (Modifier) -> Unit,
    val appLockGate: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
    val notificationPermissionEffect: @Composable () -> Unit = {},
    val systemBarStyler: @Composable (darkTheme: Boolean) -> Unit = {},
)

/** First-launch flow stages, animated between by the launch gate. */
private enum class LaunchStage { Loading, Onboarding, Restore, App }

/**
 * The whole app minus platform glue: theme resolution, the first-launch flow
 * (onboarding → one-time restore offer → app), the app-lock gate, and the tabbed nav shell.
 */
@Composable
fun HisabakRoot(slots: PlatformSlots) {
    val preferences = koinInject<AppPreferences>()
    val themeMode by preferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    slots.systemBarStyler(darkTheme)
    HisabakTheme(darkTheme = darkTheme) {
        val onboardingCompleted by preferences.onboardingCompleted
            .collectAsStateWithLifecycle(initialValue = null)
        val restoreOffered by preferences.restoreOffered
            .collectAsStateWithLifecycle(initialValue = null)
        // First-launch flow: onboarding → one-time restore-from-Drive page (skippable) → app.
        val stage = when {
            onboardingCompleted == null -> LaunchStage.Loading
            onboardingCompleted == false -> LaunchStage.Onboarding
            restoreOffered == null -> LaunchStage.Loading
            restoreOffered == false -> LaunchStage.Restore
            else -> LaunchStage.App
        }
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                (slideInHorizontally(tween(Motion.Duration.Slow, easing = Motion.Easing.Standard)) { it } +
                    fadeIn(tween(Motion.Duration.Base))) togetherWith
                    (slideOutHorizontally(tween(Motion.Duration.Slow, easing = Motion.Easing.Standard)) { -it / 6 } +
                        fadeOut(tween(Motion.Duration.Fast)))
            },
            label = "launchStage",
        ) { current ->
            when (current) {
                LaunchStage.Loading -> Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                )
                LaunchStage.Onboarding -> {
                    val analytics = koinInject<Analytics>()
                    LaunchedEffect(Unit) { analytics.setCurrentScreen("onboarding") }
                    slots.onboarding()
                }
                LaunchStage.Restore -> {
                    val analytics = koinInject<Analytics>()
                    LaunchedEffect(Unit) { analytics.setCurrentScreen("restore") }
                    slots.restore()
                }
                LaunchStage.App -> slots.appLockGate { HisabakNav(slots) }
            }
        }
    }
}

private enum class RootTab(
    val key: NavKey,
    val labelRes: StringResource,
    val icon: ImageVector,
) {
    // Ordered by what the tab is for, not by how often it is opened: the two you read
    // (Dashboard, Insights) sit together, then the two you act in (Transactions, Manage), then
    // Settings. It also puts the most-tapped tab in the middle, where the thumb lands.
    Dashboard(DashboardKey, Res.string.nav_dashboard, HugeIcons.SpaceDashboard),
    Insights(InsightsKey, Res.string.nav_insights, HugeIcons.Insights),
    Transactions(TransactionsKey, Res.string.nav_transactions, HugeIcons.List),
    Manage(ManageKey, Res.string.nav_manage, HugeIcons.Layers),
    Settings(SettingsKey, Res.string.nav_settings, HugeIcons.Settings),
}

/** Which transaction sheet to restore after a brand-editor detour (null id = the new-entry sheet). */
private data class ReopenSheet(val transactionId: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HisabakNav(slots: PlatformSlots) {
    val navigationState = rememberNavigationState(
        startRoute = DashboardKey,
        topLevelRoutes = RootTab.entries.map { it.key },
    )
    val navigator = remember { Navigator(navigationState) }
    // Pushing a full screen ON TOP of the bottom-sheet entry breaks the overlay scene on the
    // way back (duplicate saveable key, observed on device) — so the brand detours close the
    // sheet first and reopen the same transaction (null id = a new one, whose typed input the
    // draft bus preserves) when the brand editor finishes.
    val reopenTransactionAfterBrandEdit = remember { mutableStateOf<ReopenSheet?>(null) }
    // Which half of the ledger tab is showing. Hoisted here because the top bar, the FAB, and the
    // "open the inbox" intent all depend on it.
    var ledgerTab by rememberSaveable { mutableStateOf(LedgerTab.Transactions) }
    val bottomSheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }
    val filterBus = koinInject<TransactionListFilterBus>()
    val inboxOpenBus = koinInject<com.hisabak.feature.sms.presentation.InboxOpenBus>()
    val categoryFocusBus = koinInject<CategoryFocusBus>()
    val brandEditBus = koinInject<BrandEditBus>()
    val categoryCreatedBus = koinInject<CategoryCreatedBus>()
    val brandCreatedBus = koinInject<BrandCreatedBus>()
    val notificationRepository = koinInject<NotificationRepository>()
    val insightsPeriodBus = koinInject<InsightsPeriodBus>()

    val unreadCount by notificationRepository.observeUnreadCount().collectAsStateWithLifecycle(initialValue = 0)
    val pendingFocus by categoryFocusBus.pending.collectAsStateWithLifecycle()
    // The iOS "Open SMS inbox" intent may fire before (or while) the UI exists — land on the
    // SMS tab once the shell is composed.
    val pendingInboxOpen by inboxOpenBus.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingInboxOpen) {
        if (pendingInboxOpen) {
            ledgerTab = LedgerTab.Sms
            navigator.navigate(TransactionsKey)
            inboxOpenBus.consume()
        }
    }
    val pendingBrandEdit by brandEditBus.pending.collectAsStateWithLifecycle()

    // A system-notification tap publishes a focus while we may be on another tab — switch to the
    // dashboard so it can consume and expand the category.
    LaunchedEffect(pendingFocus) {
        if (pendingFocus != null && navigationState.topLevelRoute != DashboardKey) {
            navigator.navigate(DashboardKey)
        }
    }

    // A "transaction recorded" tap for an uncategorized brand asks to open that brand's editor:
    // switch to Manage and push the brand edit screen, then clear the request.
    LaunchedEffect(pendingBrandEdit) {
        pendingBrandEdit?.let { brandId ->
            navigator.navigate(ManageKey)
            navigator.navigate(BrandEditKey(id = brandId))
            brandEditBus.consume()
        }
    }

    slots.notificationPermissionEffect()

    val tabs = RootTab.entries.map {
        BottomNavTab(
            key = it.name,
            label = stringResource(it.labelRes),
            icon = it.icon,
        )
    }

    val currentTab = RootTab.entries.first { it.key == navigationState.topLevelRoute }
    // The transaction add/edit screen is an overlay bottom sheet (tab chrome stays behind it).
    // Brand/Category edits and the notifications screen are full-screen pages with a back arrow.
    val leaf = navigationState.backStacks[navigationState.topLevelRoute]?.lastOrNull()
    val fullScreen = leaf is BrandEditKey || leaf is CategoryEditKey ||
        leaf == NotificationsKey || leaf == BackupKey || leaf is InsightsAskKey ||
        leaf == SmsTemplatesKey || leaf is SmsTemplateEditKey

    val analytics = koinInject<Analytics>()
    val screenName = when (leaf) {
        is TransactionEditKey -> "transaction_edit"
        is BrandEditKey -> "brand_edit"
        is CategoryEditKey -> "category_edit"
        NotificationsKey -> "notifications"
        is InsightsAskKey -> "insights_ask"
        BackupKey -> "backup"
        SmsTemplatesKey -> "sms_templates"
        is SmsTemplateEditKey -> "sms_template_edit"
        else -> when (currentTab) {
            RootTab.Dashboard -> "dashboard"
            RootTab.Transactions -> if (ledgerTab == LedgerTab.Sms) "sms_inbox" else "transactions"
            RootTab.Insights -> "insights"
            RootTab.Manage -> "manage"
            RootTab.Settings -> "settings"
        }
    }
    LaunchedEffect(screenName) { analytics.setCurrentScreen(screenName) }

    Scaffold(
        topBar = {
            when (leaf) {
                is CategoryEditKey -> DetailTopBar(
                    title = stringResource(if (leaf.id == null) Res.string.category_new_title else Res.string.category_edit_title),
                    onBack = { navigator.goBack() },
                )
                is BrandEditKey -> DetailTopBar(
                    title = stringResource(if (leaf.id == null) Res.string.brand_new_title else Res.string.brand_edit_title),
                    onBack = { navigator.goBack() },
                )
                NotificationsKey -> DetailTopBar(
                    title = stringResource(Res.string.notifications_title),
                    onBack = { navigator.goBack() },
                )
                is InsightsAskKey -> DetailTopBar(
                    title = stringResource(Res.string.insights_ask_title_sheet),
                    onBack = { navigator.goBack() },
                )
                BackupKey -> DetailTopBar(
                    title = stringResource(Res.string.backup_title),
                    onBack = { navigator.goBack() },
                )
                SmsTemplatesKey -> DetailTopBar(
                    title = stringResource(Res.string.sms_templates_title),
                    onBack = { navigator.goBack() },
                )
                is SmsTemplateEditKey -> DetailTopBar(
                    title = stringResource(
                        if (leaf.templateId == null) Res.string.sms_template_new_title
                        else Res.string.sms_template_edit_title,
                    ),
                    onBack = { navigator.goBack() },
                )
                else -> HisabakTopBar(
                    title = when (currentTab) {
                        RootTab.Dashboard -> stringResource(Res.string.app_brand_name)
                        RootTab.Transactions -> stringResource(Res.string.nav_transactions)
                        RootTab.Insights -> stringResource(Res.string.insights_title)
                        RootTab.Manage -> stringResource(Res.string.nav_manage)
                        RootTab.Settings -> stringResource(Res.string.nav_settings)
                    },
                    onNotificationsClick = { navigator.navigate(NotificationsKey) },
                    unreadCount = unreadCount,
                )
            }
        },
        bottomBar = {
            if (!fullScreen) {
                HisabakBottomNav(
                    tabs = tabs,
                    selectedKey = currentTab.name,
                    onSelect = { key -> navigator.navigate(RootTab.valueOf(key).key) },
                )
            }
        },
        floatingActionButton = {
            if (leaf == TransactionsKey && ledgerTab == LedgerTab.Transactions) {
                FloatingActionButton(
                    onClick = { navigator.navigate(TransactionEditKey(id = null)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(HugeIcons.Add, contentDescription = stringResource(Res.string.transaction_add))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val entryProvider = entryProvider<NavKey> {
            entry<DashboardKey> {
                DashboardRoute(
                    onShowUncategorized = {
                        filterBus.request(TransactionListFilterRequest.Uncategorized)
                        navigator.navigate(TransactionsKey)
                    },
                    onOpenInsights = { period ->
                        insightsPeriodBus.request(period)
                        navigator.navigate(InsightsKey)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            entry<TransactionsKey> {
                LedgerTabs(selected = ledgerTab, onSelect = { ledgerTab = it }) {
                    when (ledgerTab) {
                        LedgerTab.Transactions -> TransactionListRoute(
                            onAdd = { navigator.navigate(TransactionEditKey(id = null)) },
                            onEdit = { id -> navigator.navigate(TransactionEditKey(id = id.value)) },
                        )
                        LedgerTab.Sms -> slots.smsInbox(
                            { smsId -> navigator.navigate(SmsTemplateEditKey(templateId = null, sampleSmsId = smsId)) },
                            { txId -> navigator.navigate(TransactionEditKey(id = txId)) },
                            Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            entry<InsightsKey> {
                InsightsRoute(
                    onOpenCategory = { id ->
                        filterBus.request(TransactionListFilterRequest.ByCategory(id))
                        navigator.navigate(TransactionsKey)
                    },
                    onOpenUncategorized = {
                        filterBus.request(TransactionListFilterRequest.Uncategorized)
                        navigator.navigate(TransactionsKey)
                    },
                    onSetLimit = { id, amountMinor ->
                        navigator.navigate(CategoryEditKey(id = id.value, prefillLimitMinor = amountMinor))
                    },
                    onOpenAsk = { period, question ->
                        navigator.navigate(InsightsAskKey(period = period.name, question = question))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            entry<InsightsAskKey>(metadata = fullScreenTransition()) { key ->
                AskRoute(
                    period = SummaryPeriod.valueOf(key.period),
                    initialQuestion = key.question,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            entry<SettingsKey> {
                slots.settings(
                    { navigator.navigate(BackupKey) },
                    { navigator.navigate(SmsTemplatesKey) },
                    Modifier.fillMaxSize(),
                )
            }
            entry<BackupKey>(metadata = fullScreenTransition()) {
                slots.backup(Modifier.fillMaxSize())
            }
            entry<SmsTemplatesKey>(metadata = fullScreenTransition()) {
                SmsTemplatesRoute(
                    onAdd = { navigator.navigate(SmsTemplateEditKey(templateId = null, sampleSmsId = null)) },
                    onOpen = { id -> navigator.navigate(SmsTemplateEditKey(templateId = id.value, sampleSmsId = null)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            entry<SmsTemplateEditKey>(metadata = fullScreenTransition()) { key ->
                SmsTemplateEditRoute(
                    templateId = key.templateId?.let(::SmsTemplateId),
                    sampleSmsId = key.sampleSmsId?.let(::SmsMessageId),
                    onDone = { navigator.goBack() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            entry<ManageKey> {
                ManageRoute(
                    modifier = Modifier.fillMaxSize(),
                    onAddBrand = { navigator.navigate(BrandEditKey(id = null)) },
                    onEditBrand = { id -> navigator.navigate(BrandEditKey(id = id.value)) },
                    onAddCategory = { navigator.navigate(CategoryEditKey(id = null)) },
                    onEditCategory = { id -> navigator.navigate(CategoryEditKey(id = id.value)) },
                    // Same shape as the dashboard's uncategorized card: park the filter, then go.
                    onViewBrandTransactions = { id ->
                        filterBus.request(TransactionListFilterRequest.ByBrand(id))
                        navigator.navigate(TransactionsKey)
                    },
                    onViewCategoryTransactions = { id ->
                        filterBus.request(TransactionListFilterRequest.ByCategory(id))
                        navigator.navigate(TransactionsKey)
                    },
                )
            }
            entry<NotificationsKey>(metadata = fullScreenTransition()) {
                NotificationsRoute(
                    onOpenCategory = { id ->
                        navigator.goBack()
                        categoryFocusBus.request(id)
                        navigator.navigate(DashboardKey)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            entry<TransactionEditKey>(metadata = BottomSheetSceneStrategy.bottomSheet()) { key ->
                TransactionEditRoute(
                    transactionId = key.id?.let(::TransactionId),
                    onDone = { navigator.goBack() },
                    onCancel = { navigator.goBack() },
                    onEditBrand = { id ->
                        reopenTransactionAfterBrandEdit.value = ReopenSheet(key.id)
                        navigator.goBack()
                        navigator.navigate(BrandEditKey(id = id.value))
                    },
                    onCreateBrand = {
                        reopenTransactionAfterBrandEdit.value = ReopenSheet(key.id)
                        navigator.goBack()
                        navigator.navigate(BrandEditKey(id = null, forPick = true))
                    },
                )
            }
            entry<BrandEditKey>(metadata = fullScreenTransition()) { key ->
                val closeBrandEditor = {
                    navigator.goBack()
                    // Came from the transaction sheet — put the sheet back.
                    reopenTransactionAfterBrandEdit.value?.let {
                        navigator.navigate(TransactionEditKey(id = it.transactionId))
                        reopenTransactionAfterBrandEdit.value = null
                    }
                }
                BrandEditRoute(
                    brandId = key.id?.let(::BrandId),
                    onDone = { id ->
                        if (key.forPick) brandCreatedBus.publish(id)
                        closeBrandEditor()
                    },
                    onCancel = { closeBrandEditor() },
                    onCreateCategory = { prefill ->
                        navigator.navigate(
                            CategoryEditKey(
                                id = null,
                                forPick = true,
                                prefillName = prefill?.name,
                                prefillType = prefill?.type?.name,
                                prefillColor = prefill?.color,
                                prefillIcon = prefill?.icon,
                            ),
                        )
                    },
                )
            }
            entry<CategoryEditKey>(metadata = fullScreenTransition()) { key ->
                CategoryEditRoute(
                    categoryId = key.id?.let(::CategoryId),
                    prefill = key.prefillName?.let { name ->
                        CategoryEditPrefill(
                            name = name,
                            type = CategoryType.entries.firstOrNull { it.name == key.prefillType }
                                ?: CategoryType.EXPENSES,
                            color = key.prefillColor ?: Category.DEFAULT_COLOR,
                            icon = key.prefillIcon ?: Category.DEFAULT_ICON,
                        )
                    },
                    proposedLimitMinor = key.prefillLimitMinor,
                    onDone = { id ->
                        if (key.forPick) categoryCreatedBus.publish(id)
                        navigator.goBack()
                    },
                    onCancel = { navigator.goBack() },
                )
            }
        }

        // Child-screen transitions are set per-entry (see fullScreenTransition()); the container
        // level pins tab switches to a cross-fade on every platform — the JB iOS default is a
        // UIKit-style push, which reads as hierarchy between sibling tabs (and was where the
        // see-through-transition artifact surfaced before entries got opaque backgrounds).
        val crossFade: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
            fadeIn(tween(Motion.Duration.Base)) togetherWith fadeOut(tween(Motion.Duration.Base))
        }
        NavDisplay(
            entries = navigationState.toEntries(entryProvider),
            onBack = { navigator.goBack() },
            transitionSpec = crossFade,
            popTransitionSpec = crossFade,
            predictivePopTransitionSpec = { _ -> crossFade() },
            sceneStrategies = listOf(bottomSheetStrategy, SinglePaneSceneStrategy()),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clearFocusOnTap()
                .clearFocusOnScroll(),
        )
    }
}

/**
 * The ledger's two halves. The selector is the top-level element of the tab and each half owns its
 * own controls beneath it — the transaction list already carries period chips and filters, and
 * stacking a second row of shared controls above them would read as one long toolbar.
 */
@Composable
private fun LedgerTabs(
    selected: LedgerTab,
    onSelect: (LedgerTab) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.pageMargin)
                .padding(top = Spacing.pageMargin),
        ) {
            LedgerTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    shape = SegmentedButtonDefaults.itemShape(index, LedgerTab.entries.size),
                ) {
                    Text(
                        stringResource(
                            when (tab) {
                                LedgerTab.Transactions -> Res.string.nav_transactions
                                LedgerTab.Sms -> Res.string.nav_sms
                            },
                        ),
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}
