package com.hisabak.security

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.R
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.security.AuthAvailability
import com.hisabak.core.domain.security.shouldLock
import com.hisabak.core.platform.security.BiometricAuthenticator
import com.hisabak.ui.components.ButtonVariant
import com.hisabak.ui.components.HisabakButton
import com.hisabak.ui.theme.Spacing
import org.koin.compose.koinInject

/**
 * Gates [content] behind the biometric/device-credential lock when it's enabled. Locks on cold start
 * and on returning from the background past the grace window (see [shouldLock]); a quick app-switch
 * within the window stays unlocked. This is an access gate, not at-rest encryption.
 */
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val preferences = koinInject<AppPreferences>()
    val enabled by preferences.appLockEnabled.collectAsStateWithLifecycle(initialValue = null)

    when (enabled) {
        // Still loading the flag — show a blank themed canvas to avoid flashing content.
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
    // Plain remember (not saveable): a fresh process/recreation must start locked.
    var locked by remember { mutableStateOf(true) }
    var lastBackgroundedAt by remember { mutableStateOf<Long?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> lastBackgroundedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START ->
                    if (shouldLock(enabled = true, lastBackgroundedAt, System.currentTimeMillis())) {
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
    val context = LocalContext.current
    val authenticator = koinInject<BiometricAuthenticator>()
    val title = stringResource(R.string.app_lock_prompt_title)
    val subtitle = stringResource(R.string.app_lock_prompt_subtitle)

    fun prompt() {
        // Graceful degradation: if the user removed their PIN/biometric after enabling the lock, the
        // gate can no longer be satisfied — bypass it rather than trap them out (removing a device
        // credential itself requires authenticating, so this is not a backdoor). The lock resumes
        // automatically once a credential is re-added.
        if (authenticator.availability() != AuthAvailability.Available) {
            onUnlocked()
            return
        }
        (context as? FragmentActivity)?.let { activity ->
            authenticator.authenticate(activity, title, subtitle) { ok ->
                if (ok) onUnlocked()
            }
        }
    }

    // Auto-present the system prompt as soon as the lock screen appears.
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
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.app_brand_name),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = stringResource(R.string.app_lock_locked_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.s6),
        )
        Text(
            text = stringResource(R.string.app_lock_locked_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s2),
        )
        HisabakButton(
            text = stringResource(R.string.app_lock_unlock),
            onClick = { prompt() },
            variant = ButtonVariant.Primary,
            modifier = Modifier.padding(top = Spacing.s6),
        )
    }
}
