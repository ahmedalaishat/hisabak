package com.hisabak.feature.notification.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.util.TypedValue
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import com.hisabak.ui.components.iconForKey

/**
 * Renders a category's Material glyph (the same [ImageVector] the in-app tiles use, via
 * [iconForKey]) onto a rounded color tile, sized for a notification large icon. Reusing the
 * in-app vector keeps the notification glyph pixel-consistent with the rest of the app without
 * shipping a parallel set of drawable assets.
 *
 * Returns null when [iconKey] is null (no category) so the caller falls back to the app icon.
 */
fun categoryGlyphBitmap(context: Context, iconKey: String?, colorKey: String?): Bitmap? {
    if (iconKey == null) return null
    val sizePx = dp(context, 48f)
    val (bg, fg) = notificationTileColors(colorKey)

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val radius = sizePx * 0.28f
    canvas.drawRoundRect(
        0f, 0f, sizePx.toFloat(), sizePx.toFloat(), radius, radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg },
    )

    val image = iconForKey(iconKey)
    val inset = sizePx * 0.24f
    val glyph = sizePx - inset * 2
    val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fg
        // Hugeicons are stroke outlines (1.5 in the 24dp viewport), not fills.
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    canvas.save()
    canvas.translate(inset, inset)
    canvas.scale(glyph / image.viewportWidth, glyph / image.viewportHeight)
    drawVectorGroup(image.root, canvas, glyphPaint)
    canvas.restore()

    return bitmap
}

private fun drawVectorGroup(group: VectorGroup, canvas: Canvas, paint: Paint) {
    canvas.save()
    // Group transform: T(translation+pivot) · R(rotation) · S(scale) · T(-pivot).
    canvas.translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
    canvas.rotate(group.rotation)
    canvas.scale(group.scaleX, group.scaleY)
    canvas.translate(-group.pivotX, -group.pivotY)
    for (node in group) {
        when (node) {
            is VectorPath -> {
                val path = PathParser().addPathNodes(node.pathData).toPath().asAndroidPath()
                path.fillType = if (node.pathFillType == PathFillType.EvenOdd) {
                    android.graphics.Path.FillType.EVEN_ODD
                } else {
                    android.graphics.Path.FillType.WINDING
                }
                canvas.drawPath(path, paint)
            }
            is VectorGroup -> drawVectorGroup(node, canvas, paint)
        }
    }
    canvas.restore()
}

/** Light tile background + readable foreground per the design's category palette (CLAUDE.md). */
private fun notificationTileColors(colorKey: String?): Pair<Int, Int> = when (colorKey) {
    "green" -> AndroidColor.parseColor("#D1FAE5") to AndroidColor.parseColor("#047857")
    "blue" -> AndroidColor.parseColor("#DBEAFE") to AndroidColor.parseColor("#2563EB")
    "orange" -> AndroidColor.parseColor("#FFEDD5") to AndroidColor.parseColor("#EA580C")
    "red" -> AndroidColor.parseColor("#FEE2E2") to AndroidColor.parseColor("#DC2626")
    "teal" -> AndroidColor.parseColor("#CCFBF1") to AndroidColor.parseColor("#0D9488")
    "purple" -> AndroidColor.parseColor("#F3E8FF") to AndroidColor.parseColor("#9333EA")
    "pink" -> AndroidColor.parseColor("#FCE7F3") to AndroidColor.parseColor("#DB2777")
    else -> AndroidColor.parseColor("#F3F4F6") to AndroidColor.parseColor("#4B5563")
}

private fun dp(context: Context, value: Float): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        context.resources.displayMetrics,
    ).toInt()
