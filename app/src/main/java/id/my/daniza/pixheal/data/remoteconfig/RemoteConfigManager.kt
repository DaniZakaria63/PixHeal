package id.my.daniza.pixheal.data.remoteconfig

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import id.my.daniza.modelpull.AotganConstants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigManager @Inject constructor() {

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(
            mapOf(AotganConstants.REMOTE_CONFIG_KEY to AotganConstants.DEFAULT_MODEL_URL)
        )
        remoteConfig.fetchAndActivate()
    }

    fun getAotganModelUrl(): String {
        return remoteConfig.getString(AotganConstants.REMOTE_CONFIG_KEY)
            .ifBlank { AotganConstants.DEFAULT_MODEL_URL }
    }
}
