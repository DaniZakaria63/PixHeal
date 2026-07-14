package id.my.daniza.pixheal

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import id.my.daniza.modelpull.remoteconfig.RemoteConfigManager
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PixHealApplication : Application() {

    @Inject
    lateinit var remoteConfigManager: RemoteConfigManager

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        remoteConfigManager
    }
}
