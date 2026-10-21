package org.umn.ngantriin.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.umn.ngantriin.ui.theme.LocalTicketColors
import org.umn.ngantriin.ui.theme.Motion
import org.umn.ngantriin.ui.theme.Spacing
import kotlin.random.Random

/**
 * A rounded rectangle with a half-circle bitten out of each side at
 * [notchY] — the tear line of a paper ticket. Without a notch position yet
 * (first frame) it is a plain rounded rectangle.
 */
@Immutable
class TicketShape(
    private val notchY: Float,
    private val corner: Dp = 16.dp,
    private val notchRadius: Dp = 11.dp,
    private val vertical: Boolean = false
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.toPx() }
        val n = with(density) { notchRadius.toPx() }
        val body = Path().apply {
            addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(r)))
        }
        if (notchY <= 0f) return Outline.Generic(body)
        val bites = Path().apply {
            if (vertical) {
                addOval(Rect(Offset(notchY, 0f), n))
                addOval(Rect(Offset(notchY, size.height), n))
            } else {
                addOval(Rect(Offset(0f, notchY), n))
                addOval(Rect(Offset(size.width, notchY), n))
            }
        }
        return Outline.Generic(Path().apply { op(body, bites, PathOperation.Difference) })
    }
}

/**
 * Faint, seeded paper grain. Built once per size, so it costs one cached
 * draw call per frame rather than any per-frame work.
 */
fun Modifier.paperGrain(seed: Int = 7): Modifier = drawWithCache {
    val random = Random(seed)
    val count = (size.width * size.height / 900f).toInt().coerceAtMost(1800)
    val points = List(count) { Offset(random.nextFloat() * size.width, random.nextFloat() * size.height) }
    val grain = Color.Black.copy(alpha = 0.035f)
    val dot = 1.2.dp.toPx()
    onDrawBehind { drawPoints(points, PointMode.Points, grain, strokeWidth = dot, cap = StrokeCap.Round) }
}

/**
 * The ticket: printed paper with a [header] above a perforated tear line
 * and a [stub] below it. Colours come from [LocalTicketColors], so the paper
 * stays paper in dark mode.
 */
@Composable
fun Ticket(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.xl),
    header: @Composable ColumnScope.() -> Unit,
    stub: (@Composable ColumnScope.() -> Unit)? = null
) {
    val colors = LocalTicketColors.current
    var headerHeight by remember { mutableFloatStateOf(0f) }
    val tear = 24.dp
    val tearPx = with(LocalDensity.current) { tear.toPx() }
    // The notches bite into the middle of the tear strip under the header.
    val shape = remember(headerHeight, tearPx, stub != null) {
        TicketShape(notchY = if (stub != null && headerHeight > 0f) headerHeight + tearPx / 2 else 0f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = colors.ink, spotColor = colors.ink)
            .background(colors.paper, shape)
            .paperGrain()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { headerHeight = it.height.toFloat() }
                .padding(contentPadding),
            content = header
        )
        if (stub != null) {
            Perforation(
                color = colors.rule,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tear)
                    .padding(horizontal = 18.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = stub
            )
        }
    }
}

/** A dashed tear line drawn across the middle of its bounds. */
@Composable
fun Perforation(color: Color, modifier: Modifier = Modifier, vertical: Boolean = false) {
    Canvas(modifier) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
        val stroke = 1.5.dp.toPx()
        if (vertical) {
            drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), stroke, StrokeCap.Round, dash)
        } else {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke, StrokeCap.Round, dash)
        }
    }
}

/** Most groups drawn individually; anything beyond becomes "+N". */
private const val MAX_VISIBLE_GROUPS = 9

/**
 * The line, made visible: one short bar per group still in front, then you.
 * When the restaurant seats the group at the front, its bar leaves on the
 * left and everyone behind slides up — the queue moving, not a number
 * changing.
 */
@Composable
fun QueueLine(
    peopleAhead: Int,
    description: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalTicketColors.current
    val shown = peopleAhead.coerceIn(0, MAX_VISIBLE_GROUPS)
    val hidden = peopleAhead - shown
    // Keys are the distance from you, so the group at the front of the line
    // (the largest key) is the one that leaves when someone is seated.
    val keys = remember(shown) { (shown downTo 1).toList() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // The counter: where the line is heading.
        Box(
            Modifier
                .size(width = 4.dp, height = 28.dp)
                .background(colors.ink, RoundedCornerShape(2.dp))
        )
        if (hidden > 0) {
            Text(
                text = "+$hidden",
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkMuted
            )
        }
        LazyRow(
            modifier = Modifier.weight(1f, fill = false),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(keys, key = { it }) {
                Box(
                    Modifier
                        .animateItem(
                            fadeInSpec = tween(Motion.FAST_MS),
                            placementSpec = Motion.emphasized(),
                            fadeOutSpec = tween(Motion.FAST_MS)
                        )
                        .size(width = 14.dp, height = 20.dp)
                        .background(colors.rule, RoundedCornerShape(4.dp))
                )
            }
            item(key = "you") {
                Box(
                    Modifier
                        .animateItem(placementSpec = Motion.emphasized())
                        .size(width = 40.dp, height = 28.dp)
                        .background(colors.accent, RoundedCornerShape(6.dp))
                )
            }
        }
    }
}
