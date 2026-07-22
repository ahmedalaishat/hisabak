package com.hisabak.ui.components

import com.hisabak.ui.icons.HugeIcons

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hisabak.R
import com.hisabak.ui.theme.Sizing
import com.hisabak.ui.theme.Spacing

/** Detail screen top bar — back arrow + title only, no avatar or bell. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            IconButton(
                onClick = onBack,
                interactionSource = interactionSource,
                modifier = Modifier.iconPressScale(pressed),
            ) {
                Icon(
                    HugeIcons.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/** Main screen top bar — avatar + title on the left, notification bell on the right. */
@Composable
fun HisabakTopBar(
    onNotificationsClick: () -> Unit = {},
    title: String = "WealthFlow",
    unreadCount: Int = 0,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        // Reserves space for the translucent system status bar so the title
        // doesn't clash with the clock/icons up top.
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = Spacing.pageMargin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            // Rounded square (~25% radius squircle) to match the Hisabak app-icon mark,
            // not a circle. See the design's logo-mark.svg / TopAppBar component.
            val logoShape = RoundedCornerShape(percent = 25)
            Box(
                Modifier
                    .size(Sizing.avatar)
                    .clip(logoShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, logoShape),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "Hisabak",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
            val bellInteraction = remember { MutableInteractionSource() }
            val bellPressed by bellInteraction.collectIsPressedAsState()
            IconButton(
                onClick = onNotificationsClick,
                interactionSource = bellInteraction,
                modifier = Modifier.iconPressScale(bellPressed),
            ) {
                androidx.compose.material3.BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            androidx.compose.material3.Badge {
                                Text(localizeDigits(if (unreadCount > 9) "9+" else unreadCount.toString(), rememberIsArabic()))
                            }
                        }
                    },
                ) {
                    Icon(
                        HugeIcons.Notifications,
                        contentDescription = stringResource(R.string.notifications_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
