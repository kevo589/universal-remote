package dev.kmedrano.remote.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.kmedrano.remote.app.di.AppContainer

private object Routes {
    const val HOME = "home"
    const val ADD_DEVICE = "addDevice"
    const val PAIRING = "pairing/{deviceId}"

    fun pairing(deviceId: String) = "pairing/$deviceId"
}

@Composable
fun AppNavGraph(container: AppContainer) {
    val navController = rememberNavController()
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container) }
        },
    )

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onAddDeviceClick = { navController.navigate(Routes.ADD_DEVICE) },
            )
        }
        composable(Routes.ADD_DEVICE) {
            AddDeviceScreen(
                onBack = { navController.popBackStack() },
                onDeviceChosen = { protocol, displayName, host ->
                    val deviceId = viewModel.addDevice(protocol, displayName, host)
                    navController.navigate(Routes.pairing(deviceId)) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }
        composable(
            route = Routes.PAIRING,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId").orEmpty()
            PairingScreen(
                deviceId = deviceId,
                viewModel = viewModel,
                onDone = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
    }
}
