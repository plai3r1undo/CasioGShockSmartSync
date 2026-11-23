package org.avmedia.gshockGoogleSync.ui.health

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.sleep.SleepSyncManager
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

/** Export and import of all health data (nights, activity, raw watch files) as a zip. */
@HiltViewModel
class HealthBackupViewModel @Inject constructor(
    private val sleepSyncManager: SleepSyncManager,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun suggestedFileName(): String = "gshock-health-${LocalDate.now()}.zip"

    fun export(uri: Uri) = run(R.string.health_export_failed) {
        val count = context.contentResolver.openOutputStream(uri)?.use { sleepSyncManager.exportTo(it) }
            ?: error("Cannot open $uri")
        context.getString(R.string.health_export_done, count)
    }

    fun import(uri: Uri) = run(R.string.health_import_failed) {
        val result = context.contentResolver.openInputStream(uri)?.use { sleepSyncManager.importFrom(it) }
            ?: error("Cannot open $uri")
        context.getString(R.string.health_import_done, result.rawFilesAdded)
    }

    private fun run(failure: Int, block: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val message = runCatching { block() }
                .onFailure { Timber.e(it, "Health backup failed") }
                .getOrElse { context.getString(failure) }
            _busy.value = false
            AppSnackbar(message)
        }
    }
}
