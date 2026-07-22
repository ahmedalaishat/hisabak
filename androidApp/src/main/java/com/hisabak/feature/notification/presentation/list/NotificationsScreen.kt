package com.hisabak.feature.notification.presentation.list

import com.hisabak.ui.icons.HugeIcons

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hisabak.R
import com.hisabak.feature.notification.domain.Notification
import com.hisabak.ui.components.CircleIconTile
import com.hisabak.ui.components.EmptyStatePanel
import com.hisabak.ui.components.SkeletonRowList
import com.hisabak.ui.components.SurfaceCard
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.Spacing
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    onRowClick: (NotificationRow) -> Unit,
    onDismiss: (NotificationRow) -> Unit,
    onMarkAllRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s5),
        ) {
            SkeletonRowList(count = 6)
        }
        return
    }

    if (state.rows.isEmpty()) {
        EmptyStatePanel(
            modifier = modifier.fillMaxSize(),
            icon = HugeIcons.NotificationsNone,
            title = stringResource(R.string.notifications_empty_title),
            subtitle = stringResource(R.string.notifications_empty_subtitle),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Spacing.pageMargin,
            end = Spacing.pageMargin,
            top = Spacing.s4,
            bottom = Spacing.s8,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        if (state.hasUnread) {
            item(key = "mark-all") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = stringResource(R.string.notifications_mark_all_read),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onMarkAllRead)
                            .padding(Spacing.s2),
                    )
                }
            }
        }
        items(state.rows, key = { it.id.value }) { row ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) {
                        onDismiss(row)
                        true
                    } else {
                        false
                    }
                },
            )
            SwipeToDismissBox(
                state = dismissState,
                modifier = Modifier.animateItem(),
                backgroundContent = { DismissBackground() },
            ) {
                NotificationCard(row = row, onClick = { onRowClick(row) })
            }
        }
    }
}

@Composable
private fun NotificationCard(row: NotificationRow, onClick: () -> Unit) {
    val c = HisabakTheme.colors
    // Icon + tint reflect the kind of alert: amber for budget limits, green for the rest.
    val (icon, tileBg, tileFg) = when (row.type) {
        Notification.TYPE_CATEGORY_LIMIT ->
            Triple(HugeIcons.PriorityHigh, c.warningSoft, c.warning)
        Notification.TYPE_TRANSACTION_RECORDED ->
            Triple(HugeIcons.ReceiptLong, c.incomeSoft, MaterialTheme.colorScheme.primary)
        else ->
            Triple(HugeIcons.NotificationsNone, c.incomeSoft, MaterialTheme.colorScheme.primary)
    }
    SurfaceCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            CircleIconTile(
                icon = icon,
                background = tileBg,
                foreground = tileFg,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (row.isRead) FontWeight.Normal else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = row.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = relativeTime(row.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // relativeTime resolves localized strings.
                )
            }
            if (!row.isRead) {
                Box(
                    Modifier
                        .size(Spacing.s3)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun DismissBackground() {
    val c = HisabakTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(c.expenseSoft)
            .padding(horizontal = Spacing.cardPadding),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = stringResource(R.string.notifications_dismiss),
            style = MaterialTheme.typography.labelLarge,
            color = c.expense,
        )
    }
}

@Composable
private fun relativeTime(instant: Instant): String {
    val minutes = Duration.between(instant, Instant.now()).toMinutes()
    return when {
        minutes < 1 -> stringResource(R.string.time_just_now)
        minutes < 60 -> stringResource(R.string.time_minutes_ago, minutes.toInt())
        minutes < 60 * 24 -> stringResource(R.string.time_hours_ago, (minutes / 60).toInt())
        minutes < 60 * 24 * 7 -> stringResource(R.string.time_days_ago, (minutes / (60 * 24)).toInt())
        else -> DateTimeFormatter.ofPattern("d MMM")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }
}
