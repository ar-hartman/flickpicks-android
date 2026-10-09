package com.flykpicks.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Blueprint §3.3. */
val FlykShapes = Shapes(
    // Poster images
    small = RoundedCornerShape(6.dp),
    // Smaller interactive elements
    medium = RoundedCornerShape(14.dp),
    // Large cards, bottom sheets
    large = RoundedCornerShape(18.dp),
)
