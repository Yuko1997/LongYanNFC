package com.longyannfc.app

import android.app.Application
import android.content.Context
import com.longyannfc.app.utils.LocaleHelper
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class LongYanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (com.longyannfc.app.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }
}
