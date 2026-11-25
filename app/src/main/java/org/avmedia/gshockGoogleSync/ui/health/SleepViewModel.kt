package org.avmedia.gshockGoogleSync.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.sleep.SleepSyncManager
import org.avmedia.gshockGoogleSync.data.sleep.StoredHeartRate
import org.avmedia.gshockGoogleSync.data.sleep.StoredNight
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Sleep charge of the night that ended on [date], or null when no night was received. */
data class SleepChargeDay(val date: LocalDate, val charge: Int?)

/**
 * Sleep charge per day for the [days] days ending on [endDate] (oldest first), keyed by the
 * night's wake date. If two nights end on the same day, the longer one counts.
 */
internal fun sleepChargeTrend(
    nights: List<StoredNight>,
    endDate: LocalDate,
    days: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): List<SleepChargeDay> {
    val byDate = nights.groupBy { it.wakeDate(zone) }.mapValues { (_, n) -> n.maxBy { it.spanMin } }
    return (days - 1 downTo 0).map { back ->
        val date = endDate.minusDays(back.toLong())
        SleepChargeDay(date, byDate[date]?.sleepCharge)
    }
}

/**
 * Last day of the trend chart's window when [selected] gets selected. The window stays put while
 * the selection is inside it (so tapping bars never scrolls the chart); otherwise it moves just
 * enough to show [selected] with up to half a window of newer days, never past [newest].
 */
internal fun trendWindowEnd(
    currentEnd: LocalDate?,
    selected: LocalDate,
    newest: LocalDate,
    days: Int,
): LocalDate {
    val end = minOf(currentEnd ?: newest, newest)
    val start = end.minusDays(days - 1L)
    if (!selected.isBefore(start) && !selected.isAfter(end)) return end
    return minOf(newest, selected.plusDays(days / 2L))
}

data class SleepUiState(
    val nights: List<StoredNight> = emptyList(), // newest first
    val selectedIndex: Int = 0,
    val syncing: Boolean = false,
    val error: String? = null,
    /** Heart-rate readings (0x52) during the selected night, oldest first. */
    val heartRate: List<StoredHeartRate> = emptyList(),
    /** Last day of the trend chart; null means the newest night. */
    val trendEnd: LocalDate? = null,
    /** Day notes keyed by wake date. */
    val notes: Map<LocalDate, String> = emptyMap(),
) {
    val selected: StoredNight? get() = nights.getOrNull(selectedIndex)
    val hasOlder: Boolean get() = selectedIndex < nights.lastIndex
    val hasNewer: Boolean get() = selectedIndex > 0

    /** Wake dates that have a night, for the date picker. */
    val nightDates: Set<LocalDate> get() = nights.map { it.wakeDate() }.toSet()

    /** Sleep charge over the [TREND_DAYS] days of the chart window (see [trendWindowEnd]). */
    val trend: List<SleepChargeDay>
        get() {
            val newest = nights.firstOrNull()?.wakeDate() ?: return emptyList()
            val selectedDate = selected?.wakeDate() ?: return emptyList()
            val end = trendWindowEnd(trendEnd, selectedDate, newest, TREND_DAYS)
            return sleepChargeTrend(nights, end, TREND_DAYS)
        }

    /** Note of the selected night's day, if any. */
    val selectedNote: String? get() = selected?.let { notes[it.wakeDate()] }

    companion object {
        const val TREND_DAYS = 14
    }
}

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val sleepSyncManager: SleepSyncManager,
) : ViewModel() {
    private val selectedIndex = MutableStateFlow(0)
    private val trendEnd = MutableStateFlow<LocalDate?>(null)

    val isSupported: Boolean get() = sleepSyncManager.isSupported

    val state: StateFlow<SleepUiState> =
        combine(
            sleepSyncManager.state,
            sleepSyncManager.activity,
            selectedIndex,
            trendEnd,
            sleepSyncManager.notes,
        ) { s, activity, index, end, notes ->
            val selected = index.coerceIn(0, (s.nights.size - 1).coerceAtLeast(0))
            SleepUiState(
                trendEnd = end,
                notes = notes,
                nights = s.nights,
                selectedIndex = selected,
                syncing = s.syncing,
                error = s.lastError,
                heartRate = s.nights.getOrNull(selected)
                    ?.let { activity.heartRateBetween(it.start, it.end) }.orEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepUiState())

    init {
        sync()
    }

    fun sync() {
        viewModelScope.launch {
            val newNights = sleepSyncManager.sync()
            if (newNights != null && newNights > 0) {
                selectedIndex.value = 0
                trendEnd.value = null
            }
        }
    }

    fun showOlder() = selectIndex(selectedIndex.value + 1)

    fun showNewer() = selectIndex(selectedIndex.value - 1)

    /** Selects the night that ended on [date], if there is one. */
    fun select(date: LocalDate) {
        val index = state.value.nights.indexOfFirst { it.wakeDate() == date }
        if (index >= 0) selectIndex(index)
    }

    private fun selectIndex(index: Int) {
        val nights = state.value.nights
        if (nights.isEmpty()) return
        val i = index.coerceIn(0, nights.lastIndex)
        trendEnd.value = trendWindowEnd(
            trendEnd.value, nights[i].wakeDate(), nights.first().wakeDate(), SleepUiState.TREND_DAYS,
        )
        selectedIndex.value = i
    }

    /** Saves the note for the selected night's day; blank text deletes it. */
    fun saveNote(text: String) {
        val date = state.value.selected?.wakeDate() ?: return
        viewModelScope.launch { sleepSyncManager.setNote(date, text) }
    }
}
