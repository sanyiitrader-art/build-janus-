package com.janus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.janus.app.ui.auth.AuthScreen
import com.janus.app.ui.discovery.DiscoveryScreen
import com.janus.app.ui.pairing.PairingFlowScreen
import com.janus.app.ui.remote.RemoteScreen

/**
 * Project Janus navigation graph.
 *
 * Phase 4 adds Routes.PAIRING -> PairingFlowScreen, reached by tapping a
 * discovered device on DiscoveryScreen. Pairing currently always starts
 * from a blank IP entry (spec #15's 3-step dialog) rather than pre-filling
 * the tapped device's address -- pre-fill can be added later without
 * changing this graph's shape, once there's a clear place to pass that
 * value through (e.g. via a route argument or shared ViewModel).
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
            DiscoveryScreen(
                onDeviceClick = { navController.navigate(Routes.PAIRING) }
            )
        }

        composable(Routes.PAIRING) {
            PairingFlowScreen(
                onFinished = { navController.popBackStack() }
            )
        }
    }
}
