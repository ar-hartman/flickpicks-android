package com.flykpicks.android.di

import android.content.Context
import com.flykpicks.android.service.IoDispatcher
import com.flykpicks.android.store.FeatureFlagCache
import com.flykpicks.android.store.KeystoreSecureStore
import com.flykpicks.android.store.LibraryStore
import com.flykpicks.android.store.PerUserState
import com.flykpicks.android.store.RecommendationsStore
import com.flykpicks.android.store.SearchStore
import com.flykpicks.android.store.SecureStore
import com.flykpicks.android.store.SharedPreferencesFeatureFlagCache
import com.flykpicks.android.util.AndroidDeviceIdProvider
import com.flykpicks.android.util.DeviceIdProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** Lives as long as the process, on the main thread; for app-wide timers (toasts, telemetry flushes). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @Singleton
    fun provideSecureStore(@ApplicationContext context: Context): SecureStore = KeystoreSecureStore(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {
    @Binds
    abstract fun bindDeviceIdProvider(impl: AndroidDeviceIdProvider): DeviceIdProvider

    @Binds
    abstract fun bindFeatureFlagCache(impl: SharedPreferencesFeatureFlagCache): FeatureFlagCache

    // Every store holding the signed-in account's data goes in this set, so a change of session
    // resets it (see PerUserState).
    @Binds
    @IntoSet
    abstract fun bindLibraryState(store: LibraryStore): PerUserState

    @Binds
    @IntoSet
    abstract fun bindRecommendationsState(store: RecommendationsStore): PerUserState

    @Binds
    @IntoSet
    abstract fun bindSearchState(store: SearchStore): PerUserState
}
