package com.janus.app.ui.remote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janus.app.viewmodel.RemoteScreenViewModel

@Composable
fun RemoteScreen(viewModel: RemoteScreenViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Phase 4 Test Button
        Button(
            onClick = { viewModel.runPhase4Tests() },
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Run Phase 4 Tests")
        }

        // Display test results
        when (val state = uiState) {
            is RemoteScreenViewModel.UiState.Success -> Text(state.message)
            is RemoteScreenViewModel.UiState.Error -> Text(state.message)
            RemoteScreenViewModel.UiState.Idle -> EmptyStateView()
        }

        // Rest of the UI (e.g., RemoteSurfaceView)
        RemoteSurfaceView(viewModel)
    }
}
