package com.hisabak.feature.sms.presentation.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hisabak.feature.sms.domain.template.TagRole
import com.hisabak.feature.sms.domain.template.TemplateValidationError
import com.hisabak.shared.resources.Res
import com.hisabak.shared.resources.action_save
import com.hisabak.shared.resources.action_saving
import com.hisabak.shared.resources.sms_template_default_readonly
import com.hisabak.shared.resources.sms_template_enable
import com.hisabak.shared.resources.sms_template_enable_import
import com.hisabak.shared.resources.sms_template_error_anchor
import com.hisabak.shared.resources.sms_template_exists
import com.hisabak.shared.resources.sms_template_exists_disabled
import com.hisabak.shared.resources.sms_template_error_amount_invalid
import com.hisabak.shared.resources.sms_template_error_amount_missing
import com.hisabak.shared.resources.sms_template_matches
import com.hisabak.shared.resources.sms_template_matches_conflicts
import com.hisabak.shared.resources.sms_template_pattern_label
import com.hisabak.shared.resources.sms_template_preview_amount
import com.hisabak.shared.resources.sms_template_preview_brand
import com.hisabak.shared.resources.sms_template_preview_date
import com.hisabak.shared.resources.sms_template_preview_label
import com.hisabak.shared.resources.sms_template_role_amount
import com.hisabak.shared.resources.sms_template_role_brand
import com.hisabak.shared.resources.sms_template_role_date
import com.hisabak.shared.resources.sms_template_role_skip
import com.hisabak.shared.resources.sms_template_role_time
import com.hisabak.shared.resources.sms_template_sample_hint
import com.hisabak.shared.resources.sms_template_sample_label
import com.hisabak.shared.resources.sms_template_save
import com.hisabak.shared.resources.sms_template_save_import
import com.hisabak.shared.resources.sms_template_tokens_hint
import com.hisabak.ui.components.ButtonVariant
import com.hisabak.ui.components.ColoredFilterChip
import com.hisabak.ui.components.HisabakButton
import com.hisabak.ui.components.NoticeCard
import com.hisabak.ui.components.NoticeTone
import com.hisabak.ui.components.SurfaceCard
import com.hisabak.ui.components.localizedFormatArg
import com.hisabak.ui.components.tintPairForColor
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmsTemplateEditScreen(
    state: SmsTemplateEditUiState,
    onSampleChange: (String) -> Unit,
    onRoleSelect: (TagRole) -> Unit,
    onTokenTap: (Int) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s5),
    ) {
        if (state.isDefaultTemplate) {
            NoticeCard(
                text = stringResource(Res.string.sms_template_default_readonly),
                tone = NoticeTone.Info,
            )
            PatternCard(state.pattern)
            return@Column
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sectionTitleGap)) {
            Text(
                text = stringResource(Res.string.sms_template_sample_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.sample,
                onValueChange = onSampleChange,
                placeholder = { Text(stringResource(Res.string.sms_template_sample_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.tokens.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sectionTitleGap)) {
                Text(
                    text = stringResource(Res.string.sms_template_tokens_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    TagRole.entries.forEach { role ->
                        ColoredFilterChip(
                            label = role.label(),
                            colorKey = role.colorKey(),
                            selected = state.activeRole == role,
                            onClick = { onRoleSelect(role) },
                        )
                    }
                }
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.s3),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
                    ) {
                        state.tokens.forEachIndexed { index, token ->
                            TokenChip(token = token, onTap = { onTokenTap(index) })
                        }
                    }
                }
            }
        }

        if (state.pattern.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sectionTitleGap)) {
                Text(
                    text = stringResource(Res.string.sms_template_preview_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.s4), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        PreviewLine(stringResource(Res.string.sms_template_preview_amount), state.previewAmount)
                        PreviewLine(stringResource(Res.string.sms_template_preview_brand), state.previewBrand)
                        PreviewLine(stringResource(Res.string.sms_template_preview_date), state.previewDate)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sectionTitleGap)) {
                Text(
                    text = stringResource(Res.string.sms_template_pattern_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PatternCard(state.pattern)
            }

            state.inboxPreview?.let { preview ->
                if (preview.conflicts > 0) {
                    NoticeCard(
                        text = stringResource(
                            Res.string.sms_template_matches_conflicts,
                            localizedFormatArg(preview.matches),
                            localizedFormatArg(preview.conflicts),
                        ),
                        tone = NoticeTone.Warning,
                    )
                } else if (preview.matches > 0) {
                    NoticeCard(
                        text = stringResource(Res.string.sms_template_matches, localizedFormatArg(preview.matches)),
                        tone = NoticeTone.Info,
                    )
                }
            }
        }

        state.duplicate?.let { duplicate ->
            NoticeCard(
                text = stringResource(
                    if (duplicate.enabled) Res.string.sms_template_exists
                    else Res.string.sms_template_exists_disabled,
                ),
                tone = NoticeTone.Info,
            )
        }

        state.validationError?.let { error ->
            NoticeCard(
                text = stringResource(
                    when (error) {
                        TemplateValidationError.MissingAmount -> Res.string.sms_template_error_amount_missing
                        TemplateValidationError.InvalidAmount -> Res.string.sms_template_error_amount_invalid
                        TemplateValidationError.InsufficientAnchor -> Res.string.sms_template_error_anchor
                    },
                ),
                tone = NoticeTone.Error,
            )
        }
        state.generalError?.let {
            NoticeCard(text = it, tone = NoticeTone.Error)
        }

        HisabakButton(
            text = stringResource(
                when {
                    state.isSaving -> Res.string.action_saving
                    state.duplicate?.enabled == false && state.importsSample ->
                        Res.string.sms_template_enable_import
                    state.duplicate?.enabled == false -> Res.string.sms_template_enable
                    state.importsSample -> Res.string.sms_template_save_import
                    else -> Res.string.sms_template_save
                },
            ),
            onClick = onSave,
            variant = ButtonVariant.Primary,
            enabled = state.canSave,
            fullWidth = true,
        )
        Spacer(Modifier.height(Spacing.s6))
    }
}

@Composable
private fun PatternCard(pattern: String) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = pattern,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.s4),
        )
    }
}

@Composable
private fun PreviewLine(label: String, value: String?) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Spacing.s2),
        )
        Text(
            text = value ?: "—",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TokenChip(token: SmsTemplateEditUiState.Token, onTap: () -> Unit) {
    val (bg, fg) = if (token.role != null) {
        tintPairForColor(token.role.colorKey())
    } else {
        HisabakTheme.colors.surfaceSunken to MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = token.text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onTap)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun TagRole.label(): String = stringResource(
    when (this) {
        TagRole.AMOUNT -> Res.string.sms_template_role_amount
        TagRole.BRAND -> Res.string.sms_template_role_brand
        TagRole.DATE -> Res.string.sms_template_role_date
        TagRole.TIME -> Res.string.sms_template_role_time
        TagRole.SKIP -> Res.string.sms_template_role_skip
    },
)

private fun TagRole.colorKey(): String = when (this) {
    TagRole.AMOUNT -> "green"
    TagRole.BRAND -> "blue"
    TagRole.DATE -> "orange"
    TagRole.TIME -> "teal"
    TagRole.SKIP -> "gray"
}
