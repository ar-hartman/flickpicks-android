package com.flykpicks.android

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.stripe.android.PaymentConfiguration
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FlykPicksApp : Application(), SingletonImageLoader.Factory {

    /** Built on first image load, not at startup. */
    @Inject lateinit var imageLoader: Lazy<ImageLoader>

    override fun onCreate() {
        super.onCreate()
        if (AppConfig.stripePublishableKey.isNotBlank()) {
            PaymentConfiguration.init(this, AppConfig.stripePublishableKey)
        } else {
            Log.w(TAG, "flyk.stripePublishableKey is not set; payment flows are unavailable.")
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

    private companion object {
        const val TAG = "FlykPicksApp"
    }
}
