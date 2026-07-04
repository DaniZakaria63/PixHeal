package id.my.daniza.pixheal.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import id.my.daniza.pixheal.ui.screens.home.HomeScreen
import id.my.daniza.pixheal.ui.theme.PixHealAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PixHealAppTheme {
                HomeScreen()
            }
        }
    }
}
