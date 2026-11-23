package org.avmedia.gshockGoogleSync.ui.health

import AppTextLarge
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppDatePickerDialog
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_ZOOM = 12f // 2 hours across the card

@Composable
fun EnergyCardView(
    modifier: Modifier = Modifier,
    viewModel: EnergyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            AppTextLarge(text = stringResource(R.string.energy), modifier = Modifier.fillMaxWidth())

            DaySelector(
                label = state.date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())),
                hasNewer = state.hasNewer,
                onOlder = viewModel::showOlder,
                onNewer = viewModel::showNewer,
                onPick = { showDatePicker = true },
            )
            if (showDatePicker) {
                AppDatePickerDialog(
                    initialDate = state.date,
                    maxDate = state.today,
                    onConfirm = viewModel::select,
                    onDismiss = { showDatePicker = false },
                )
            }

            val day = state.day
            if (day == null || !day.hasData) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(if (state.hasAnyData) R.string.energy_no_data_day else R.string.energy_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Spacer(Modifier.height(8.dp))
            val timeFormat = rememberTimeFormat(withMinutes = true)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                EnergyValue(stringResource(R.string.energy_total), "${day.totalKcal}")
                day.activeKcal?.let { EnergyValue(stringResource(R.string.energy_active), "$it") }
                day.peak?.let { EnergyValue(stringResource(R.string.energy_peak), "%.1f".format(Locale.getDefault(), it.kcal)) }
            }

            Spacer(Modifier.height(16.dp))
            EnergyChart(day, timeFormat)

            Spacer(Modifier.height(8.dp))
            val notes = buildList {
                day.restingKcalPer5?.let {
                    add(stringResource(R.string.energy_resting, "%.1f".format(Locale.getDefault(), it)))
                }
                val first = day.firstKnown
                val last = day.lastKnown
                if (first != null && last != null) {
                    add(
                        stringResource(
                            R.string.energy_coverage,
                            timeFormat.format(first.start),
                            timeFormat.format(last.start.plusSeconds(300)),
                        )
                    )
                }
            }
            Text(
                text = notes.joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun rememberTimeFormat(withMinutes: Boolean): DateTimeFormatter {
    val context = LocalContext.current
    return remember(context, withMinutes) {
        val is24 = DateFormat.is24HourFormat(context)
        val pattern = when {
            is24 && withMinutes -> "HH:mm"
            is24 -> "HH"
            withMinutes -> "h:mm a"
            else -> "h a"
        }
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).withZone(ZoneId.systemDefault())
    }
}

/** "< [date] >" row; tapping the date opens a picker. Shared by the Energy and Sleep cards. */
@Composable
internal fun DaySelector(
    label: String,
    hasNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onPick: () -> Unit,
    hasOlder: Boolean = true,
    olderDescription: String = stringResource(R.string.previous_day),
    newerDescription: String = stringResource(R.string.next_day),
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onOlder, enabled = hasOlder) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = olderDescription)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = stringResource(R.string.energy_pick_date), onClick = onPick)
                .padding(vertical = 8.dp),
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onNewer, enabled = hasNewer) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = newerDescription)
        }
    }
}

@Composable
private fun EnergyValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One bar per 5 minutes over the whole day. Bars above the resting level are drawn in full
 * colour; the dashed line is the resting level. Tap to inspect a slot; the slider zooms in
 * and the chart can then be dragged (and flung) horizontally.
 *
 * The scroll position is kept here (not in a scroll container) so a zoom step can re-centre
 * the view in the same frame: zooming keeps the same time of day in the middle of the card.
 */
@Composable
private fun EnergyChart(day: EnergyDay, timeFormat: DateTimeFormatter) {
    var selected by remember(day.date) { mutableStateOf<Int?>(null) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    /** Left edge of the view, as a fraction of the day (0 = midnight). */
    var viewStart by rememberSaveable { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var fling by remember { mutableStateOf<Job?>(null) }

    val barColor = MaterialTheme.colorScheme.primary
    val restBarColor = barColor.copy(alpha = 0.35f)
    val markerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val hourFormat = rememberTimeFormat(withMinutes = false)
    val rest = day.restingKcalPer5
    val maxKcal = maxOf(day.peak?.kcal ?: 0f, (rest ?: 0f) * 2f, 1f) * 1.1f
    val slotCount = day.slots.size

    val detail = selected?.let { day.slots.getOrNull(it) }
    Text(
        text = if (detail == null) {
            stringResource(R.string.energy_tap_hint)
        } else {
            val time = "${timeFormat.format(detail.start)}–${timeFormat.format(detail.start.plusSeconds(300))}"
            if (detail.kcal == null) stringResource(R.string.energy_slot_empty, time)
            else stringResource(
                R.string.energy_slot,
                time,
                "%.1f".format(Locale.getDefault(), detail.kcal),
                detail.steps ?: 0,
            )
        },
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (detail != null) FontWeight.SemiBold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(4.dp))

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val viewportPx = constraints.maxWidth.toFloat()
        val minLabelSpacingPx = with(LocalDensity.current) { 44.dp.toPx() }
        // Visible part of the day is 1/zoom; the view may start anywhere in [0, 1 - 1/zoom].
        fun clampStart(start: Float) = start.coerceIn(0f, (1f - 1f / zoom).coerceAtLeast(0f))
        fun scrollByPx(deltaPx: Float): Float {
            if (viewportPx <= 0f) return 0f
            val before = viewStart
            viewStart = clampStart(viewStart - deltaPx / (viewportPx * zoom))
            return (before - viewStart) * viewportPx * zoom
        }

        val dragState = rememberDraggableState { delta -> scrollByPx(delta) }

        Column {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clipToBounds()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        enabled = zoom > 1f,
                        onDragStarted = { fling?.cancel() },
                        onDragStopped = { velocity ->
                            fling = scope.launch {
                                var last = 0f
                                AnimationState(0f, velocity).animateDecay(exponentialDecay()) {
                                    val consumed = scrollByPx(value - last)
                                    last = value
                                    if (consumed == 0f && value != 0f) cancelAnimation()
                                }
                            }
                        },
                    )
                    .pointerInput(day.date, slotCount, zoom) {
                        detectTapGestures {
                            fling?.cancel()
                            val contentWidth = size.width * zoom
                            selected = slotAt(it.x + viewStart * contentWidth, contentWidth, slotCount)
                        }
                    }
            ) {
                val labelSpace = 16.dp.toPx()
                val chartHeight = size.height - labelSpace
                val contentWidth = size.width * zoom
                val slotWidth = contentWidth / slotCount
                val offset = viewStart * contentWidth
                fun x(i: Int) = i * slotWidth - offset
                fun visible(i: Int) = x(i) + slotWidth >= 0f && x(i) <= size.width
                fun y(kcal: Float) = chartHeight * (1f - kcal / maxKcal)

                drawLine(gridColor, Offset(0f, chartHeight), Offset(size.width, chartHeight), 1.dp.toPx())

                // Hour grid: as dense as the zoom allows (every 1, 2, 3 or 6 hours).
                val pxPerHour = slotWidth * 12
                val stepHours = listOf(1, 2, 3, 6).first { it * pxPerHour >= minLabelSpacingPx || it == 6 }
                val paint = android.graphics.Paint().apply {
                    color = labelColor
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                day.slots.forEachIndexed { i, slot ->
                    if (!visible(i)) return@forEachIndexed
                    val local = slot.start.atZone(ZoneId.systemDefault())
                    if (local.minute != 0 || local.hour % stepHours != 0) return@forEachIndexed
                    val gx = x(i)
                    if (i != 0) drawLine(gridColor, Offset(gx, 0f), Offset(gx, chartHeight), 0.5.dp.toPx())
                    val text = hourFormat.format(slot.start)
                    val half = paint.measureText(text) / 2
                    drawIntoCanvas {
                        it.nativeCanvas.drawText(
                            text,
                            gx.coerceIn(half, size.width - half),
                            size.height - 2.dp.toPx(),
                            paint,
                        )
                    }
                }

                // Bars.
                val gap = if (slotWidth > 2.dp.toPx()) 0.5.dp.toPx() else 0f
                day.slots.forEachIndexed { i, slot ->
                    val kcal = slot.kcal ?: return@forEachIndexed
                    if (!visible(i)) return@forEachIndexed
                    val color = if (rest != null && kcal <= rest * 1.02f) restBarColor else barColor
                    drawRect(
                        color = color,
                        topLeft = Offset(x(i), y(kcal)),
                        size = Size((slotWidth - gap).coerceAtLeast(1f), chartHeight - y(kcal)),
                    )
                }

                // Resting level.
                if (rest != null) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y(rest)),
                        end = Offset(size.width, y(rest)),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    )
                }

                // Selection marker (also for empty slots); visible even in monochrome themes.
                selected?.let { i ->
                    if (!visible(i)) return@let
                    val mx = x(i) + slotWidth / 2
                    drawLine(markerColor, Offset(mx, 0f), Offset(mx, chartHeight), 1.5.dp.toPx())
                    drawCircle(markerColor, radius = 3.dp.toPx(), center = Offset(mx, 3.dp.toPx()))
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.energy_zoom),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = zoom,
                    onValueChange = { value ->
                        fling?.cancel()
                        // Keep the time at the centre of the card fixed while zooming.
                        val center = viewStart + 0.5f / zoom
                        zoom = value
                        viewStart = clampStart(center - 0.5f / value)
                    },
                    valueRange = 1f..MAX_ZOOM,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(
                    text = visibleSpanLabel(zoom),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp),
                )
            }
        }
    }
}

/** How much of the day fits in the card at this zoom ("24 h", "6 h", "2 h"). */
internal fun visibleSpanLabel(zoom: Float): String {
    val minutes = (24 * 60 / zoom).roundToInt()
    return if (minutes >= 90) "${(minutes / 60f).roundToInt()} h" else "$minutes m"
}

private fun slotAt(x: Float, width: Float, count: Int): Int? =
    if (width <= 0f || count == 0) null else (x / width * count).toInt().coerceIn(0, count - 1)
