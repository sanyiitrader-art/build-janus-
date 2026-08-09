package com.janus.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.janus.app.R

/**
 * Project Janus first-launch authentication screen (spec #4).
 *
 * Login is OPTIONAL — this screen must never block access to the
 * remote-control system. Google login and account creation are UI-complete
 * here but not yet wired to a real credential flow (that lands with the
 * dedicated auth subsystem — AuthRepository / GoogleAuthProvider — since
 * Google Sign-In requires a real OAuth client ID that only the project owner
 * can provision; see AuthViewModel.kt for the isolation boundary). The
 * Google button is intentionally shown as present-but-not-yet-functional so
 * the layout doesn't need to change once real credentials are added.
 *
 * Layout matches spec #4 exactly: Skip top-right, Sign-up near bottom-left,
 * primary actions (Google login, create account) centered.
 */
@Composable
fun AuthScreen(
    onSkip: () -> Unit,
    onAuthenticated: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {

        // Skip — top-right
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text(text = stringResource(R.string.auth_skip))
        }

        // Primary auth actions — centered
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onAuthenticated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.auth_login_google))
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onAuthenticated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.auth_create_account))
            }
        }

        // Sign up — near bottom-left
        TextButton(
            onClick = onAuthenticated,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(text = stringResource(R.string.auth_sign_up))
        }
    }
}