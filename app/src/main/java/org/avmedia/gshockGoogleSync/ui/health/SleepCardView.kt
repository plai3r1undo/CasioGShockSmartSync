package org.avmedia.gshockGoogleSync.ui.health

import AppTextLarge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.sleep.StoredNight
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockapi.model.SleepStage
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

private data class StagePalette(val wake: Color, val rem: Color, val light: Color, val deep: Color) {
    fun of(stage: SleepStage) = when (stage) {
        SleepStage.WAKE -> wake
        SleepStage.REM -> rem
        SleepStage.LIGHT -> light
        SleepStage.DEEP -> deep
    }
}

@Composable
private fun stagePalette(): StagePalette =
    if (isSystemInDarkTheme()) {
        StagePalette(Color(0xFFFFC94D), Color(0xFFA9ACFF), Color(0xFF5C74FF), Color(0xFF2E3FB8))
    } else {
        StagePalette(Color(0xFFF5B800), Color(0xFF8B8FF0), Color(0xFF2F45D9), Color(0xFF0B1466))
    }

/** ANS charge as the official app shows it: one decimal, truncated, with sign (0.386 -> "+0.3"). */
internal fun formatAnsCharge(value: Float): String {
    val truncated = (value * 10).toInt() / 10.0
    return String.format(Locale.US, "%+.1f", truncated)
}

internal fun formatHm(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)

@Composable
fun SleepCardView(
    modifier: Modifier = Modifier,
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AppTextLarge(text = stringResource(R.string.sleep), modifier = Modifier.weight(1f))
                if (state.syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = viewModel::sync) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.sleep_sync))
                    }
                }
            }

            val night = state.selected
            if (night == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(if (state.syncing) R.string.sleep_syncing else R.string.sleep_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.error?.let { ErrorLine(it) }
                return@Column
            }

            NightSelector(
                night = night,
                hasOlder = state.hasOlder,
                hasNewer = state.hasNewer,
                onOlder = viewModel::showOlder,
                onNewer = viewModel::showNewer,
            )
            state.error?.let { ErrorLine(it) }

            Spacer(Modifier.height(8.dp))
            ChargeRow(night)
            Spacer(Modifier.height(16.dp))
            Hypnogram(night)
            Spacer(Modifier.height(16.dp))
            StageBreakdown(night)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            Spacer(Modifier.height(12.dp))
            Vitals(night)
        }
    }
}

@Composable
private fun ErrorLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun NightSelector(
    night: StoredNight,
    hasOlder: Boolean,
    hasNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
) {
    val label = night.wakeDate().format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onOlder, enabled = hasOlder) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.sleep_previous_night))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onNewer, enabled = hasNewer) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.sleep_next_night))
        }
    }
}

@Composable
private fun ChargeRow(night: StoredNight) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        BigValue(label = stringResource(R.string.sleep_charge), value = night.sleepCharge.toString())
        BigValue(label = stringResource(R.string.ans_charge), value = formatAnsCharge(night.ansCharge))
    }
}

@Composable
private fun BigValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Stage timeline: wake on top, then REM, light and deep, like the official app. */
@Composable
private fun Hypnogram(night: StoredNight) {
    val segments = night.segments()
    val palette = stagePalette()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val zone = ZoneId.systemDefault()

    if (segments.isEmpty()) {
        Text(
            text = stringResource(R.string.sleep_no_hypnogram),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val total = segments.last().second.toFloat().coerceAtLeast(1f)
    val lanes = listOf(SleepStage.WAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val laneHeight = size.height / lanes.size
        // Baseline between "awake" and "asleep", as in the official app.
        drawLine(gridColor, Offset(0f, laneHeight), Offset(size.width, laneHeight), strokeWidth = 1.dp.toPx())
        val minWidth = 1.dp.toPx()
        segments.forEach { (from, to, stage) ->
            val lane = lanes.indexOf(stage)
            val x = from / total * size.width
            val w = ((to - from) / total * size.width).coerceAtLeast(minWidth)
            drawRect(
                color = palette.of(stage),
                topLeft = Offset(x, lane * laneHeight + 2.dp.toPx()),
                size = Size(w, laneHeight - 4.dp.toPx()),
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = night.start.atZone(zone).format(timeFormat),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = night.end.atZone(zone).format(timeFormat),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StageBreakdown(night: StoredNight) {
    val palette = stagePalette()
    val span = night.spanMin.coerceAtLeast(1)
    fun pct(min: Int) = "${(min * 100f / span).roundToInt()}%"

    BreakdownRow(stringResource(R.string.sleep_total_span), night.spanMin, "100%", null, bold = true)
    BreakdownRow(stringResource(R.string.sleep_interruptions), night.interruptionsMin, pct(night.interruptionsMin), palette.wake)
    BreakdownRow(stringResource(R.string.sleep_actual), night.actualSleepMin, pct(night.actualSleepMin), null, bold = true)
    BreakdownRow(stringResource(R.string.sleep_rem), night.remMin, pct(night.remMin), palette.rem)
    BreakdownRow(stringResource(R.string.sleep_light), night.lightMin, pct(night.lightMin), palette.light)
    BreakdownRow(stringResource(R.string.sleep_deep), night.deepMin, pct(night.deepMin), palette.deep)
}

@Composable
private fun BreakdownRow(label: String, minutes: Int, percent: String, dot: Color?, bold: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = if (bold) 0.dp else 12.dp),
    ) {
        if (dot != null) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dot),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(text = formatHm(minutes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = percent,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun Vitals(night: StoredNight) {
    VitalRow(stringResource(R.string.sleep_heart_rate), "${night.heartRate} bpm", "${night.heartRateBaseline} bpm")
    VitalRow(stringResource(R.string.sleep_hrv), "${night.hrvMs} ms", "${night.hrvBaselineMs} ms")
    VitalRow(
        stringResource(R.string.sleep_breathing),
        String.format(Locale.getDefault(), "%.1f /min", night.breathingRate),
        String.format(Locale.getDefault(), "%.1f /min", night.breathingRateBaseline),
    )
}

@Composable
private fun VitalRow(label: String, value: String, baseline: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 3.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(R.string.sleep_baseline, baseline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
