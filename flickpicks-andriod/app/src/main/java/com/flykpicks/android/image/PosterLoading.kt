package com.flykpicks.android.image

import coil3.Extras
import coil3.getOrDefault
import coil3.network.NetworkClient
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import okhttp3.HttpUrl
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The URL to load a title's poster from (iOS `AsyncPosterImage.effectivePosterUrl`): the server's
 * `posterUrl` when it sent one (relative URLs resolve against the API), otherwise the poster
 * endpoint by id, so titles the server hasn't stored artwork for yet can still load.
 * Null when there is nothing to load.
 *
 * The endpoint's parameter is `flyksId`, as in the `posterUrl`s the server sends. iOS builds its
 * fallback with `mediaID`, which the server rejects (ios-gaps.md G25).
 */
fun posterRequestUrl(posterUrl: String?, flyksId: String?, apiBaseUrl: HttpUrl): String? {
    if (!posterUrl.isNullOrEmpty()) return apiBaseUrl.resolve(posterUrl)?.toString()
    if (flyksId.isNullOrEmpty()) return null
    return apiBaseUrl.resolve("v1/media/poster")!!
        .newBuilder()
        .addQueryParameter("flyksId", flyksId)
        .build()
        .toString()
}

/** The wide backdrop shown at the top of a title's detail screen (iOS `AsyncDetailImage`). */
fun detailImageRequestUrl(flyksId: String, apiBaseUrl: HttpUrl): String =
    apiBaseUrl.resolve("v1/media/detailImage")!!
        .newBuilder()
        .addQueryParameter("mediaID", flyksId)
        .build()
        .toString()

/** Marks an image request as a poster load, so [PosterNetworkClient] applies the backfill rules. */
fun ImageRequest.Builder.posterBackfillRetries(): ImageRequest.Builder = apply {
    extras[backfillPendingCodesKey] = POSTER_PENDING_CODES
}

/** As [posterBackfillRetries], for the detail image, where only 202 means "not ready yet". */
fun ImageRequest.Builder.detailImageBackfillRetries(): ImageRequest.Builder = apply {
    extras[backfillPendingCodesKey] = DETAIL_IMAGE_PENDING_CODES
}

/** The statuses that mean "ask again shortly". Empty on requests the backfill rules don't apply to. */
internal val backfillPendingCodesKey = Extras.Key<Set<Int>>(default = emptySet())

internal val POSTER_PENDING_CODES = setOf(202, 404)
internal val DETAIL_IMAGE_PENDING_CODES = setOf(202)

/** The server is still generating the poster after every quick retry. Try again later. */
class PosterPendingException(url: String) : IOException("Poster still pending: $url")

/** The server has no poster for this title (204). */
class PosterUnavailableException(url: String) : IOException("No poster: $url")

/**
 * Applies the iOS poster backfill protocol to requests marked with [posterBackfillRetries] or
 * [detailImageBackfillRetries]:
 *
 * - 202 (artwork being generated) or, for posters, 404 (title not registered yet): wait and
 *   retry, 2 s, 4 s, then 8 s apart. When those run out, fail with [PosterPendingException]; for
 *   a poster the UI waits 30 s and tries once more.
 * - 204 (no image exists): fail with [PosterUnavailableException] without reading a body.
 *
 * Neither outcome reaches Coil's fetcher, so none of them is written to the disk cache.
 * Waits are coroutine delays, so a poster scrolled off screen cancels cleanly.
 */
class PosterNetworkClient(
    private val delegate: NetworkClient,
    private val retryDelays: List<Duration> = DEFAULT_RETRY_DELAYS,
) : NetworkClient {

    override suspend fun <T> executeRequest(
        request: NetworkRequest,
        block: suspend (response: NetworkResponse) -> T,
    ): T {
        val pendingCodes = request.extras.getOrDefault(backfillPendingCodesKey)
        if (pendingCodes.isEmpty()) return delegate.executeRequest(request, block)

        var retries = 0
        while (true) {
            val attempt = delegate.executeRequest(request) { response ->
                when (response.code) {
                    in pendingCodes -> Attempt.Pending
                    NO_CONTENT -> Attempt.Unavailable
                    else -> Attempt.Done(block(response))
                }
            }
            when (attempt) {
                is Attempt.Done -> return attempt.value
                Attempt.Unavailable -> throw PosterUnavailableException(request.url)
                Attempt.Pending -> {
                    if (retries == retryDelays.size) throw PosterPendingException(request.url)
                    delay(retryDelays[retries++])
                }
            }
        }
    }

    private sealed interface Attempt<out T> {
        data class Done<T>(val value: T) : Attempt<T>
        data object Pending : Attempt<Nothing>
        data object Unavailable : Attempt<Nothing>
    }

    companion object {
        val DEFAULT_RETRY_DELAYS = listOf(2.seconds, 4.seconds, 8.seconds)

        /** After [PosterPendingException], how long the UI waits before its one last try. */
        val LATE_RETRY_DELAY = 30.seconds

        private const val NO_CONTENT = 204
    }
}
