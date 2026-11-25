package org.avmedia.gshockGoogleSync.ui.settings

import AppTextLarge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.others.PreConnectionViewModel
import org.avmedia.gshockGoogleSync.ui.others.rememberAddWatch
import org.avmedia.gshockGoogleSync.utils.Utils

/**
 * The paired watches: which one is in use and whether it is connected, plus pairing a new
 * watch and removing one. Available without a connected watch.
 */
@Composable
fun MyWatchesCard(isWatchConnected: Boolean, modifier: Modifier = Modifier) {
    val viewModel: PreConnectionViewModel = hiltViewModel()
    val devices by viewModel.pairedDevices.collectAsState()
    val context = LocalContext.current
    val addWatch = rememberAddWatch(viewModel)
    var toRemove by remember { mutableStateOf<PreConnectionViewModel.DeviceItem?>(null) }

    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            AppTextLarge(text = stringResource(R.string.my_watches))
            Spacer(Modifier.height(8.dp))
            if (devices.isEmpty()) {
                Text(
                    text = stringResource(R.string.my_watches_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            devices.forEach { device ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setDevice(Utils.sanitizeMacAddress(device.address), device.name)
                        }
                        .padding(vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Watch, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (device.isLastUsed) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val status = when {
                            device.isLastUsed && isWatchConnected -> stringResource(R.string.my_watches_connected)
                            device.isLastUsed -> stringResource(R.string.my_watches_not_connected)
                            else -> null
                        }
                        if (status != null) {
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isWatchConnected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { toRemove = device }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.my_watches_remove))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = addWatch.start, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_watch))
            }
        }
    }

    toRemove?.let { device ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text(stringResource(R.string.my_watches_remove_title, device.name)) },
            text = { Text(stringResource(R.string.my_watches_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.disassociate(context, Utils.sanitizeMacAddress(device.address))
                    toRemove = null
                }) {
                    Text(stringResource(R.string.my_watches_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { toRemove = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
