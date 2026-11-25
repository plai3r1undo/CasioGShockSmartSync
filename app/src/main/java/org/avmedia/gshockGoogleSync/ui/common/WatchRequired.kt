package org.avmedia.gshockGoogleSync.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.avmedia.gshockGoogleSync.R

/**
 * Shows [content] only while the watch is connected. Screens that read their values from the
 * watch (time, alarms, watch settings) cannot work offline, so they show a short explanation
 * instead and load as soon as the watch connects.
 */
@Composable
fun WatchRequired(isConnected: Boolean, content: @Composable () -> Unit) {
    if (isConnected) {
        content()
    } else {
        WatchNotConnectedCard()
    }
}

@Composable
fun WatchNotConnectedCard(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.BluetoothDisabled,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.watch_not_connected),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.watch_not_connected_screen),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** How long the watch may take to connect before the warning appears. */
private const val NOT_CONNECTED_WARNING_DELAY_MS = 8_000L

/**
 * Warns once when the watch has not connected for a few seconds (on opening the app, or after
 * the connection was lost). Dismissing it keeps the app usable; it comes back only after the
 * watch has connected and been lost again.
 */
@Composable
fun WatchNotConnectedWarning(isConnected: Boolean) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(isConnected) {
        show = false
        if (!isConnected) {
            delay(NOT_CONNECTED_WARNING_DELAY_MS)
            show = true
        }
    }
    if (!show) return
    AlertDialog(
        onDismissRequest = { show = false },
        icon = { Icon(Icons.Default.BluetoothDisabled, contentDescription = null) },
        title = { Text(stringResource(R.string.watch_not_connected)) },
        text = { Text(stringResource(R.string.watch_not_connected_warning)) },
        confirmButton = {
            TextButton(onClick = { show = false }) { Text(stringResource(R.string.ok)) }
        },
    )
}
