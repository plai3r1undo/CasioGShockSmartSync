package org.avmedia.gshockGoogleSync.pairing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.WatchInfo
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether at least one watch is paired with the app. With a paired watch the app opens on its
 * main screens even while the watch is not connected (the companion service keeps connecting
 * in the background); the pairing screen is only for the first setup.
 */
@Singleton
class PairedWatchState @Inject constructor(
    private val repository: GShockRepository,
    @param:ApplicationContext private val context: Context,
) {
    private val _hasPairedWatch = MutableStateFlow(compute())
    val hasPairedWatch: StateFlow<Boolean> = _hasPairedWatch.asStateFlow()

    init {
        primeWatchInfo()
    }

    /** Call after a watch was paired or removed. */
    fun refresh() {
        _hasPairedWatch.value = compute()
    }

    private fun compute(): Boolean =
        runCatching {
            repository.getAssociationsWithNames(context).isNotEmpty() ||
                LocalDataStorage.getDeviceAddresses(context).isNotEmpty()
        }.onFailure { Timber.w(it, "Cannot read paired watches") }
            .getOrDefault(false)

    /**
     * Until a watch connects, the library knows no model, so features such as the Health tab
     * would be hidden. Use the last connected watch's model in the meantime; the real watch
     * replaces it when it connects.
     */
    private fun primeWatchInfo() {
        runCatching {
            if (WatchInfo.name.isNotBlank()) return
            val last = LocalDataStorage.get(context, KEY_LAST_DEVICE_NAME, "").orEmpty()
            if (last.isNotBlank()) WatchInfo.setNameAndModel(last)
        }.onFailure { Timber.w(it, "Cannot restore the last watch model") }
    }

    private companion object {
        const val KEY_LAST_DEVICE_NAME = "LastDeviceName"
    }
}
