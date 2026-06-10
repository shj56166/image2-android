package com.shj56166androidimage2.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shj56166androidimage2.app.R

@Composable
fun MaskEditorScreen(
    imagePath: String,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onSave: (List<List<Pair<Float, Float>>>) -> Unit,
) {
    val strokes = remember { mutableStateListOf<MutableList<Offset>>() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val imageRect = remember(canvasSize, width, height) { computeFitRect(canvasSize, width, height) }

    BackHandler(onBack = onDismiss)

    Column(
        modifier =
            modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.mask_editor_title), style = MaterialTheme.typography.titleLarge)
        Box(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { canvasSize = it },
        ) {
            AsyncImage(model = imagePath, contentDescription = null, modifier = Modifier.fillMaxSize())
            Canvas(
                modifier =
                    Modifier.fillMaxSize()
                        .pointerInput(imageRect, width, height) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    toImagePoint(offset, imageRect, width, height)?.let { point ->
                                        strokes.add(mutableStateListOf(point))
                                    }
                                },
                                onDrag = { change, _ ->
                                    toImagePoint(change.position, imageRect, width, height)
                                        ?.let { point -> strokes.lastOrNull()?.add(point) }
                                },
                            )
                        },
            ) {
                strokes.forEach { stroke ->
                    if (stroke.size < 2) return@forEach
                    val path =
                        Path().apply {
                            val first = toDisplayPoint(stroke.first(), imageRect, width, height)
                            moveTo(first.x, first.y)
                            stroke.drop(1).forEach { point ->
                                val mapped = toDisplayPoint(point, imageRect, width, height)
                                lineTo(mapped.x, mapped.y)
                            }
                        }
                    drawPath(
                        path = path,
                        color = Color.Red.copy(alpha = 0.66f),
                        style = Stroke(width = 20f, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel_action))
            }
            Button(
                onClick = { onSave(strokes.map { stroke -> stroke.map { point -> point.x to point.y } }) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.save_mask_action))
            }
        }
        Text(
            stringResource(R.string.canvas_size_label, width, height),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun computeFitRect(size: IntSize, imageWidth: Int, imageHeight: Int): Rect {
    if (size.width <= 0 || size.height <= 0 || imageWidth <= 0 || imageHeight <= 0) {
        return Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
    }
    val containerWidth = size.width.toFloat()
    val containerHeight = size.height.toFloat()
    val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
    val containerAspect = containerWidth / containerHeight
    return if (containerAspect > imageAspect) {
        val displayedHeight = containerHeight
        val displayedWidth = displayedHeight * imageAspect
        val left = (containerWidth - displayedWidth) / 2f
        Rect(left, 0f, left + displayedWidth, displayedHeight)
    } else {
        val displayedWidth = containerWidth
        val displayedHeight = displayedWidth / imageAspect
        val top = (containerHeight - displayedHeight) / 2f
        Rect(0f, top, displayedWidth, top + displayedHeight)
    }
}

private fun toImagePoint(point: Offset, rect: Rect, imageWidth: Int, imageHeight: Int): Offset? {
    if (!rect.contains(point)) return null
    val mappedX = ((point.x - rect.left) / rect.width) * imageWidth
    val mappedY = ((point.y - rect.top) / rect.height) * imageHeight
    return Offset(
        x = mappedX.coerceIn(0f, imageWidth.toFloat()),
        y = mappedY.coerceIn(0f, imageHeight.toFloat()),
    )
}

private fun toDisplayPoint(point: Offset, rect: Rect, imageWidth: Int, imageHeight: Int): Offset {
    val x = rect.left + (point.x / imageWidth.toFloat()) * rect.width
    val y = rect.top + (point.y / imageHeight.toFloat()) * rect.height
    return Offset(x, y)
}
