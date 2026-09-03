package com.hisabak.feature.category.presentation.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hisabak.feature.category.domain.CategoryColor
import com.hisabak.shared.resources.*
import com.hisabak.ui.components.HisabakButton
import com.hisabak.ui.components.NoticeCard
import com.hisabak.ui.components.NoticeTone
import com.hisabak.ui.components.iconForKey
import com.hisabak.ui.components.tintPairForColor
import com.hisabak.ui.theme.PillShape
import com.hisabak.ui.theme.Sizing
import com.hisabak.ui.theme.Spacing
import com.hisabak.ui.theme.hueForegroundDark
import com.hisabak.ui.theme.hueForegroundLight
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.layout.onSizeChanged

/** Another category's style — used to show a pick in context and to flag a clash. */
data class UsedCategoryColor(val name: String, val colorKey: String, val iconKey: String)

/**
 * The custom color picker: a hue track, a live preview in both themes, and — the part that earns
 * its place — the colors already in use, so the choice is made against real neighbours rather
 * than against a blank sheet. Picking a hue too close to an existing category says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryColorPickerSheet(
    initialHue: Int,
    iconKey: String,
    inUse: List<UsedCategoryColor>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Keyed on the incoming hue so reopening the sheet snaps back to the category's actual
    // colour — unkeyed, it would restore whatever was last dragged and then cancelled.
    var hue by rememberSaveable(initialHue) { mutableIntStateOf(initialHue) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val clash = remember(hue, inUse) {
        inUse.firstOrNull { used ->
            CategoryColor.hueFor(used.colorKey)?.let { CategoryColor.collides(hue, it) } == true
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.pageMargin)
                .padding(bottom = Spacing.sectionGap),
            verticalArrangement = Arrangement.spacedBy(Spacing.s5),
        ) {
            Text(
                text = stringResource(Res.string.category_color_picker_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            HueTrack(hue = hue, onHueChange = { hue = it })

            // Both themes at once: the shade is derived per theme, so one preview would be a
            // half-truth about what the user just chose.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                ThemePreview(hue, iconKey, dark = false, modifier = Modifier.weight(1f))
                ThemePreview(hue, iconKey, dark = true, modifier = Modifier.weight(1f))
            }

            if (inUse.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                    Text(
                        text = stringResource(Res.string.category_color_in_use),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                        inUse.take(12).forEach { used ->
                            val (_, fg) = tintPairForColor(used.colorKey)
                            Box(
                                modifier = Modifier
                                    .size(Sizing.iconSm)
                                    .clip(CircleShape)
                                    .background(fg, CircleShape),
                            )
                        }
                    }
                }
            }

            if (clash != null) {
                NoticeCard(
                    text = stringResource(Res.string.category_color_clash, clash.name),
                    tone = NoticeTone.Info,
                )
            }

            HisabakButton(
                text = stringResource(Res.string.common_done),
                onClick = { onPick(CategoryColor.customKey(hue)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HueTrack(hue: Int, onHueChange: (Int) -> Unit) {
    val spectrum = remember {
        Brush.horizontalGradient((0..12).map { hueForegroundLight(it * 30) })
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Measured at layout, not from inside pointerInput: those lambdas run as coroutines after
    // attach, so the first frame would place the thumb against a 1px track — pinned to the far
    // left no matter which colour is selected.
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    fun hueAt(x: Float): Int {
        if (widthPx <= 0) return hue
        val fraction = (x / widthPx).coerceIn(0f, 1f)
        val directed = if (rtl) 1f - fraction else fraction
        return (directed * 359f).toInt()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TrackHeight)
            .clip(PillShape)
            .background(spectrum, PillShape)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(rtl) {
                detectHorizontalDragGestures(
                    onDragStart = { onHueChange(hueAt(it.x)) },
                ) { change, _ -> onHueChange(hueAt(change.position.x)) }
            }
            .pointerInput(rtl) {
                detectTapGestures { onHueChange(hueAt(it.x)) }
            },
    ) {
        val fraction = hue / 359f
        val directed = if (rtl) 1f - fraction else fraction
        val thumbX = with(density) { ((widthPx * directed) - ThumbSize.toPx() / 2).toDp() }
        // Nothing to place until the track has been measured.
        if (widthPx > 0) Box(
            modifier = Modifier
                .offset(x = thumbX)
                .align(Alignment.CenterStart)
                .size(ThumbSize)
                .clip(CircleShape)
                .background(Color.White, CircleShape)
                .border(2.dp, hueForegroundLight(hue), CircleShape),
        )
    }
}

@Composable
private fun ThemePreview(
    hue: Int,
    iconKey: String,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val fg = if (dark) hueForegroundDark(hue) else hueForegroundLight(hue)
    val surface = if (dark) DarkPreviewSurface else LightPreviewSurface
    val shape = MaterialTheme.shapes.medium

    Row(
        modifier = modifier
            .clip(shape)
            .background(surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier = Modifier
                .size(Sizing.tileSize)
                .clip(shape)
                .background(fg.copy(alpha = 0.15f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconForKey(iconKey),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = stringResource(
                if (dark) Res.string.category_color_preview_dark else Res.string.category_color_preview_light,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (dark) Color(0xFFB9BFC9) else Color(0xFF6B7280),
        )
    }
}

private val TrackHeight = 28.dp
private val ThumbSize = 24.dp
private val LightPreviewSurface = Color(0xFFF7F8FA)
private val DarkPreviewSurface = Color(0xFF14171C)
