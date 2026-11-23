package org.avmedia.gshockGoogleSync.ui.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.collectAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockapi.WatchInfo

@Composable
fun HealthScreen() {
    var showProfileDialog by remember { mutableStateOf(false) }

    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle(
                    stringResource(id = R.string.health),
                    Modifier
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Sleep card — Nightly Recharge data stored from the watch (DW-H5600)
                    if (WatchInfo.hasSleepData) {
                        SleepCardView(modifier = Modifier.fillMaxWidth())
                    }

                    // Steps card — shows Today, Hourly, and Daily panels all at once
                    StepCounterView(
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Energy card — 5-minute energy from the life-log (DW-H5600)
                    if (WatchInfo.hasSleepData) {
                        EnergyCardView(modifier = Modifier.fillMaxWidth())
                    }

                    // Profile button — opens exercise profile dialog
                    AppButton(
                        text = stringResource(id = R.string.profile),
                        onClick = { showProfileDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )

                    HealthBackupButtons()

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Exercise profile dialog — shown when user taps the Profile button
    if (showProfileDialog) {
        ProfileDialog(
            onDismiss = { showProfileDialog = false }
        )
    }
}

/** Export / import of all health data as a zip, through the system file picker. */
@Composable
private fun HealthBackupButtons(viewModel: HealthBackupViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::export) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        AppButton(
            text = stringResource(R.string.health_export),
            onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
            enabled = !busy,
            modifier = Modifier.weight(1f),
        )
        AppButton(
            text = stringResource(R.string.health_import),
            onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
            enabled = !busy,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewHealthScreen() {
    HealthScreen()
}
