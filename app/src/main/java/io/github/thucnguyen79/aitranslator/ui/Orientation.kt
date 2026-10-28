package io.github.thucnguyen79.aitranslator.ui

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import io.github.thucnguyen79.aitranslator.ConversationLayout

/**
 * How far a seat's area, and anything shown to that seat, is turned (degrees clockwise): seat 0 holds the
 * phone, seat 1 sits opposite, seats 2 and 3 at the left and right sides. In the list layout only enlarged
 * text is turned, towards seat 1.
 */
internal fun seatTurn(seat: Int, layout: ConversationLayout): Int = when {
    seat == 1 -> 180
    layout != ConversationLayout.AROUND -> 0
    seat == 2 -> 90
    seat == 3 -> 270
    else -> 0
}

/** Turns the content by a multiple of 90°; for a quarter turn its width and height are swapped first. */
internal fun Modifier.turned(degrees: Int): Modifier = when (degrees % 360) {
    0 -> this
    180 -> rotate(180f)
    else -> layout { measurable, c ->
        val p = measurable.measure(Constraints(c.minHeight, c.maxHeight, c.minWidth, c.maxWidth))
        layout(p.height, p.width) { p.place((p.height - p.width) / 2, (p.width - p.height) / 2) }
    }.rotate(degrees.toFloat())
}

/**
 * Lists turned a quarter do not fling: the release velocity is measured on the screen's axes, so a fling
 * would go sideways or backwards. Dragging still scrolls normally.
 */
internal object NoFling : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float = initialVelocity
}

/** One colour per person (F1.3), readable on light and dark backgrounds. */
private val SEAT_COLORS = listOf(Color(0xFF1E6FD9), Color(0xFF8E44AD), Color(0xFF2E8B57), Color(0xFFD35400))

internal fun seatColor(seat: Int) = SEAT_COLORS[Math.floorMod(seat, SEAT_COLORS.size)]

/** A light wash of the seat's colour, so each person's area stands apart from the others. */
@Composable
internal fun seatTint(seat: Int): Color =
    seatColor(seat).copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.16f else 0.08f)
