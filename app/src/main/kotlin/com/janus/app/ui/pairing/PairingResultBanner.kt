package com.janus.app.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.janus.app.R
import com.janus.app.ui.theme.JanusNotificationError
import com.janus.app.ui.theme.JanusNotificationSuccess

/**
 * Success/failure result banner shown after a pairing attempt (spec #15):
 * "Paired" on success, "Recheck your information and try again." on
 * failure -- no lower-level protocol detail exposed to the user, matching
 * the spec's "do not expose unnecessary technical complexity" requirement.
 */
@Composable
fun PairingResultBanner(
    success: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (success) JanusNotificationSuccess else JanusNotificationError
    val messageRes = if (success) R.string.pairing_success else R.string.pairing_failure

    Text(
        text = stringResource(messageRes),
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .padding(16.dp)
    )
}