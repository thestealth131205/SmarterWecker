package de.letheapp.nocturn.emergency.puzzle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun PuzzleScreen(puzzle: Puzzle, onSolved: () -> Unit) {
    var pool by remember(puzzle) { mutableStateOf(puzzle.pool) }
    val placedSlots = remember(puzzle) { mutableStateMapOf<Int, PuzzleElement>() }
    val slotBounds = remember(puzzle) { mutableStateMapOf<Int, Rect>() }

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            text = "Notfall-Alarm! Sortiere die Elemente in der vorgegebenen Reihenfolge ein.",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Vorgabe",
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            puzzle.sequence.forEach { element ->
                ElementVisual(element, modifier = Modifier.padding(4.dp))
            }
        }

        Text(
            text = "Ziel",
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            puzzle.sequence.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(56.dp)
                        .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .onGloballyPositioned { coordinates ->
                            slotBounds[index] = coordinates.boundsInWindow()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    placedSlots[index]?.let { ElementVisual(it) }
                }
            }
        }

        Text(
            text = "Auswahl",
            modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
        FlowPool(
            pool = pool,
            slotBounds = slotBounds,
            onDropped = { element, slotIndex ->
                if (slotIndex != null && placedSlots[slotIndex] == null &&
                    puzzle.sequence[slotIndex].key == element.key
                ) {
                    placedSlots[slotIndex] = element
                    pool = pool.filterNot { it.key == element.key }
                    if (placedSlots.size == puzzle.sequence.size) {
                        onSolved()
                    }
                }
            }
        )
    }
}

private const val POOL_COLUMNS = 5

@Composable
private fun FlowPool(
    pool: List<PuzzleElement>,
    slotBounds: Map<Int, Rect>,
    onDropped: (PuzzleElement, Int?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        pool.chunked(POOL_COLUMNS).forEach { rowElements ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowElements.forEach { element ->
                    DraggableElement(
                        element = element,
                        slotBounds = slotBounds,
                        onDropped = { slotIndex -> onDropped(element, slotIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DraggableElement(
    element: PuzzleElement,
    slotBounds: Map<Int, Rect>,
    onDropped: (Int?) -> Unit
) {
    var offset by remember(element.key) { mutableStateOf(Offset.Zero) }
    var bounds by remember(element.key) { mutableStateOf<Rect?>(null) }
    var dragging by remember(element.key) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .padding(4.dp)
            .zIndex(if (dragging) 1f else 0f)
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .onGloballyPositioned { coordinates -> bounds = coordinates.boundsInWindow() }
            .pointerInput(element.key) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offset += dragAmount
                    },
                    onDragEnd = {
                        dragging = false
                        val center = bounds?.center
                        val targetSlot = center?.let { point ->
                            slotBounds.entries.firstOrNull { it.value.contains(point) }?.key
                        }
                        onDropped(targetSlot)
                        offset = Offset.Zero
                    },
                    onDragCancel = {
                        dragging = false
                        offset = Offset.Zero
                    }
                )
            }
    ) {
        ElementVisual(element)
    }
}

@Composable
private fun ElementVisual(element: PuzzleElement, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(48.dp), contentAlignment = Alignment.Center) {
        when (element) {
            is PuzzleElement.EmojiElement -> Text(text = element.emoji, style = MaterialTheme.typography.headlineSmall)
            is PuzzleElement.ShapeElement -> ShapeVisual(element.shape, element.color)
        }
    }
}

@Composable
private fun ShapeVisual(shape: PuzzleShape, color: Color) {
    val base = Modifier.size(40.dp).background(color)
    when (shape) {
        PuzzleShape.CIRCLE -> Box(base.clip(CircleShape))
        PuzzleShape.SQUARE -> Box(base)
        PuzzleShape.TRIANGLE -> Box(base.clip(polygonShape(3, rotationDegrees = -90f)))
        PuzzleShape.DIAMOND -> Box(base.clip(polygonShape(4, rotationDegrees = -90f)))
        PuzzleShape.HEXAGON -> Box(base.clip(polygonShape(6)))
        PuzzleShape.STAR -> Box(base.clip(starShape()))
    }
}

private fun polygonShape(sides: Int, rotationDegrees: Float = 0f) = GenericShape { size, _ ->
    val radius = minOf(size.width, size.height) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val rotation = Math.toRadians(rotationDegrees.toDouble())
    for (i in 0 until sides) {
        val angle = rotation + 2 * PI * i / sides
        val point = Offset(
            x = center.x + radius * cos(angle).toFloat(),
            y = center.y + radius * sin(angle).toFloat()
        )
        if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

private fun starShape(points: Int = 5) = GenericShape { size, _ ->
    val outerRadius = minOf(size.width, size.height) / 2f
    val innerRadius = outerRadius * 0.45f
    val center = Offset(size.width / 2f, size.height / 2f)
    val rotation = Math.toRadians(-90.0)
    val step = PI / points
    for (i in 0 until points * 2) {
        val radius = if (i % 2 == 0) outerRadius else innerRadius
        val angle = rotation + step * i
        val point = Offset(
            x = center.x + radius * cos(angle).toFloat(),
            y = center.y + radius * sin(angle).toFloat()
        )
        if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}
