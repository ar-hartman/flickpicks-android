package com.flykpicks.android.api

import com.flykpicks.android.store.TokenStore
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation

/**
 * Marks a Retrofit endpoint as public: no bearer token is attached, and a 401 from it is
 * returned to the caller rather than triggering a token refresh (iOS `requiresAuth: false`).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Unauthenticated

internal const val AUTHORIZATION_HEADER = "Authorization"

internal fun Request.isUnauthenticated(): Boolean =
    tag(Invocation::class.java)?.method()?.isAnnotationPresent(Unauthenticated::class.java) == true

internal fun Request.withBearer(token: String): Request =
    newBuilder().header(AUTHORIZATION_HEADER, "Bearer $token").build()

/**
 * Adds `Authorization: Bearer <access token>` to authenticated requests (blueprint §7.1).
 * With [onlyOrigin] set, other origins (e.g. a CDN poster URL) never get the token.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val onlyOrigin: HttpUrl? = null,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.isUnauthenticated() || request.header(AUTHORIZATION_HEADER) != null) {
            return chain.proceed(request)
        }
        if (onlyOrigin != null && !request.url.hasOriginOf(onlyOrigin)) return chain.proceed(request)
        val token = tokenStore.accessToken() ?: return chain.proceed(request)
        return chain.proceed(request.withBearer(token))
    }
}

internal fun HttpUrl.hasOriginOf(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port
