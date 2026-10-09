package com.flykpicks.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.flykpicks.android.AppConfig
import com.flykpicks.android.image.PosterNetworkClient
import com.flykpicks.android.image.PosterPendingException
import com.flykpicks.android.image.posterBackfillRetries
import com.flykpicks.android.image.posterRequestUrl
import com.flykpicks.android.ui.theme.DS
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import kotlinx.coroutines.delay

/**
 * A title's poster, cropped to fill the space the [modifier] gives it (iOS `AsyncPosterImage`,
 * blueprint §11.10). Loads `posterUrl`, or the poster endpoint by [flyksId] when there is none.
 *
 * While loading it shows the gradient placeholder with a spinner; with nothing to load, or on
 * failure, the placeholder alone. Posters still being generated server-side are retried per
 * [PosterNetworkClient], then once more after 30 s.
 */
@Composable
fun AsyncPosterImage(
    posterUrl: String?,
    flyksId: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val url = remember(posterUrl, flyksId) { posterRequestUrl(posterUrl, flyksId, AppConfig.apiBaseHttpUrl) }
    Box(modifier.clipToBounds()) {
        if (url == null || LocalInspectionMode.current) {
            PosterPlaceholder(loading = false, Modifier.matchParentSize())
        } else {
            var lateRetry by remember(url) { mutableStateOf(false) }
            var state by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
            val context = LocalPlatformContext.current
            val request = remember(url, lateRetry) {
                ImageRequest.Builder(context)
                    .data(url)
                    .httpHeaders(OctetStreamAccept)
                    .posterBackfillRetries()
                    .build()
            }
            if (state !is AsyncImagePainter.State.Success) {
                PosterPlaceholder(loading = state is AsyncImagePainter.State.Loading, Modifier.matchParentSize())
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                onState = { state = it },
            )
            val pending = (state as? AsyncImagePainter.State.Error)?.result?.throwable is PosterPendingException
            if (pending && !lateRetry) {
                LaunchedEffect(url) {
                    delay(PosterNetworkClient.LATE_RETRY_DELAY)
                    lateRetry = true
                }
            }
        }
    }
}

// iOS asks for raw bytes from the poster endpoint.
private val OctetStreamAccept = NetworkHeaders.Builder().set("Accept", "application/octet-stream").build()

/**
 * Cream-to-jewel gradient with a brass film icon; [loading] adds a spinner. The cream base keeps
 * it opaque: on iOS the gradient's translucent stops sit over the cream row, and a poster's drop
 * shadow must not show through.
 */
@Composable
internal fun PosterPlaceholder(loading: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(FlykColors.Canvas)
            .background(
                Brush.linearGradient(
                    listOf(FlykColors.Canvas, FlykColors.Sapphire.copy(alpha = 0.15f), FlykColors.Jade.copy(alpha = 0.10f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Theaters, contentDescription = null, tint = FlykColors.Brass, modifier = Modifier.size(36.dp))
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = FlykColors.InkMuted, strokeWidth = 2.dp)
        }
    }
}

@Preview
@Composable
private fun PosterPlaceholderPreview() {
    FlykPicksTheme {
        PosterPlaceholder(loading = true, Modifier.width(DS.PosterWidthLarge).aspectRatio(2f / 3f))
    }
}
