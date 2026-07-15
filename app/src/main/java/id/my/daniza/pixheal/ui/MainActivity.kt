package id.my.daniza.pixheal.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import id.my.daniza.pixheal.ui.navigation.PixHealNavHost
import id.my.daniza.pixheal.ui.screens.unsupported.UnsupportedArchScreen
import id.my.daniza.pixheal.ui.theme.PixHealAppTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PixHealAppTheme {
                if (isArm64()) {
                    PixHealNavHost()
                } else {
                    UnsupportedArchScreen()
                }
            }
        }
    }

    private fun isArm64(): Boolean {
        return Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
    }
}
