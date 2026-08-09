package com.janus.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Project Janus corner-radius scale. Slightly tighter/squarer radii than
 * stock Material3 defaults to reinforce the industrial/console aesthetic
 * (cards, dialogs, and the device tab bar should read as precise panels,
 * not soft/playful bubbles).
 */
val JanusShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)