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
import org.avmedia.gshockGoogleSync.data.sleep.StoredNight
import javax.inject.Inject

data class SleepUiState(
    val nights: List<StoredNight> = emptyList(), // newest first
    val selectedIndex: Int = 0,
    val syncing: Boolean = false,
    val error: String? = null,
) {
    val selected: StoredNight? get() = nights.getOrNull(selectedIndex)
    val hasOlder: Boolean get() = selectedIndex < nights.lastIndex
    val hasNewer: Boolean get() = selectedIndex > 0
}

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val sleepSyncManager: SleepSyncManager,
) : ViewModel() {
    private val selectedIndex = MutableStateFlow(0)

    val isSupported: Boolean get() = sleepSyncManager.isSupported

    val state: StateFlow<SleepUiState> =
        combine(sleepSyncManager.state, selectedIndex) { s, index ->
            SleepUiState(
                nights = s.nights,
                selectedIndex = index.coerceIn(0, (s.nights.size - 1).coerceAtLeast(0)),
                syncing = s.syncing,
                error = s.lastError,
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
}
