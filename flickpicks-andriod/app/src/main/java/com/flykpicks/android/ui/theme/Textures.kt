package com.flykpicks.android.ui.theme

import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.random.Random

// Ports of iOS `TextileEffect` and `InkPaperEffect` (blueprint §3.5–3.6). Densities are per square
// dp and alphas are the iOS color alpha × layer opacity, so the textures read the same at any
// density. iOS re-randomizes on every render; a fixed seed keeps them stable across redraws.

/** Cream canvas with a faint grain and diagonal weave (iOS `.textileBackground()`). */
fun Modifier.textileBackground(): Modifier = drawWithCache {
    val grain = randomPoints(size, perDp2 = 1f / 35f, seed = TEXTILE_SEED)
    val grainPaint = pointPaint(FlykColors.Ink.copy(alpha = 0.22f * 0.10f), 1f * density)
    val weave = diagonalLines(size, step = 18f * density)
    val weaveStroke = Stroke(width = 1f * density)
    onDrawBehind {
        drawRect(FlykColors.Canvas)
        drawPoints(grain, grainPaint)
        drawPath(weave, FlykColors.Ink.copy(alpha = 0.10f * 0.08f), style = weaveStroke)
    }
}

/**
 * Mottled-paper texture over the navy band (iOS `InkPaperEffect` at the TopBand's 0.9 opacity).
 * Drawn in overlay blend against whatever is behind it, so apply it after the background.
 */
fun Modifier.inkPaper(): Modifier = drawWithCache {
    val intensity = 0.9f
    val random = Random(INK_SEED)
    val area = (size.width / density) * (size.height / density)
    // iOS blurs solid circles by 10pt; a radial gradient gives the same soft edge without a blur
    // pass (RenderEffect blur needs API 31, minSdk is 30).
    val blobs = List(maxOf((area / 22_000f).toInt(), 12)) {
        val center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
        center to (18f + random.nextFloat() * 42f + 10f) * density
    }
    val mottle = FlykColors.NavBarText.copy(alpha = 0.10f * 0.10f * intensity)
    val grain = randomPoints(size, perDp2 = 1f / 4_500f, seed = INK_SEED + 1)
    val grainPaint = pointPaint(FlykColors.NavBarText.copy(alpha = 0.18f * 0.06f * intensity), 1f * density)
        .apply { blendMode = android.graphics.BlendMode.OVERLAY }
    val fibers = diagonalLines(size, step = 22f * density)
    val fiberStroke = Stroke(width = 1f * density)
    onDrawWithContent {
        drawContent()
        blobs.forEach { (center, radius) ->
            drawCircle(
                brush = Brush.radialGradient(listOf(mottle, Color.Transparent), center, radius),
                radius = radius,
                center = center,
                blendMode = BlendMode.Overlay,
            )
        }
        drawPoints(grain, grainPaint)
        drawPath(
            fibers,
            FlykColors.NavBarText.copy(alpha = 0.12f * 0.05f * intensity),
            style = fiberStroke,
            blendMode = BlendMode.Overlay,
        )
    }
}

private const val TEXTILE_SEED = 0x7E471E
private const val INK_SEED = 0x1A4

/** Flat x/y pairs for `Canvas.drawPoints`: one native call instead of thousands of rects. */
private fun CacheDrawScope.randomPoints(size: Size, perDp2: Float, seed: Int): FloatArray {
    val count = ((size.width / density) * (size.height / density) * perDp2).toInt()
    val random = Random(seed)
    return FloatArray(count * 2) { i ->
        if (i % 2 == 0) random.nextFloat() * size.width else random.nextFloat() * size.height
    }
}

/** Parallel lines at 45°, bottom-left to top-right, [step] px apart (iOS weave / fibers). */
private fun diagonalLines(size: Size, step: Float): Path = Path().apply {
    var x = -size.height
    while (x < size.width) {
        moveTo(x, size.height)
        lineTo(x + size.height, 0f)
        x += step
    }
}

private fun pointPaint(color: Color, sizePx: Float) = Paint().apply {
    this.color = color.toArgb()
    strokeWidth = sizePx
    strokeCap = Paint.Cap.SQUARE
}

private fun DrawScope.drawPoints(points: FloatArray, paint: Paint) {
    if (points.isEmpty()) return
    drawIntoCanvas { it.nativeCanvas.drawPoints(points, paint) }
}
