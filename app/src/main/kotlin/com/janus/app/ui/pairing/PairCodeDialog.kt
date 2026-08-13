package com.janus.app.ui.pairing

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.janus.app.R

/**
 * Pairing window 3 of 3 (spec #15): 6-digit pairing code entry, shown on
 * the Target's Developer Options screen during pairing.
 */
@Composable
fun PairCodeDialog(
    error: String? = null,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pairing_code_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 6) text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                label = { Text(stringResource(R.string.pairing_code_title)) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } }
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(text) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}