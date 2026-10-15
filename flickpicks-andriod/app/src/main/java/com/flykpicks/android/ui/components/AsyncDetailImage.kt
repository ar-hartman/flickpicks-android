package com.flykpicks.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.flykpicks.android.AppConfig
import com.flykpicks.android.api.endpoints.FlykSource
import com.flykpicks.android.image.detailImageBackfillRetries
import com.flykpicks.android.image.detailImageRequestUrl
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType

/**
 * A title's wide backdrop, cropped to fill the space [modifier] gives it (iOS `AsyncDetailImage`).
 * While it loads, an ink gradient with a spinner; when there is none, the gradient with
 * "No backdrop available". An image the server is still generating is retried per
 * [com.flykpicks.android.image.PosterNetworkClient].
 *
 * The backend counts this request as a view of the title's detail screen, attributed to [source].
 * So it is kept out of the disk cache: like iOS, each app run asks the server once per title.
 */
@Composable
fun AsyncDetailImage(
    flyksId: String,
    modifier: Modifier = Modifier,
    source: String = FlykSource.LIBRARY_DETAIL,
) {
    Box(modifier.clipToBounds()) {
        if (LocalInspectionMode.current) {
            DetailImagePlaceholder(loading = false, Modifier.matchParentSize())
        } else {
            var state by remember(flyksId) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
            val context = LocalPlatformContext.current
            val request = remember(flyksId, source) {
                ImageRequest.Builder(context)
                    .data(detailImageRequestUrl(flyksId, AppConfig.apiBaseHttpUrl))
                    .httpHeaders(
                        NetworkHeaders.Builder()
                            .set("Accept", "application/octet-stream")
                            .set(FlykSource.HEADER, source)
                            .build(),
                    )
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .detailImageBackfillRetries()
                    .build()
            }
            if (state !is AsyncImagePainter.State.Success) {
                DetailImagePlaceholder(loading = state !is AsyncImagePainter.State.Error, Modifier.matchParentSize())
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                onState = { state = it },
            )
        }
    }
}

/**
 * The 220dp backdrop at the top of a title's detail screen, darkening toward its lower edge
 * (the iOS detail views' `heroBanner`). [source] is passed to [AsyncDetailImage].
 */
@Composable
fun DetailHeroBanner(flyksId: String, modifier: Modifier = Modifier, source: String = FlykSource.LIBRARY_DETAIL) {
    Box(modifier.fillMaxWidth().height(220.dp)) {
        AsyncDetailImage(flyksId, Modifier.matchParentSize(), source)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(100.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, FlykColors.Ink.copy(alpha = 0.7f)))),
        )
    }
}

/** Ink fading to cream. A spinner while [loading]; otherwise the "no backdrop" mark. */
@Composable
internal fun DetailImagePlaceholder(loading: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(FlykColors.Canvas)
            .background(
                Brush.linearGradient(
                    listOf(
                        FlykColors.Ink.copy(alpha = 0.85f),
                        FlykColors.Ink.copy(alpha = 0.40f),
                        FlykColors.Canvas.copy(alpha = 0.30f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp), color = FlykColors.Brass, strokeWidth = 2.dp)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = FlykColors.Brass.copy(alpha = 0.6f),
                    modifier = Modifier.size(30.dp),
                )
                Text(
                    "No backdrop available",
                    style = FlykType.Caption2.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
                    color = FlykColors.Brass.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Preview
@Composable
private fun DetailImagePlaceholderPreview() {
    FlykPicksTheme {
        DetailImagePlaceholder(loading = false, Modifier.fillMaxWidth().height(220.dp))
    }
}
