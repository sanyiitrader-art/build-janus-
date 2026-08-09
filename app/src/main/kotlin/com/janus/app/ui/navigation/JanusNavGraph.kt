package com.janus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.janus.app.ui.auth.AuthScreen
import com.janus.app.ui.discovery.DiscoveryScreen
import com.janus.app.ui.remote.RemoteScreen

/**
 * Project Janus navigation graph.
 *
 * Phase 3 adds Routes.DEVICES_FOUND -> DiscoveryScreen, reached via
 * RemoteScreen's TEMPORARY button (see RemoteScreen.kt) until the real
 * swipe-right navigation drawer (spec #7) replaces it in Phase 10.
 *
 * REMOTE is deliberately the graph's true entry point in later phases (per
 * requirement #4 — remote-control must not depend on login); AUTH is shown
 * first only on first launch via a "has the user seen auth" check that will
 * be wired in once SettingsRepository's auth-seen flag exists. For now this
 * starts at AUTH unconditionally so the skip button's navigation path is
 * reachable and testable immediately.
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
            RemoteScreen(
                onOpenDiscovery = { navController.navigate(Routes.DEVICES_FOUND) }
            )
        }

        composable(Routes.DEVICES_FOUND) {
            DiscoveryScreen()
        }
    }
}