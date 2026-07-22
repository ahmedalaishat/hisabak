package com.hisabak

import com.hisabak.ui.icons.HugeIcons

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.presentation.BrandEditBus
import com.hisabak.feature.brand.presentation.edit.BrandEditRoute
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.presentation.edit.CategoryEditRoute
import com.hisabak.core.data.preferences.AppLocale
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.ThemeMode
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.feature.dashboard.presentation.CategoryFocusBus
import com.hisabak.feature.dashboard.presentation.DashboardRoute
import com.hisabak.feature.onboarding.presentation.OnboardingRoute
import com.hisabak.feature.restore.presentation.RestoreRoute
import com.hisabak.feature.settings.presentation.SettingsRoute
import com.hisabak.feature.notification.domain.NotificationRepository
import com.hisabak.feature.notification.platform.SystemNotifier
import com.hisabak.feature.notification.presentation.list.NotificationsRoute
import com.hisabak.feature.sms.presentation.inbox.SmsInboxRoute
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.feature.transaction.presentation.edit.TransactionEditRoute
import com.hisabak.feature.transaction.presentation.list.TransactionListFilterBus
import com.hisabak.feature.transaction.presentation.list.TransactionListFilterRequest
import com.hisabak.feature.transaction.presentation.list.TransactionListRoute
import com.hisabak.feature.backup.presentation.BackupRoute
import com.hisabak.nav.BackupKey
import com.hisabak.nav.BottomSheetSceneStrategy
import com.hisabak.nav.BrandEditKey
import com.hisabak.nav.CategoryEditKey
import com.hisabak.nav.DashboardKey
import com.hisabak.nav.ManageKey
import com.hisabak.nav.Navigator
import com.hisabak.nav.NotificationsKey
import com.hisabak.nav.SettingsKey
import com.hisabak.nav.SmsKey
import com.hisabak.nav.TransactionEditKey
import com.hisabak.nav.TransactionsKey
import com.hisabak.nav.fullScreenTransition
import com.hisabak.nav.rememberNavigationState
import com.hisabak.nav.toEntries
import com.hisabak.security.AppLockGate
import com.hisabak.ui.components.BottomNavTab
import com.hisabak.ui.components.DetailTopBar
import com.hisabak.ui.components.HisabakBottomNav
import com.hisabak.ui.components.HisabakTopBar
import com.hisabak.ui.components.clearFocusOnTap
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.Motion
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

// FragmentActivity (not plain ComponentActivity) is required by androidx.biometric's BiometricPrompt;
// it still extends ComponentActivity (so Navigation 3 keeps its dispatcher owner) and pulls in
// androidx.fragment, not appcompat — preserving the app's no-appcompat constraint.
class MainActivity : FragmentActivity() {

    private val categoryFocusBus: CategoryFocusBus by inject()
    private val brandEditBus: BrandEditBus by inject()

    // Apply the saved app language before any resources are resolved.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleFocusIntent(intent)
        setContent {
            val preferences = koinInject<AppPreferences>()
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Keep the system-bar icons legible against the app's resolved theme rather than the
            // system uiMode that enableEdgeToEdge() keys off — otherwise picking Light while the
            // device is in Dark (or switching theme in-app) leaves light icons on a light bar.
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
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
                            OnboardingRoute()
                        }
                        LaunchStage.Restore -> {
                            val analytics = koinInject<Analytics>()
                            LaunchedEffect(Unit) { analytics.setCurrentScreen("restore") }
                            RestoreRoute()
                        }
                        LaunchStage.App -> AppLockGate { HisabakNav() }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleFocusIntent(intent)
    }

    /** A notification tap carries either a category to focus (dashboard) or an uncategorized brand
     *  to open in the editor; publish whichever is present so the nav layer can react. */
    private fun handleFocusIntent(intent: Intent?) {
        intent?.getStringExtra(SystemNotifier.EXTRA_CATEGORY_ID)?.let(categoryFocusBus::request)
        intent?.getStringExtra(SystemNotifier.EXTRA_BRAND_ID)?.let(brandEditBus::request)
    }
}

/** First-launch flow stages, animated between by the launch gate. */
private enum class LaunchStage { Loading, Onboarding, Restore, App }

private enum class RootTab(
    val key: NavKey,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Dashboard(DashboardKey, R.string.nav_dashboard, HugeIcons.SpaceDashboard),
    Transactions(TransactionsKey, R.string.nav_transactions, HugeIcons.List),
    Sms(SmsKey, R.string.nav_sms, HugeIcons.Message),
    Manage(ManageKey, R.string.nav_manage, HugeIcons.Layers),
    Settings(SettingsKey, R.string.nav_settings, HugeIcons.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HisabakNav() {
    val navigationState = rememberNavigationState(
        startRoute = DashboardKey,
        topLevelRoutes = RootTab.entries.map { it.key },
    )
    val navigator = remember { Navigator(navigationState) }
    val bottomSheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }
    val filterBus = koinInject<TransactionListFilterBus>()
    val categoryFocusBus = koinInject<CategoryFocusBus>()
    val brandEditBus = koinInject<BrandEditBus>()
    val notificationRepository = koinInject<NotificationRepository>()

    val unreadCount by notificationRepository.observeUnreadCount().collectAsStateWithLifecycle(initialValue = 0)
    val pendingFocus by categoryFocusBus.pending.collectAsStateWithLifecycle()
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

    // Ask for notification permission once on first launch (Android 13+).
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
        leaf == NotificationsKey || leaf == BackupKey

    val analytics = koinInject<Analytics>()
    val screenName = when (leaf) {
        is TransactionEditKey -> "transaction_edit"
        is BrandEditKey -> "brand_edit"
        is CategoryEditKey -> "category_edit"
        NotificationsKey -> "notifications"
        BackupKey -> "backup"
        else -> when (currentTab) {
            RootTab.Dashboard -> "dashboard"
            RootTab.Transactions -> "transactions"
            RootTab.Sms -> "sms_inbox"
            RootTab.Manage -> "manage"
            RootTab.Settings -> "settings"
        }
    }
    LaunchedEffect(screenName) { analytics.setCurrentScreen(screenName) }

    Scaffold(
        topBar = {
            when (leaf) {
                is CategoryEditKey -> DetailTopBar(
                    title = stringResource(if (leaf.id == null) R.string.category_new_title else R.string.category_edit_title),
                    onBack = { navigator.goBack() },
                )
                is BrandEditKey -> DetailTopBar(
                    title = stringResource(if (leaf.id == null) R.string.brand_new_title else R.string.brand_edit_title),
                    onBack = { navigator.goBack() },
                )
                NotificationsKey -> DetailTopBar(
                    title = stringResource(R.string.notifications_title),
                    onBack = { navigator.goBack() },
                )
                BackupKey -> DetailTopBar(
                    title = stringResource(R.string.backup_title),
                    onBack = { navigator.goBack() },
                )
                else -> HisabakTopBar(
                    title = when (currentTab) {
                        RootTab.Dashboard -> stringResource(R.string.app_brand_name)
                        RootTab.Transactions -> stringResource(R.string.nav_transactions)
                        RootTab.Sms -> stringResource(R.string.sms_inbox_title)
                        RootTab.Manage -> stringResource(R.string.nav_manage)
                        RootTab.Settings -> stringResource(R.string.nav_settings)
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
            if (leaf == TransactionsKey) {
                FloatingActionButton(
                    onClick = { navigator.navigate(TransactionEditKey(id = null)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(HugeIcons.Add, contentDescription = stringResource(R.string.transaction_add))
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
                    modifier = Modifier.fillMaxSize(),
                )
            }
            entry<TransactionsKey> {
                TransactionListRoute(
                    onAdd = { navigator.navigate(TransactionEditKey(id = null)) },
                    onEdit = { id -> navigator.navigate(TransactionEditKey(id = id.value)) },
                )
            }
            entry<SmsKey> {
                SmsInboxRoute(modifier = Modifier.fillMaxSize())
            }
            entry<SettingsKey> {
                SettingsRoute(
                    onOpenBackup = { navigator.navigate(BackupKey) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            entry<BackupKey>(metadata = fullScreenTransition()) {
                BackupRoute(modifier = Modifier.fillMaxSize())
            }
            entry<ManageKey> {
                ManageRoute(
                    modifier = Modifier.fillMaxSize(),
                    onAddBrand = { navigator.navigate(BrandEditKey(id = null)) },
                    onEditBrand = { id -> navigator.navigate(BrandEditKey(id = id.value)) },
                    onAddCategory = { navigator.navigate(CategoryEditKey(id = null)) },
                    onEditCategory = { id -> navigator.navigate(CategoryEditKey(id = id.value)) },
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
                )
            }
            entry<BrandEditKey>(metadata = fullScreenTransition()) { key ->
                BrandEditRoute(
                    brandId = key.id?.let(::BrandId),
                    onDone = { navigator.goBack() },
                    onCancel = { navigator.goBack() },
                )
            }
            entry<CategoryEditKey>(metadata = fullScreenTransition()) { key ->
                CategoryEditRoute(
                    categoryId = key.id?.let(::CategoryId),
                    onDone = { navigator.goBack() },
                    onCancel = { navigator.goBack() },
                )
            }
        }

        // Transitions are set per-entry (see fullScreenTransition() on the full-screen children),
        // not globally: a global NavDisplay transition fights the bottom sheet's own open/close
        // animation. So the transaction add/edit sheet and bottom-nav tab switches keep the defaults,
        // while pushing/popping full-screen children slides + fades.
        NavDisplay(
            entries = navigationState.toEntries(entryProvider),
            onBack = { navigator.goBack() },
            sceneStrategy = bottomSheetStrategy.then(SinglePaneSceneStrategy()),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clearFocusOnTap(),
        )
    }
}
