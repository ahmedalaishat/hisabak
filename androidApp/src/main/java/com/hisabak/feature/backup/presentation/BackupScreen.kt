package com.hisabak.feature.backup.presentation

import com.hisabak.ui.icons.HugeIcons

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hisabak.R
import com.hisabak.core.domain.backup.AutoBackupPeriod
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.ui.components.ButtonVariant
import com.hisabak.ui.components.HisabakButton
import com.hisabak.ui.components.SurfaceCard
import com.hisabak.ui.theme.Motion
import com.hisabak.ui.theme.Spacing

private const val MIN_PASSPHRASE_LENGTH = 8

/** Why we're offering to re-back-up: passphrase changed, encryption turned on, or encryption off. */
private enum class BackupPrompt { PassphraseChanged, EncryptionOn, EncryptionOff }

@Composable
fun BackupScreen(
    state: BackupUiState,
    onSetEnabled: (Boolean) -> Unit,
    onSetEncryptionEnabled: (Boolean) -> Unit,
    onSetPassphrase: (String) -> Unit,
    onSetPeriod: (AutoBackupPeriod) -> Unit,
    onConnectAccount: () -> Unit,
    onBackupNow: () -> Unit,
    onClearError: () -> Unit,
    onDismissSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state.sync,
        // Settings → Sync slides forward (in from the end); Sync → Settings slides back. RTL-aware.
        transitionSpec = {
            val towards = if (targetState != null) SlideDirection.Start else SlideDirection.End
            (slideIntoContainer(towards, tween(Motion.Duration.Slow, easing = Motion.Easing.Standard)) +
                fadeIn(tween(Motion.Duration.Base))) togetherWith
                (slideOutOfContainer(towards, tween(Motion.Duration.Slow, easing = Motion.Easing.Standard)) +
                    fadeOut(tween(Motion.Duration.Base)))
        },
        label = "backupSync",
        modifier = modifier,
    ) { sync ->
        if (sync != null) {
            SyncScreen(
                kind = SyncKind.BackUp,
                phase = sync,
                onContinue = onDismissSync,
                onRetry = onBackupNow,
                onClose = onDismissSync,
            )
        } else {
            BackupSettings(
                state = state,
                onSetEnabled = onSetEnabled,
                onSetEncryptionEnabled = onSetEncryptionEnabled,
                onSetPassphrase = onSetPassphrase,
                onSetPeriod = onSetPeriod,
                onConnectAccount = onConnectAccount,
                onBackupNow = onBackupNow,
                onClearError = onClearError,
            )
        }
    }
}

@Composable
private fun BackupSettings(
    state: BackupUiState,
    onSetEnabled: (Boolean) -> Unit,
    onSetEncryptionEnabled: (Boolean) -> Unit,
    onSetPassphrase: (String) -> Unit,
    onSetPeriod: (AutoBackupPeriod) -> Unit,
    onConnectAccount: () -> Unit,
    onBackupNow: () -> Unit,
    onClearError: () -> Unit,
) {
    var showPassphraseSheet by rememberSaveable { mutableStateOf(false) }
    var showPeriodSheet by rememberSaveable { mutableStateOf(false) }
    var showTurnOff by rememberSaveable { mutableStateOf(false) }
    // After a change that affects future backups (new passphrase / encryption off), offer to re-back-up.
    var backupPrompt by remember { mutableStateOf<BackupPrompt?>(null) }
    // Shows the encryption toggle "on" while the set-passphrase sheet is open, before it's persisted.
    var pendingEncrypt by remember { mutableStateOf(false) }

    val canBackupNow = state.account != null && (!state.encryptionEnabled || state.passphraseSet)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.pageMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap),
    ) {
        BackupHeader()

        state.error?.let { ErrorBanner(it, onClearError) }

        if (state.ready) AnimatedContent(
            targetState = state.enabled,
            transitionSpec = {
                (fadeIn(tween(Motion.Duration.Slow)) +
                    slideInVertically(tween(Motion.Duration.Slow, easing = Motion.Easing.Standard)) { it / 10 }) togetherWith
                    fadeOut(tween(Motion.Duration.Base)) using SizeTransform(clip = false)
            },
            label = "backupEnabled",
        ) { enabled ->
            if (!enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap)) {
                    BenefitsList()
                    HisabakButton(
                        text = stringResource(R.string.backup_turn_on),
                        onClick = {
                            onSetEnabled(true)
                            if (state.account == null) onConnectAccount()
                        },
                        fullWidth = true,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap)) {
                    LastBackupCard(state = state)

                    HisabakButton(
                        text = stringResource(R.string.backup_now),
                        onClick = onBackupNow,
                        enabled = canBackupNow,
                        fullWidth = true,
                    )

                    SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                        SettingsRow(
                            icon = HugeIcons.Schedule,
                            title = stringResource(R.string.backup_auto_title),
                            subtitle = if (state.period != AutoBackupPeriod.NEVER) {
                                stringResource(R.string.backup_auto_hint)
                            } else {
                                null
                            },
                            value = stringResource(state.period.labelRes()),
                            onClick = { showPeriodSheet = true },
                        )
                        RowDivider()
                        SettingsRow(
                            icon = HugeIcons.Lock,
                            title = stringResource(R.string.backup_encrypt),
                            subtitle = stringResource(R.string.backup_encrypt_hint),
                        ) {
                            Switch(
                                checked = state.encryptionEnabled || pendingEncrypt,
                                onCheckedChange = { value ->
                                    if (value) {
                                        // Don't persist encryption-on yet; only the passphrase save does.
                                        pendingEncrypt = true
                                        showPassphraseSheet = true
                                    } else {
                                        onSetEncryptionEnabled(false)
                                        pendingEncrypt = false
                                        // Future backups won't be encrypted — offer to update Drive now.
                                        if (state.account != null) backupPrompt = BackupPrompt.EncryptionOff
                                    }
                                },
                            )
                        }
                        if (state.encryptionEnabled) {
                            RowDivider()
                            SettingsRow(
                                icon = HugeIcons.Key,
                                title = stringResource(R.string.backup_passphrase),
                                subtitle = stringResource(
                                    if (state.passphraseSet) R.string.backup_passphrase_set else R.string.backup_passphrase_not_set,
                                ),
                                value = stringResource(
                                    if (state.passphraseSet) R.string.backup_change else R.string.backup_set,
                                ),
                                onClick = { showPassphraseSheet = true },
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.backup_turn_off),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { showTurnOff = true }
                            .padding(Spacing.s4),
                    )
                }
            }
        }
    }

    if (showTurnOff) {
        AlertDialog(
            onDismissRequest = { showTurnOff = false },
            title = { Text(stringResource(R.string.backup_turn_off_title)) },
            text = { Text(stringResource(R.string.backup_turn_off_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showTurnOff = false
                    onSetEnabled(false)
                }) { Text(stringResource(R.string.backup_turn_off)) }
            },
            dismissButton = {
                TextButton(onClick = { showTurnOff = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showPassphraseSheet) {
        PassphraseSheet(
            onDismiss = {
                showPassphraseSheet = false
                // Backed out without saving → encryption stays off (it was never persisted on).
                pendingEncrypt = false
                if (state.encryptionEnabled && !state.passphraseSet) onSetEncryptionEnabled(false)
            },
            onSave = {
                val wasChange = state.passphraseSet // already had one → this is a change vs first set
                onSetPassphrase(it) // persists the passphrase and turns encryption on together
                pendingEncrypt = false
                showPassphraseSheet = false
                if (state.account != null) {
                    backupPrompt = if (wasChange) BackupPrompt.PassphraseChanged else BackupPrompt.EncryptionOn
                }
            },
        )
    }

    backupPrompt?.let { prompt ->
        val titleRes = when (prompt) {
            BackupPrompt.PassphraseChanged -> R.string.backup_pass_changed_title
            BackupPrompt.EncryptionOn -> R.string.backup_enc_on_title
            BackupPrompt.EncryptionOff -> R.string.backup_enc_off_title
        }
        val messageRes = when (prompt) {
            BackupPrompt.PassphraseChanged -> R.string.backup_pass_changed_message
            BackupPrompt.EncryptionOn -> R.string.backup_enc_on_message
            BackupPrompt.EncryptionOff -> R.string.backup_enc_off_message
        }
        AlertDialog(
            onDismissRequest = { backupPrompt = null },
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(messageRes)) },
            confirmButton = {
                TextButton(onClick = {
                    backupPrompt = null
                    onBackupNow()
                }) { Text(stringResource(R.string.backup_now)) }
            },
            dismissButton = {
                TextButton(onClick = { backupPrompt = null }) {
                    Text(stringResource(R.string.backup_pass_changed_later))
                }
            },
        )
    }
    if (showPeriodSheet) {
        PeriodSheet(
            selected = state.period,
            onSelect = {
                onSetPeriod(it)
                showPeriodSheet = false
            },
            onDismiss = { showPeriodSheet = false },
        )
    }
}

@Composable
private fun BackupHeader() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                HugeIcons.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Text(
            text = stringResource(R.string.backup_header_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s5),
        )
        Text(
            text = stringResource(R.string.backup_header_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s2),
        )
    }
}

@Composable
private fun LastBackupCard(state: BackupUiState) {
    val context = LocalContext.current
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s5)) {
            Icon(
                HugeIcons.CloudSync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            val b = state.lastBackup
            if (b == null) {
                Text(
                    text = stringResource(R.string.backup_never),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    val date = remember(b.modifiedAtMillis) {
                        DateUtils.getRelativeDateTimeString(
                            context, b.modifiedAtMillis,
                            DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0,
                        ).toString()
                    }
                    Text(
                        text = stringResource(R.string.backup_last_line, date),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.backup_size_line, Formatter.formatShortFileSize(context, b.sizeBytes)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BenefitsList() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s5)) {
        Benefit(
            HugeIcons.Lock,
            stringResource(R.string.backup_point_encrypted_title),
            stringResource(R.string.backup_point_encrypted_sub),
        )
        Benefit(
            HugeIcons.CloudDownload,
            stringResource(R.string.backup_point_restore_title),
            stringResource(R.string.backup_point_restore_sub),
        )
        Benefit(
            HugeIcons.Schedule,
            stringResource(R.string.backup_point_auto_title),
            stringResource(R.string.backup_point_auto_sub),
        )
    }
}

@Composable
private fun Benefit(icon: ImageVector, title: String, subtitle: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s4)) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(Spacing.s3))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.cardPadding, vertical = Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                value?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    HugeIcons.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = Spacing.cardPadding + 22.dp + Spacing.s4),
    )
}

@Composable
private fun ErrorBanner(error: BackupError, onDismiss: () -> Unit) {
    SurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.errorContainer,
        onClick = onDismiss,
    ) {
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassphraseSheet(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var passphrase by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val tooShort = passphrase.isNotEmpty() && passphrase.length < MIN_PASSPHRASE_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != passphrase
    val canSave = passphrase.length >= MIN_PASSPHRASE_LENGTH && passphrase == confirm

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.pageMargin)
                .padding(bottom = Spacing.sectionGap),
            verticalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            Text(
                stringResource(R.string.backup_passphrase_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.backup_passphrase_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.backup_passphrase_upcoming),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PassphraseField(
                value = passphrase,
                onChange = { passphrase = it },
                label = stringResource(R.string.backup_passphrase),
                error = if (tooShort) stringResource(R.string.backup_passphrase_too_short) else null,
            )
            PassphraseField(
                value = confirm,
                onChange = { confirm = it },
                label = stringResource(R.string.backup_passphrase_confirm),
                error = if (mismatch) stringResource(R.string.backup_passphrase_mismatch) else null,
            )
            HisabakButton(
                text = stringResource(R.string.backup_passphrase_save),
                onClick = { onSave(passphrase) },
                enabled = canSave,
                fullWidth = true,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSheet(
    selected: AutoBackupPeriod,
    onSelect: (AutoBackupPeriod) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.sectionGap),
        ) {
            Text(
                stringResource(R.string.backup_auto_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.pageMargin, vertical = Spacing.s3),
            )
            AutoBackupPeriod.entries.forEach { period ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = period == selected, onClick = { onSelect(period) })
                        .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                ) {
                    RadioButton(selected = period == selected, onClick = { onSelect(period) })
                    Text(
                        stringResource(period.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun PassphraseField(value: String, onChange: (String) -> Unit, label: String, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun AutoBackupPeriod.labelRes(): Int = when (this) {
    AutoBackupPeriod.NEVER -> R.string.backup_auto_never
    AutoBackupPeriod.DAILY -> R.string.backup_auto_daily
    AutoBackupPeriod.WEEKLY -> R.string.backup_auto_weekly
    AutoBackupPeriod.MONTHLY -> R.string.backup_auto_monthly
}

internal fun BackupError.messageRes(): Int = when (this) {
    BackupError.WrongPassphrase -> R.string.backup_err_wrong_passphrase
    BackupError.Corrupt -> R.string.backup_err_corrupt
    BackupError.Empty -> R.string.backup_err_empty
    BackupError.AuthRequired -> R.string.backup_err_auth
    BackupError.Network -> R.string.backup_err_network
    BackupError.PassphraseRequired -> R.string.backup_err_no_passphrase
    is BackupError.UnsupportedVersion -> R.string.backup_err_unsupported
}
