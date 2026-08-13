package com.janus.app.ui.pairing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.janus.app.JanusApplication
import com.janus.app.viewmodel.PairingUiState
import com.janus.app.viewmodel.PairingViewModel

/**
 * Assembles the individual pairing dialogs (spec #15) into the full
 * sequential flow: IP -> port -> code -> result, driven entirely by
 * [PairingViewModel]'s state.
 */
@Composable
fun PairingFlowScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val appModule = (context.applicationContext as JanusApplication).appModule
    val viewModel: PairingViewModel = viewModel(
        factory = PairingViewModel.Factory(appModule.adbKeystoreManager)
    )

    val state by viewModel.uiState.collectAsState()

    when (val current = state) {
        is PairingUiState.EnterIp -> {
            PairIpDialog(
                onSubmit = viewModel::onIpEntered,
                onDismiss = onFinished
            )
        }

        is PairingUiState.EnterPort -> {
            PairPortDialog(
                error = current.error,
                onSubmit = viewModel::onPortEntered,
                onDismiss = onFinished
            )
        }

        is PairingUiState.EnterCode -> {
            PairCodeDialog(
                error = current.error,
                onSubmit = viewModel::onCodeEntered,
                onDismiss = onFinished
            )
        }

        is PairingUiState.Pairing -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Pairing…") },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                },
                confirmButton = {}
            )
        }

        PairingUiState.Success -> {
            AlertDialog(
                onDismissRequest = onFinished,
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        PairingResultBanner(success = true)
                    }
                },
                confirmButton = {
                    Button(onClick = onFinished) {
                        Text("Done")
                    }
                }
            )
        }

        is PairingUiState.Failure -> {
            AlertDialog(
                onDismissRequest = onFinished,
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        PairingResultBanner(success = false)
                    }
                },
                confirmButton = {
                    Button(onClick = viewModel::reset) {
                        Text("Try Again")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onFinished) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}