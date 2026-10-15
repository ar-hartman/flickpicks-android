package com.flykpicks.android.store

import android.util.Log
import com.flykpicks.android.service.GenreService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the genre table once per app run (iOS `GenreStore`). A failed load is silent: unknown
 * genres render as "—" and the next caller tries again. Genres are catalog data, not per-user.
 *
 * Titles get their genre names when they are fetched, so anything that maps titles waits for
 * this first. iOS lets the two requests race (ios-gaps.md G23).
 */
@Singleton
class GenreStore @Inject constructor(private val service: GenreService) {

    private val mutex = Mutex()

    @Volatile
    var isLoaded: Boolean = false
        private set

    /** Callers arriving while a load is in flight wait for it instead of starting another. */
    suspend fun loadIfNeeded() {
        if (isLoaded) return
        mutex.withLock {
            if (isLoaded) return
            try {
                service.load()
                isLoaded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Genre fetch failed; genres show as \"—\" until a later load succeeds", e)
            }
        }
    }

    private companion object {
        const val TAG = "GenreStore"
    }
}
