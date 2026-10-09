package com.flykpicks.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flykpicks.android.di.ApplicationScope
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

// iOS ToastStore / ToastView (blueprint §9.7, §11.19). Every iOS toast is a success message with
// the same checkmark, so there's no icon parameter.

data class ToastMessage(val text: String)

/** One app-wide confirmation toast at a time. A new one replaces the current and restarts its timer. */
@Singleton
class ToastStore @Inject constructor(@ApplicationScope private val scope: CoroutineScope) {
    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()

    private var dismissJob: Job? = null

    fun show(text: String) {
        dismissJob?.cancel()
        _current.value = ToastMessage(text)
        dismissJob = scope.launch {
            delay(DISPLAY_DURATION)
            _current.value = null
        }
    }

    companion object {
        /** iOS shows toasts for 2 s (the blueprint's 3 s isn't what iOS does). */
        val DISPLAY_DURATION = 2.seconds
    }
}

/** Shows [toastStore]'s current toast, sliding up from the bottom of the space [modifier] gives it. */
@Composable
fun ToastOverlay(toastStore: ToastStore, modifier: Modifier = Modifier) {
    val toast by toastStore.current.collectAsStateWithLifecycle()
    ToastHost(toast, modifier)
}

@Composable
internal fun ToastHost(toast: ToastMessage?, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = toast,
        modifier = modifier,
        transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith fadeOut() },
        contentAlignment = Alignment.BottomCenter,
        label = "toast",
    ) { message ->
        if (message != null) ToastView(message)
    }
}

@Composable
private fun ToastView(message: ToastMessage) {
    Row(
        modifier = Modifier
            .dropShadow(CircleShape, Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.25f), offset = DpOffset(0.dp, 5.dp)))
            .background(FlykColors.Ink, CircleShape)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FlykColors.Brass, modifier = Modifier.size(16.dp))
        Text(message.text, style = FlykType.Subheadline.copy(fontWeight = FontWeight.SemiBold), color = FlykColors.NavBarText)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F0)
@Composable
private fun ToastPreview() {
    FlykPicksTheme {
        ToastHost(ToastMessage("Password changed successfully"), Modifier.padding(24.dp))
    }
}
