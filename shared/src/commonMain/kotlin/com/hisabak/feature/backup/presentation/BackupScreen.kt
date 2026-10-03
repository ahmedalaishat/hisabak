package com.hisabak.feature.backup.presentation

import com.hisabak.ui.icons.HugeIcons

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
import com.hisabak.ui.format.LocalDateFormatter
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisabak.shared.resources.*
import org.jetbrains.compose.resources.StringResource
import com.hisabak.core.domain.backup.AutoBackupPeriod
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.ui.components.ButtonVariant
import com.hisabak.ui.components.HisabakButton
import com.hisabak.ui.components.SurfaceCard
import com.hisabak.ui.components.dismissKeyboardOnGesture
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
    onExportFile: () -> Unit,
    onPickImportFile: () -> Unit,
    onConfirmImport: () -> Unit,
    onSubmitImportPassphrase: (String) -> Unit,
    onCancelImport: () -> Unit,
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
                kind = state.syncKind,
                phase = sync,
                onContinue = onDismissSync,
                onRetry = {
                    // A failed import is spent (its bytes are dropped), so retrying means a new file.
                    when (state.syncKind) {
                        SyncKind.Export -> onExportFile()
                        SyncKind.Import -> {
                            onDismissSync()
                            onPickImportFile()
                        }
                        else -> onBackupNow()
                    }
                },
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
                onExportFile = onExportFile,
                onPickImportFile = onPickImportFile,
            )
        }
    }

    when (val step = state.importStep) {
        ImportStep.Confirm -> ImportConfirmDialog(onConfirm = onConfirmImport, onDismiss = onCancelImport)
        is ImportStep.Passphrase -> ImportPassphraseDialog(
            wrong = step.error == BackupError.WrongPassphrase,
            onSubmit = onSubmitImportPassphrase,
            onDismiss = onCancelImport,
        )
        null -> Unit
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
    onExportFile: () -> Unit,
    onPickImportFile: () -> Unit,
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
                        text = stringResource(Res.string.backup_turn_on),
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

                    // Backup can be enabled with no account (connect declined/failed/revoked);
                    // without this the only reconnect path was toggling backup off and on.
                    if (state.account == null) {
                        HisabakButton(
                            text = stringResource(Res.string.backup_connect_account),
                            onClick = onConnectAccount,
                            fullWidth = true,
                        )
                    }
                    HisabakButton(
                        text = stringResource(Res.string.backup_now),
                        onClick = onBackupNow,
                        enabled = canBackupNow,
                        fullWidth = true,
                    )

                    SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                        SettingsRow(
                            icon = HugeIcons.Schedule,
                            title = stringResource(Res.string.backup_auto_title),
                            subtitle = if (state.period != AutoBackupPeriod.NEVER) {
                                stringResource(Res.string.backup_auto_hint)
                            } else {
                                null
                            },
                            value = stringResource(state.period.labelRes()),
                            onClick = { showPeriodSheet = true },
                        )
                        RowDivider()
                        SettingsRow(
                            icon = HugeIcons.Lock,
                            title = stringResource(Res.string.backup_encrypt),
                            subtitle = stringResource(Res.string.backup_encrypt_hint),
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
                                title = stringResource(Res.string.backup_passphrase),
                                subtitle = stringResource(
                                    if (state.passphraseSet) Res.string.backup_passphrase_set else Res.string.backup_passphrase_not_set,
                                ),
                                value = stringResource(
                                    if (state.passphraseSet) Res.string.backup_change else Res.string.backup_set,
                                ),
                                onClick = { showPassphraseSheet = true },
                            )
                        }
                    }

                    Text(
                        text = stringResource(Res.string.backup_turn_off),
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

        // Outside the Drive gate: a file needs no account, no network, and no backup switch.
        if (state.ready) {
            BackupFileSection(state = state, onExportFile = onExportFile, onPickImportFile = onPickImportFile)
        }
    }

    if (showTurnOff) {
        AlertDialog(
            onDismissRequest = { showTurnOff = false },
            title = { Text(stringResource(Res.string.backup_turn_off_title)) },
            text = { Text(stringResource(Res.string.backup_turn_off_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showTurnOff = false
                    onSetEnabled(false)
                }) { Text(stringResource(Res.string.backup_turn_off)) }
            },
            dismissButton = {
                TextButton(onClick = { showTurnOff = false }) { Text(stringResource(Res.string.action_cancel)) }
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
            BackupPrompt.PassphraseChanged -> Res.string.backup_pass_changed_title
            BackupPrompt.EncryptionOn -> Res.string.backup_enc_on_title
            BackupPrompt.EncryptionOff -> Res.string.backup_enc_off_title
        }
        val messageRes = when (prompt) {
            BackupPrompt.PassphraseChanged -> Res.string.backup_pass_changed_message
            BackupPrompt.EncryptionOn -> Res.string.backup_enc_on_message
            BackupPrompt.EncryptionOff -> Res.string.backup_enc_off_message
        }
        AlertDialog(
            onDismissRequest = { backupPrompt = null },
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(messageRes)) },
            confirmButton = {
                TextButton(onClick = {
                    backupPrompt = null
                    onBackupNow()
                }) { Text(stringResource(Res.string.backup_now)) }
            },
            dismissButton = {
                TextButton(onClick = { backupPrompt = null }) {
                    Text(stringResource(Res.string.backup_pass_changed_later))
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
private fun BackupFileSection(
    state: BackupUiState,
    onExportFile: () -> Unit,
    onPickImportFile: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        Text(
            text = stringResource(Res.string.backup_file_title).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.s1),
        )
        SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
            SettingsRow(
                icon = HugeIcons.Download,
                title = stringResource(Res.string.backup_file_export),
                // Says which file they'll get: a plain one is readable by whoever holds it.
                subtitle = stringResource(
                    if (state.encryptionEnabled) Res.string.backup_file_export_encrypted else Res.string.backup_file_export_plain,
                ),
                onClick = if (state.exporting) null else onExportFile,
            )
            RowDivider()
            SettingsRow(
                icon = HugeIcons.Inbox,
                title = stringResource(Res.string.backup_file_import),
                subtitle = stringResource(Res.string.backup_file_import_hint),
                onClick = onPickImportFile,
            )
        }
    }
}

@Composable
private fun ImportConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.backup_file_import_confirm_title)) },
        text = { Text(stringResource(Res.string.backup_file_import_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.backup_file_import_confirm_action), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun ImportPassphraseDialog(wrong: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var passphrase by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.restore_passphrase_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                Text(stringResource(Res.string.backup_file_import_passphrase_message))
                PassphraseField(
                    value = passphrase,
                    onChange = { passphrase = it },
                    label = stringResource(Res.string.backup_passphrase),
                    error = if (wrong) stringResource(Res.string.backup_err_wrong_passphrase) else null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(passphrase) }, enabled = passphrase.isNotEmpty()) {
                Text(stringResource(Res.string.restore_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
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
            text = stringResource(Res.string.backup_header_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s5),
        )
        Text(
            text = stringResource(Res.string.backup_header_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s2),
        )
    }
}

@Composable
private fun LastBackupCard(state: BackupUiState) {
    val formatter = LocalDateFormatter.current
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
                    text = stringResource(Res.string.backup_never),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    val date = remember(b.modifiedAtMillis, formatter) {
                        formatter.relativeDateTime(b.modifiedAtMillis)
                    }
                    Text(
                        text = stringResource(Res.string.backup_last_line, date),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.backup_size_line, formatter.shortFileSize(b.sizeBytes)),
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
            stringResource(Res.string.backup_point_encrypted_title),
            stringResource(Res.string.backup_point_encrypted_sub),
        )
        Benefit(
            HugeIcons.CloudDownload,
            stringResource(Res.string.backup_point_restore_title),
            stringResource(Res.string.backup_point_restore_sub),
        )
        Benefit(
            HugeIcons.Schedule,
            stringResource(Res.string.backup_point_auto_title),
            stringResource(Res.string.backup_point_auto_sub),
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
            // Built here, inside the sheet's own composition: a sheet is a separate window with
            // its own focus owner, so a modifier constructed at the call site would clear focus
            // on the parent window instead and do nothing.
            modifier = Modifier
                .dismissKeyboardOnGesture()
                .fillMaxWidth()
                .padding(horizontal = Spacing.pageMargin)
                .padding(bottom = Spacing.sectionGap),
            verticalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            Text(
                stringResource(Res.string.backup_passphrase_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(Res.string.backup_passphrase_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Res.string.backup_passphrase_upcoming),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PassphraseField(
                value = passphrase,
                onChange = { passphrase = it },
                label = stringResource(Res.string.backup_passphrase),
                error = if (tooShort) stringResource(Res.string.backup_passphrase_too_short) else null,
            )
            PassphraseField(
                value = confirm,
                onChange = { confirm = it },
                label = stringResource(Res.string.backup_passphrase_confirm),
                error = if (mismatch) stringResource(Res.string.backup_passphrase_mismatch) else null,
            )
            HisabakButton(
                text = stringResource(Res.string.backup_passphrase_save),
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
            // Built here, inside the sheet's own composition: a sheet is a separate window with
            // its own focus owner, so a modifier constructed at the call site would clear focus
            // on the parent window instead and do nothing.
            modifier = Modifier
                .dismissKeyboardOnGesture()
                .fillMaxWidth()
                .padding(bottom = Spacing.sectionGap),
        ) {
            Text(
                stringResource(Res.string.backup_auto_sheet_title),
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

private fun AutoBackupPeriod.labelRes(): StringResource = when (this) {
    AutoBackupPeriod.NEVER -> Res.string.backup_auto_never
    AutoBackupPeriod.DAILY -> Res.string.backup_auto_daily
    AutoBackupPeriod.WEEKLY -> Res.string.backup_auto_weekly
    AutoBackupPeriod.MONTHLY -> Res.string.backup_auto_monthly
}

internal fun BackupError.messageRes(): StringResource = when (this) {
    BackupError.WrongPassphrase -> Res.string.backup_err_wrong_passphrase
    BackupError.Corrupt -> Res.string.backup_err_corrupt
    BackupError.Empty -> Res.string.backup_err_empty
    BackupError.AuthRequired -> Res.string.backup_err_auth
    BackupError.Network -> Res.string.backup_err_network
    BackupError.PassphraseRequired -> Res.string.backup_err_no_passphrase
    BackupError.FileAccess -> Res.string.backup_err_file_access
    is BackupError.UnsupportedVersion -> Res.string.backup_err_unsupported
}
