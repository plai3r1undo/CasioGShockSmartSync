package org.avmedia.gshockGoogleSync.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

data class SleepUiState(
    val nights: List<StoredNight> = emptyList(), // newest first
    val selectedIndex: Int = 0,
    val syncing: Boolean = false,
    val error: String? = null,
    /** Heart-rate readings (0x52) during the selected night, oldest first. */
    val heartRate: List<StoredHeartRate> = emptyList(),
) {
    val selected: StoredNight? get() = nights.getOrNull(selectedIndex)
    val hasOlder: Boolean get() = selectedIndex < nights.lastIndex
    val hasNewer: Boolean get() = selectedIndex > 0

    /** Wake dates that have a night, for the date picker. */
    val nightDates: Set<LocalDate> get() = nights.map { it.wakeDate() }.toSet()

    /** Sleep charge over the [TREND_DAYS] days ending with the selected night. */
    val trend: List<SleepChargeDay>
        get() = selected?.let { sleepChargeTrend(nights, it.wakeDate(), TREND_DAYS) }.orEmpty()

    companion object {
        const val TREND_DAYS = 14
    }
}

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val sleepSyncManager: SleepSyncManager,
) : ViewModel() {
    private val selectedIndex = MutableStateFlow(0)

    val isSupported: Boolean get() = sleepSyncManager.isSupported

    val state: StateFlow<SleepUiState> =
        combine(sleepSyncManager.state, sleepSyncManager.activity, selectedIndex) { s, activity, index ->
            val selected = index.coerceIn(0, (s.nights.size - 1).coerceAtLeast(0))
            SleepUiState(
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
            if (newNights != null && newNights > 0) selectedIndex.value = 0
        }
    }

    fun showOlder() = selectedIndex.update { (it + 1).coerceAtMost((state.value.nights.size - 1).coerceAtLeast(0)) }

    fun showNewer() = selectedIndex.update { (it - 1).coerceAtLeast(0) }

    /** Selects the night that ended on [date], if there is one. */
    fun select(date: LocalDate) {
        val index = state.value.nights.indexOfFirst { it.wakeDate() == date }
        if (index >= 0) selectedIndex.value = index
    }
}
