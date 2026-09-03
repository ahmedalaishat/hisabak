package com.hisabak.feature.sms.presentation.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.shared.resources.Res
import com.hisabak.shared.resources.action_cancel
import com.hisabak.shared.resources.action_delete
import com.hisabak.shared.resources.common_delete_named
import com.hisabak.shared.resources.sms_template_badge_default
import com.hisabak.shared.resources.sms_template_badge_custom
import com.hisabak.shared.resources.sms_template_badge_learned
import com.hisabak.shared.resources.sms_template_delete_body
import com.hisabak.shared.resources.sms_template_delete_title
import com.hisabak.shared.resources.sms_template_new
import com.hisabak.shared.resources.sms_templates_empty_subtitle
import com.hisabak.shared.resources.sms_templates_empty_title
import com.hisabak.shared.resources.sms_templates_hint
import com.hisabak.ui.components.Badge
import com.hisabak.ui.components.BadgeTone
import com.hisabak.ui.components.ButtonVariant
import com.hisabak.ui.components.HisabakButton
import com.hisabak.ui.components.SurfaceCard
import com.hisabak.ui.icons.HugeIcons
import com.hisabak.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

@Composable
fun SmsTemplatesScreen(
    state: SmsTemplatesUiState,
    onAdd: () -> Unit,
    onOpen: (SmsTemplateId) -> Unit,
    onToggle: (SmsTemplateId, Boolean) -> Unit,
    onDeleteRequest: (SmsTemplateId) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.pageMargin, vertical = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            Text(
                text = stringResource(Res.string.sms_templates_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            HisabakButton(
                text = stringResource(Res.string.sms_template_new),
                onClick = onAdd,
                variant = ButtonVariant.Primary,
                leadingIcon = HugeIcons.Add,
                fullWidth = true,
            )
        }
        if (state.templates.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = Spacing.s8), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(Res.string.sms_templates_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.s2))
                    Text(
                        stringResource(Res.string.sms_templates_empty_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(state.templates, key = { it.id.value }) { row ->
            TemplateRowCard(
                row = row,
                onOpen = { onOpen(row.id) },
                onToggle = { onToggle(row.id, it) },
                onDelete = { onDeleteRequest(row.id) },
            )
        }
    }

    if (state.pendingDelete != null) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(Res.string.sms_template_delete_title)) },
            text = { Text(stringResource(Res.string.sms_template_delete_body)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun TemplateRowCard(
    row: SmsTemplatesUiState.TemplateRow,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onOpen).padding(Spacing.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(
                    label = stringResource(
                        when {
                            row.isDefault -> Res.string.sms_template_badge_default
                            // Learned rules read as "yours" too, but the user never wrote one —
                            // say where it came from so it isn't a mystery entry in the list.
                            row.derivedByAi -> Res.string.sms_template_badge_learned
                            else -> Res.string.sms_template_badge_custom
                        },
                    ),
                    tone = if (row.isDefault) BadgeTone.Neutral else BadgeTone.Info,
                )
                Spacer(Modifier.weight(1f))
                if (!row.isDefault) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            HugeIcons.DeleteOutline,
                            contentDescription = stringResource(Res.string.common_delete_named, row.pattern),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = row.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(Spacing.s2))
            Text(
                text = row.pattern,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (row.enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 3,
            )
        }
    }
}
