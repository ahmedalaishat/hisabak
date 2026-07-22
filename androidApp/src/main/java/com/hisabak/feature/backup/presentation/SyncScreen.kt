package com.hisabak.feature.backup.presentation

import com.hisabak.ui.icons.HugeIcons

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hisabak.R
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.ui.components.ButtonVariant
import com.hisabak.ui.components.HisabakButton
import com.hisabak.ui.theme.LocalReducedMotion
import com.hisabak.ui.theme.Spacing

enum class SyncKind { BackUp, Restore }

sealed interface SyncPhase {
    data object Running : SyncPhase
    data class Done(val restoredCount: Int? = null) : SyncPhase
    data class Failed(val error: BackupError) : SyncPhase
}

/**
 * Full-screen progress/result for a backup or restore. The running state shows an animated "sync
 * halo" (orbiting arc + pulsing glow around a cloud glyph); success draws a check, failure shows the
 * mapped error. Reduced-motion falls back to a plain spinner.
 */
@Composable
fun SyncScreen(
    kind: SyncKind,
    phase: SyncPhase,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = Spacing.s7),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.s6, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SyncHalo(kind, phase)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(titleRes(kind, phase)),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = subtitle(kind, phase),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.s2),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            when (phase) {
                is SyncPhase.Done -> HisabakButton(
                    text = stringResource(R.string.sync_continue),
                    onClick = onContinue,
                    fullWidth = true,
                )
                is SyncPhase.Failed -> {
                    HisabakButton(
                        text = stringResource(R.string.sync_try_again),
                        onClick = onRetry,
                        fullWidth = true,
                    )
                    HisabakButton(
                        text = stringResource(R.string.sync_close),
                        onClick = onClose,
                        variant = ButtonVariant.Ghost,
                        fullWidth = true,
                    )
                }
                SyncPhase.Running -> Unit // no actions while running
            }
        }
    }
}

@Composable
private fun SyncHalo(kind: SyncKind, phase: SyncPhase) {
    val primary = MaterialTheme.colorScheme.primary
    val reduced = LocalReducedMotion.current

    Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        when (phase) {
            SyncPhase.Running -> {
                if (reduced) {
                    CircularProgressIndicator(color = primary, modifier = Modifier.size(72.dp))
                } else {
                    val transition = rememberInfiniteTransition(label = "sync")
                    val angle by transition.animateFloat(
                        initialValue = 0f, targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
                        label = "angle",
                    )
                    val pulse by transition.animateFloat(
                        initialValue = 0f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
                        label = "pulse",
                    )
                    Box(
                        Modifier
                            .size(132.dp)
                            .graphicsLayer {
                                val s = 0.85f + pulse * 0.3f
                                scaleX = s; scaleY = s; alpha = (1f - pulse) * 0.7f
                            }
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.12f)),
                    )
                    Canvas(Modifier.size(132.dp).graphicsLayer { rotationZ = angle }) {
                        val sw = 5.dp.toPx()
                        val inset = sw / 2
                        val arcSize = Size(size.width - sw, size.height - sw)
                        drawCircle(primary.copy(alpha = 0.16f), (size.minDimension - sw) / 2, style = Stroke(sw))
                        drawArc(
                            color = primary, startAngle = -90f, sweepAngle = 100f, useCenter = false,
                            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw),
                        )
                    }
                }
                CenterDisc(primary.copy(alpha = 0.12f)) {
                    Icon(
                        if (kind == SyncKind.BackUp) HugeIcons.CloudUpload else HugeIcons.CloudDownload,
                        contentDescription = null, tint = primary, modifier = Modifier.size(44.dp),
                    )
                }
            }
            is SyncPhase.Done -> {
                Canvas(Modifier.size(132.dp)) {
                    val sw = 5.dp.toPx()
                    drawCircle(primary, (size.minDimension - sw) / 2, style = Stroke(sw))
                }
                CenterDisc(primary) {
                    Icon(
                        HugeIcons.Check, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(52.dp).scale(if (reduced) 1f else 1f),
                    )
                }
            }
            is SyncPhase.Failed -> CenterDisc(MaterialTheme.colorScheme.error.copy(alpha = 0.14f)) {
                Icon(
                    HugeIcons.ErrorOutline, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}

@Composable
private fun CenterDisc(color: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    Box(
        Modifier.size(96.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun titleRes(kind: SyncKind, phase: SyncPhase): Int = when (phase) {
    SyncPhase.Running -> if (kind == SyncKind.BackUp) R.string.sync_backup_running_title else R.string.sync_restore_running_title
    is SyncPhase.Done -> if (kind == SyncKind.BackUp) R.string.sync_backup_done_title else R.string.sync_restore_done_title
    is SyncPhase.Failed -> if (kind == SyncKind.BackUp) R.string.sync_backup_failed_title else R.string.sync_restore_failed_title
}

@Composable
private fun subtitle(kind: SyncKind, phase: SyncPhase): String = when (phase) {
    SyncPhase.Running -> stringResource(
        if (kind == SyncKind.BackUp) R.string.sync_backup_running_sub else R.string.sync_restore_running_sub,
    )
    is SyncPhase.Done ->
        if (phase.restoredCount != null) stringResource(R.string.sync_restore_done_sub, phase.restoredCount)
        else stringResource(R.string.sync_backup_done_sub)
    is SyncPhase.Failed -> stringResource(phase.error.messageRes())
}
