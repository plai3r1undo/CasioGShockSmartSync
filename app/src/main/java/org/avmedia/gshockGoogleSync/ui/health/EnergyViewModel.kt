package org.avmedia.gshockGoogleSync.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.avmedia.gshockGoogleSync.data.sleep.SleepSyncManager
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class EnergyUiState(
    val date: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val day: EnergyDay? = null,
    val hasAnyData: Boolean = false,
) {
    val hasNewer: Boolean get() = date < today
}

/** Energy card: 5-minute energy from the life-log data stored by [SleepSyncManager], for any date. */
@HiltViewModel
class EnergyViewModel @Inject constructor(
    sleepSyncManager: SleepSyncManager,
) : ViewModel() {
    /** null = follow today. */
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    private val today = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val state: StateFlow<EnergyUiState> =
        combine(sleepSyncManager.activity, selectedDate, today) { activity, selected, today ->
            val zone = ZoneId.systemDefault()
            val date = selected?.coerceAtMost(today) ?: today
            EnergyUiState(
                date = date,
                today = today,
                day = activity.energyDay(date, zone),
                hasAnyData = activity.bins.any { it.kcal != null },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EnergyUiState())

    fun showOlder() = selectedDate.update { (it ?: state.value.today).minusDays(1) }

    fun showNewer() = selectedDate.update { current ->
        val today = state.value.today
        (current ?: today).plusDays(1).takeIf { it < today } // reaching today follows today again
    }

    fun select(date: LocalDate) {
        selectedDate.value = date.takeIf { it < state.value.today }
    }
}
