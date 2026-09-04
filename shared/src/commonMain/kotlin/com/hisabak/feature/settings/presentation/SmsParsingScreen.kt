package com.hisabak.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.core.common.AppConfig
import com.hisabak.shared.resources.Res
import com.hisabak.shared.resources.settings_auto_confirm
import com.hisabak.shared.resources.settings_auto_confirm_hint
import com.hisabak.shared.resources.settings_remote_parse
import com.hisabak.shared.resources.settings_remote_parse_hint
import com.hisabak.shared.resources.settings_sms_templates
import com.hisabak.shared.resources.settings_sms_templates_hint
import com.hisabak.ui.icons.HugeIcons
import com.hisabak.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings → SMS parsing. Three decisions that build on each other, in the order they apply: which
 * formats are recognised, whether an unrecognised one may be sent away to be read, and whether the
 * answer may be acted on without asking.
 *
 * No platform touchpoints, so unlike the Settings screen itself this needs no per-platform Route.
 */
@Composable
fun SmsParsingRoute(
    onOpenTemplates: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
    appConfig: AppConfig = koinInject(),
) {
    val remoteParseEnabled by viewModel.remoteParseEnabled.collectAsStateWithLifecycle(initialValue = false)
    val autoConfirmEnabled by viewModel.autoConfirmEnabled.collectAsStateWithLifecycle(initialValue = false)

    SmsParsingScreen(
        remoteParseEnabled = remoteParseEnabled,
        // No service configured in this build -> the row stays hidden rather than offering an
        // opt-in that would do nothing.
        remoteParseSupported = appConfig.hasParseService,
        autoConfirmEnabled = autoConfirmEnabled,
        onRemoteParseChange = viewModel::setRemoteParseEnabled,
        onAutoConfirmChange = viewModel::setAutoConfirmEnabled,
        onOpenTemplates = onOpenTemplates,
        modifier = modifier,
    )
}

@Composable
fun SmsParsingScreen(
    remoteParseEnabled: Boolean,
    remoteParseSupported: Boolean,
    autoConfirmEnabled: Boolean,
    onRemoteParseChange: (Boolean) -> Unit,
    onAutoConfirmChange: (Boolean) -> Unit,
    onOpenTemplates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.pageMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        SettingCard(
            icon = HugeIcons.Message,
            title = stringResource(Res.string.settings_sms_templates),
            hint = stringResource(Res.string.settings_sms_templates_hint),
            onClick = onOpenTemplates,
            trailing = {
                Icon(
                    imageVector = HugeIcons.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        if (remoteParseSupported) {
            SettingCard(
                // Not CloudSync: that is the Backup icon, and this is not a sync.
                icon = HugeIcons.Brain,
                title = stringResource(Res.string.settings_remote_parse),
                hint = stringResource(Res.string.settings_remote_parse_hint),
                trailing = { Switch(checked = remoteParseEnabled, onCheckedChange = onRemoteParseChange) },
            )
        }
        SettingCard(
            icon = HugeIcons.CheckCircle,
            title = stringResource(Res.string.settings_auto_confirm),
            hint = stringResource(Res.string.settings_auto_confirm_hint),
            trailing = { Switch(checked = autoConfirmEnabled, onCheckedChange = onAutoConfirmChange) },
        )
    }
}
