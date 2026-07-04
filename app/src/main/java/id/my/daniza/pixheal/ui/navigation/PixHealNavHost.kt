package id.my.daniza.pixheal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.my.daniza.pixheal.ui.screens.edit.EditScreen
import id.my.daniza.pixheal.ui.screens.home.HomeScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Edit : Screen("edit/{projectId}") {
        fun createRoute(projectId: Long): String = "edit/$projectId"
    }
}

@Composable
fun PixHealNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToEdit = { projectId ->
                    navController.navigate(Screen.Edit.createRoute(projectId))
                }
            )
        }

        composable(
            route = Screen.Edit.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: return@composable
            EditScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
