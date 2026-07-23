package com.hisabak.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.security.AuthAvailability
import com.hisabak.core.domain.security.shouldLock
import com.hisabak.core.platform.security.IosBiometricAuthenticator
import com.hisabak.shared.resources.Res
import com.hisabak.shared.resources.app_lock_locked_hint
import com.hisabak.shared.resources.app_lock_locked_title
import com.hisabak.shared.resources.app_lock_prompt_title
import com.hisabak.shared.resources.app_lock_unlock
import com.hisabak.ui.components.ButtonVariant
import com.hisabak.ui.components.HisabakButton
import com.hisabak.ui.icons.HugeIcons
import com.hisabak.ui.theme.Spacing
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * iOS counterpart of androidApp's `AppLockGate`: locks on cold start and on returning from
 * the background past the grace window (the shared [shouldLock]); JB lifecycle maps app
 * background/foreground to ON_STOP/ON_START inside ComposeUIViewController.
 */
@Composable
fun IosAppLockGate(content: @Composable () -> Unit) {
    val preferences = koinInject<AppPreferences>()
    val enabled by preferences.appLockEnabled.collectAsStateWithLifecycle(initialValue = null)

    when (enabled) {
        null -> Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        false -> content()
        true -> LockedContent(content)
    }
}

@Composable
private fun LockedContent(content: @Composable () -> Unit) {
    // Plain remember (not saveable): a fresh process must start locked.
    var locked by remember { mutableStateOf(true) }
    var lastBackgroundedAt by remember { mutableStateOf<Long?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP ->
                    lastBackgroundedAt = Clock.System.now().toEpochMilliseconds()
                Lifecycle.Event.ON_START ->
                    if (shouldLock(enabled = true, lastBackgroundedAt, Clock.System.now().toEpochMilliseconds())) {
                        locked = true
                    }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (locked) {
        LockScreen(onUnlocked = { locked = false })
    } else {
        content()
    }
}

@Composable
private fun LockScreen(onUnlocked: () -> Unit) {
    val authenticator = remember { IosBiometricAuthenticator() }
    val reason = stringResource(Res.string.app_lock_prompt_title)

    fun prompt() {
        // Graceful degradation, same as Android: if the credential was removed after enabling the
        // lock, bypass rather than trap the user out; the lock resumes once one is re-added.
        if (authenticator.availability() != AuthAvailability.Available) {
            onUnlocked()
            return
        }
        authenticator.authenticate(reason) { ok -> if (ok) onUnlocked() }
    }

    LaunchedEffect(Unit) { prompt() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.pageMargin),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = HugeIcons.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = stringResource(Res.string.app_lock_locked_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.s6),
        )
        Text(
            text = stringResource(Res.string.app_lock_locked_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s2),
        )
        HisabakButton(
            text = stringResource(Res.string.app_lock_unlock),
            onClick = { prompt() },
            variant = ButtonVariant.Primary,
            modifier = Modifier.padding(top = Spacing.s6),
        )
    }
}
