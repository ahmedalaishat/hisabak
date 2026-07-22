package com.hisabak.feature.sms.presentation.inbox

import com.hisabak.ui.icons.HugeIcons

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisabak.R
import com.hisabak.core.common.Money
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.ui.components.AmountText
import com.hisabak.ui.components.compactAmount
import com.hisabak.ui.components.AmountTone
import com.hisabak.ui.components.Badge
import com.hisabak.ui.components.BadgeTone
import com.hisabak.ui.components.SkeletonRowList
import com.hisabak.ui.components.EmptyStatePanel
import com.hisabak.ui.components.HisabakButton
import com.hisabak.ui.components.ButtonVariant
import com.hisabak.ui.components.PrimaryPillButton
import com.hisabak.ui.components.SearchField
import com.hisabak.ui.components.SectionHeader
import com.hisabak.ui.components.SmsStatus
import com.hisabak.ui.components.StatusChip
import com.hisabak.ui.components.SurfaceCard
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.Sizing
import com.hisabak.ui.theme.Spacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsInboxScreen(
    state: SmsInboxUiState,
    snackbarHostState: SnackbarHostState,
    autoImportAvailable: Boolean,
    onSearchChange: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onIngest: () -> Unit,
    onDelete: (SmsMessageId) -> Unit,
    onEnableAutoImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.pageMargin, vertical = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            if (autoImportAvailable) {
                item { AutoImportBanner(granted = state.autoImportGranted, onEnable = onEnableAutoImport) }
            }
            item { PasteParseCard(draft = state.draftBody, preview = state.draftPreview, isProcessing = state.isProcessing, onDraftChange = onDraftChange, onIngest = onIngest) }
            item {
                SearchField(
                    value = state.search,
                    onValueChange = onSearchChange,
                    placeholder = stringResource(R.string.sms_search_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { SectionHeader(title = stringResource(R.string.sms_recent_section)) }
            if (state.isLoading) {
                item { SkeletonRowList(count = 5) }
            } else if (state.rows.isEmpty()) {
                item {
                    EmptyStatePanel(
                        icon = HugeIcons.Inbox,
                        title = stringResource(R.string.sms_empty_title),
                        subtitle = when {
                            state.search.isNotBlank() -> stringResource(R.string.common_no_matches_subtitle, state.search)
                            autoImportAvailable -> stringResource(R.string.sms_empty_auto)
                            else -> stringResource(R.string.sms_empty_manual)
                        },
                    )
                }
            } else {
                items(state.rows, key = { it.id.value }) { row ->
                    SmsRowCard(
                        row = row,
                        onImport = { onIngest() },
                        onDelete = { onDelete(row.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(Spacing.pageMargin),
        )
    }
}

@Composable
private fun AutoImportBanner(granted: Boolean, onEnable: () -> Unit) {
    val colors = HisabakTheme.colors
    // Tinted banner with a leading status icon: green/active vs amber/disabled.
    val tint = if (granted) colors.incomeSoft else colors.warningSoft
    val accent = if (granted) colors.income else colors.warning
    val icon = if (granted) HugeIcons.CheckCircle else HugeIcons.ErrorOutline
    SurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = tint,
        borderColor = Color.Transparent,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(Sizing.icon),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Text(
                    stringResource(if (granted) R.string.sms_auto_active_title else R.string.sms_auto_disabled_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(if (granted) R.string.sms_auto_active_body else R.string.sms_auto_disabled_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!granted) {
                HisabakButton(
                    text = stringResource(R.string.sms_enable),
                    onClick = onEnable,
                    variant = ButtonVariant.Primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasteParseCard(
    draft: String,
    preview: ParsedSmsData?,
    isProcessing: Boolean,
    onDraftChange: (String) -> Unit,
    onIngest: () -> Unit,
) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.sms_paste_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.sectionTitleGap))
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = {
                Text(
                    stringResource(R.string.sms_paste_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            minLines = 2,
            shape = MaterialTheme.shapes.medium,
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.cardGap))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Live preview of what the draft will import — brand + amount — before tapping.
            if (preview != null) {
                Badge(label = preview.brandName.orEmpty(), tone = BadgeTone.Info, dot = true)
                preview.amount?.let { amount ->
                    Spacer(Modifier.width(Spacing.s3))
                    AmountText(
                        value = amount.amountMinor / 100.0,
                        tone = AmountTone.Expense,
                        showSign = false,
                        size = 15.sp,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            HisabakButton(
                text = stringResource(if (isProcessing) R.string.sms_parsing else R.string.sms_parse_import),
                onClick = onIngest,
                variant = ButtonVariant.Secondary,
                enabled = !isProcessing,
            )
        }
    }
}

@Composable
private fun SmsRowCard(
    row: SmsInboxRow,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = when {
        row.isLinked -> SmsStatus.Linked
        row.parsedAmount != null -> SmsStatus.Parsed
        else -> SmsStatus.Unparsed
    }
    val isParsed = status == SmsStatus.Parsed

    SurfaceCard(modifier = modifier.fillMaxWidth()) {
        // Top row: date/time left, StatusChip right
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatDate(row.receivedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusChip(status = status)
        }
        Spacer(Modifier.height(Spacing.sectionTitleGap))
        // SMS body capped at 2 lines
        Text(
            row.body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        // Parsed detail row
        if (isParsed && row.parsedBrand != null && row.parsedAmount != null) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.parsedBrand,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    AmountText(
                        value = row.parsedAmount.amountMinor / 100.0,
                        tone = AmountTone.Expense,
                        showSign = false,
                        size = 18.sp,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PrimaryPillButton(
                        text = stringResource(R.string.sms_import),
                        onClick = onImport,
                        leadingIcon = HugeIcons.Download,
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(Sizing.controlHeightSm)) {
                        Icon(
                            HugeIcons.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(Spacing.s3))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDelete, modifier = Modifier.size(Sizing.controlHeightSm)) {
                    Icon(
                        HugeIcons.DeleteOutline,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

internal fun formatMoney(money: Money): String {
    val sign = if (money.amountMinor < 0) "-" else ""
    return "$sign${money.currency.code} ${compactAmount(abs(money.amountMinor) / 100.0)}"
}

internal fun formatDate(instant: java.time.Instant): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
