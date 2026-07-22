package com.hisabak.feature.restore.presentation

import com.hisabak.ui.icons.HugeIcons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hisabak.R
import com.hisabak.feature.backup.presentation.SyncKind
import com.hisabak.feature.backup.presentation.SyncScreen
import com.hisabak.feature.backup.presentation.messageRes
import com.hisabak.ui.components.ButtonVariant
import com.hisabak.ui.components.HisabakButton
import com.hisabak.ui.theme.Motion
import com.hisabak.ui.theme.Spacing

private enum class RestoreView { Intro, Passphrase, Sync }

@Composable
fun RestoreScreen(
    state: RestoreUiState,
    onConnect: () -> Unit,
    onSubmitPassphrase: (String) -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = when {
        state.sync != null -> RestoreView.Sync
        state.needsPassphrase -> RestoreView.Passphrase
        else -> RestoreView.Intro
    }

    AnimatedContent(
        targetState = view,
        // Advancing (Intro → Passphrase → Sync) slides the new pane in from the end + fade;
        // going back slides the reverse. SlideDirection.Start/End are layout-direction aware (RTL).
        transitionSpec = {
            val towards = if (targetState.ordinal > initialState.ordinal) SlideDirection.Start else SlideDirection.End
            (slideIntoContainer(towards, tween(Motion.Duration.Slow, easing = Motion.Easing.Standard)) +
                fadeIn(tween(Motion.Duration.Base))) togetherWith
                (slideOutOfContainer(towards, tween(Motion.Duration.Slow, easing = Motion.Easing.Standard)) +
                    fadeOut(tween(Motion.Duration.Base)))
        },
        label = "restoreView",
        modifier = modifier,
    ) { current ->
        when (current) {
            RestoreView.Sync -> SyncScreen(
                kind = SyncKind.Restore,
                phase = state.sync ?: com.hisabak.feature.backup.presentation.SyncPhase.Running,
                onContinue = onFinish,
                onRetry = onConnect,
                onClose = onSkip,
            )
            RestoreView.Passphrase -> {
                var passphrase by rememberSaveable { mutableStateOf("") }
                RestorePane(
                    heroIcon = HugeIcons.Lock,
                    title = stringResource(R.string.restore_passphrase_title),
                    subtitle = stringResource(R.string.restore_passphrase_subtitle, state.account?.email ?: ""),
                    message = (state.message as? RestoreMessage.Failed)?.let { stringResource(it.error.messageRes()) },
                    primaryText = stringResource(R.string.restore_action),
                    primaryEnabled = passphrase.isNotEmpty(),
                    onPrimary = { onSubmitPassphrase(passphrase) },
                    onSkip = onSkip,
                ) {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(stringResource(R.string.backup_passphrase)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.s5),
                    )
                }
            }
            RestoreView.Intro -> RestorePane(
                heroIcon = HugeIcons.CloudDownload,
                title = stringResource(R.string.restore_title),
                subtitle = stringResource(R.string.restore_subtitle),
                message = if (state.message is RestoreMessage.NothingFound) stringResource(R.string.restore_none_found) else null,
                primaryText = stringResource(
                    if (state.account != null) R.string.restore_try_another else R.string.restore_connect,
                ),
                primaryEnabled = true,
                onPrimary = onConnect,
                onSkip = onSkip,
            )
        }
    }
}

@Composable
private fun RestorePane(
    heroIcon: ImageVector,
    title: String,
    subtitle: String,
    message: String?,
    primaryText: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
    field: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = Spacing.s7),
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = heroIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s3)) {
            Text(
                text = stringResource(R.string.restore_overline).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.s4),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s4),
            )
            field?.invoke()
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.s4),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HisabakButton(
                text = primaryText,
                onClick = onPrimary,
                variant = ButtonVariant.Primary,
                enabled = primaryEnabled,
                fullWidth = true,
            )
            HisabakButton(
                text = stringResource(R.string.restore_skip),
                onClick = onSkip,
                variant = ButtonVariant.Ghost,
                fullWidth = true,
            )
        }
    }
}
