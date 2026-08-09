package com.janus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.janus.app.ui.auth.AuthScreen
import com.janus.app.ui.remote.RemoteScreen

/**
 * Project Janus navigation graph.
 *
 * Phase 1 scope: only the two destinations needed for the app shell to run
 * and show something meaningful — AUTH (first-launch screen, skippable) and
 * REMOTE (the main screen, showing the empty state since no device can be
 * connected yet). Every other destination in Routes.kt (drawer sections,
 * settings screens, diagnostics, device detail) is added to this NavHost as
 * its corresponding screen file is implemented in later phases — adding a
 * destination here is a mechanical, low-risk change since each screen is
 * self-contained.
 *
 * REMOTE is deliberately the graph's true entry point in later phases (per
 * requirement #4 — remote-control must not depend on login); AUTH is shown
 * first only on first launch via a "has the user seen auth" check that will
 * be wired in once SettingsRepository exists (Phase 2). For now this starts
 * at AUTH unconditionally so the skip button's navigation path is reachable
 * and testable immediately.
 */
@Composable
fun JanusNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.AUTH
    ) {
        composable(Routes.AUTH) {
            AuthScreen(
                onSkip = {
                    navController.navigate(Routes.REMOTE) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
                onAuthenticated = {
                    navController.navigate(Routes.REMOTE) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.REMOTE) {
            RemoteScreen()
        }
    }
}