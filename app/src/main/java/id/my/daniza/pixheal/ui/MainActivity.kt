package id.my.daniza.pixheal.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import id.my.daniza.pixheal.ui.navigation.PixHealNavHost
import id.my.daniza.pixheal.ui.theme.PixHealAppTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PixHealAppTheme {
                PixHealNavHost()
            }
        }
    }
}
