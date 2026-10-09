package com.flykpicks.android.service

import com.flykpicks.android.api.ApiError
import javax.inject.Qualifier

/** Dispatcher for blocking work (Keystore crypto behind `TokenStore`). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Runs [block], replacing an [ApiError.Http] with the typed error [map] returns for it.
 * Statuses [map] doesn't recognize (it returns null) propagate unchanged.
 */
internal inline fun <T> mapHttpErrors(map: (ApiError.Http) -> Throwable?, block: () -> T): T =
    try {
        block()
    } catch (e: ApiError.Http) {
        throw map(e) ?: e
    }
