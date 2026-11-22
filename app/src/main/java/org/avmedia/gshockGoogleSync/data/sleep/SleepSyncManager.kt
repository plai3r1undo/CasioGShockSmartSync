package org.avmedia.gshockGoogleSync.data.sleep

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.io.ActivityIOFunctional
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SleepSyncState(
    val nights: List<StoredNight> = emptyList(),
    val syncing: Boolean = false,
    val lastSyncEpochMs: Long? = null,
    val lastError: String? = null,
)

/**
 * Downloads new nights from the watch and persists them. Used both by the Health screen
 * and by the always-connected background action, so nights are saved even if the screen
 * is never opened (the watch keeps only a few nights and sends each one once).
 */
@Singleton
class SleepSyncManager @Inject constructor(
    private val api: GShockRepository,
    @param:ApplicationContext private val context: Context,
) {
    private val dir = File(context.filesDir, "sleep")
    private val store = SleepStore(dir)
    private val activityStore = ActivityStore(dir)
    private val mutex = Mutex()

    private val _activity = MutableStateFlow(loadActivity())

    /** Daytime activity (steps, kcal, active time) from the same transfers. */
    val activity: StateFlow<StoredActivity> = _activity.asStateFlow()

    private val _state = MutableStateFlow(SleepSyncState(nights = store.load()))
    val state: StateFlow<SleepSyncState> = _state.asStateFlow()

    val isSupported: Boolean get() = WatchInfo.hasSleepData

    /** Returns the number of new nights, or null if a sync was skipped or failed. */
    suspend fun sync(): Int? {
        if (!isSupported || !api.isConnected()) return null
        if (!mutex.tryLock()) return null // a sync is already running
        try {
            _state.update { it.copy(syncing = true, lastError = null) }
            val data = runCatching { api.getSleepData() }
                .onFailure { Timber.e(it, "Sleep sync failed") }
                .getOrNull()
            if (data == null) {
                _state.update { it.copy(syncing = false, lastError = "Sync failed") }
                return null
            }
            val nights = withContext(Dispatchers.IO) {
                store.archiveRaw(data.rawFiles)
                data.activity?.let { _activity.value = activityStore.merge(listOf(it)) }
                store.merge(data.nights.map(StoredNight::from))
            }
            if (data.warnings.isNotEmpty()) Timber.w("Sleep sync warnings: ${data.warnings}")
            val incomplete = data.warnings.firstOrNull { it.startsWith("transfer incomplete") }
            _state.update {
                it.copy(
                    nights = nights,
                    syncing = false,
                    lastSyncEpochMs = System.currentTimeMillis(),
                    lastError = incomplete,
                )
            }
            Timber.i(
                "Sleep sync: ${data.nights.size} new night(s), ${data.rawFiles.size} file(s), " +
                    "steps=${data.activity?.todaySteps} kcal=${data.activity?.todayKcal}"
            )
            return data.nights.size
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Cannot store sleep data")
            _state.update { it.copy(lastError = "Cannot store sleep data") }
            return null
        } finally {
            _state.update { it.copy(syncing = false) }
            mutex.unlock()
        }
    }

    /**
     * Loads stored activity. When there is no activity file yet, or it was written by an older
     * decoder, it is rebuilt from the raw transfers archived on every sync.
     */
    private fun loadActivity(): StoredActivity =
        runCatching {
            if (File(dir, "activity.json").exists()) {
                val stored = activityStore.load()
                if (stored.version >= StoredActivity.CURRENT_VERSION) return@runCatching stored
                activityStore.clear() // decoded by an older version: rebuild from the raw archive
            }
            val archived = store.archivedTransfers().mapNotNull(ActivityIOFunctional::parse)
            if (archived.isEmpty()) StoredActivity()
            else activityStore.merge(archived).also {
                Timber.i("Rebuilt activity from ${archived.size} archived transfer(s)")
            }
        }.onFailure { Timber.e(it, "Cannot load activity") }
            .getOrDefault(StoredActivity())
}
