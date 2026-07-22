package com.hisabak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import com.hisabak.ui.theme.Green300
import com.hisabak.ui.theme.Spacing

/** Green gradient call-out used for the "Smart Saving Tip" card. */
@Composable
fun GradientBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.horizontalGradient(
                    listOf(MaterialTheme.colorScheme.primary, Green300),
                ),
            )
            .padding(Spacing.s6),
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
            )
        }
    }
}

/** Dark inverse-surface promotional banner with a CTA. */
@Composable
fun DarkPromoBanner(
    title: String,
    body: String,
    ctaLabel: String,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(Spacing.s6),
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = Spacing.s2, bottom = Spacing.cardGap),
            )
            PrimaryPillButton(
                text = ctaLabel,
                onClick = onCtaClick,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
