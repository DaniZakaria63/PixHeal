package id.my.daniza.pixheal.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.my.daniza.pixheal.ui.screens.edit.EditScreen
import id.my.daniza.pixheal.ui.screens.home.HomeScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Edit : Screen("edit/{imageUri}") {
        fun createRoute(uri: Uri): String = "edit/${Uri.encode(uri.toString())}"
    }
    data object ObjectRemoval : Screen("object_removal")
    data object BasicEditing : Screen("basic_editing")
    data object BackgroundRemoval : Screen("background_removal")
    data object Export : Screen("export")
}

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val screen: Screen,
)

val bottomNavItems = listOf(
    BottomNavItem("Enhancer", Icons.Filled.AutoFixHigh, Screen.Home),
    BottomNavItem("Obj Removal", Icons.Filled.BackHand, Screen.ObjectRemoval),
    BottomNavItem("Basic Edit", Icons.Filled.Crop, Screen.BasicEditing),
    BottomNavItem("BG Removal", Icons.Filled.LayersClear, Screen.BackgroundRemoval),
    BottomNavItem("Export", Icons.Filled.FileDownload, Screen.Export),
)

private val bottomNavRoutes = setOf(
    Screen.Home.route,
    Screen.ObjectRemoval.route,
    Screen.BasicEditing.route,
    Screen.BackgroundRemoval.route,
    Screen.Export.route,
)

@Composable
fun PixHealNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    Scaffold(
        bottomBar = {
            if (navBackStackEntry?.destination?.route in bottomNavRoutes) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = navBackStackEntry?.destination?.route == item.screen.route,
                            onClick = {
                                if (item.screen.route != navBackStackEntry?.destination?.route) {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(Screen.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToEdit = { uri ->
                        navController.navigate(Screen.Edit.createRoute(uri))
                    }
                )
            }

            composable(
                route = Screen.Edit.route,
                arguments = listOf(navArgument("imageUri") { type = NavType.StringType }),
            ) { backStackEntry ->
                val uriString = backStackEntry.arguments?.getString("imageUri") ?: return@composable
                val uri = Uri.parse(uriString)
                EditScreen(
                    imageUri = uri,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Screen.ObjectRemoval.route) {
                PlaceholderScreen("Object Removal")
            }
            composable(Screen.BasicEditing.route) {
                PlaceholderScreen("Basic Editing")
            }
            composable(Screen.BackgroundRemoval.route) {
                PlaceholderScreen("Background Removal")
            }
            composable(Screen.Export.route) {
                PlaceholderScreen("Export")
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$title — Coming Soon",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
