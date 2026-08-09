package com.janus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.janus.app.ui.navigation.JanusNavGraph
import com.janus.app.ui.theme.ProjectJanusTheme

/**
 * Project Janus single-Activity host. All screens (remote view, drawer
 * sections, settings, dialogs) are Compose destinations reached through
 * JanusNavGraph — there is no second Activity for "the remote control
 * screen": the live Target video and touch input both live inside this
 * same Activity's Compose tree (see RemoteSurfaceView.kt in a later phase).
 *
 * configChanges is handled in the manifest for orientation/screenSize, so
 * this Activity is NOT destroyed/recreated on rotation — critical later for
 * not tearing down the video decoder/Surface mid-stream on a Target
 * rotation event (requirement #37).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            JanusApp()
        }
    }
}

@Composable
private fun JanusApp() {
    ProjectJanusTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            JanusNavGraph()
        }
    }
}