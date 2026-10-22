package com.myflower.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.myflower.app.data.DataRepository
import com.myflower.app.data.local.TokenManager
import com.myflower.app.ui.device.DeviceDetailScreen
import com.myflower.app.ui.device.DeviceListScreen
import com.myflower.app.ui.login.LoginScreen
import com.myflower.app.ui.register.RegisterScreen

@Composable
fun MainNavigation() {
    val repository = remember { DataRepository() }
    val startDestination = if (TokenManager.isLoggedIn()) DeviceList else Login
    val backStack = rememberNavBackStack(startDestination)

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Login> {
                LoginScreen(
                    repository = repository,
                    onLoginSuccess = {
                        backStack.clear()
                        backStack.add(DeviceList)
                    },
                    onNavigateToRegister = { backStack.add(Register) }
                )
            }
            entry<Register> {
                RegisterScreen(
                    repository = repository,
                    onRegisterSuccess = {
                        backStack.clear()
                        backStack.add(DeviceList)
                    },
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }
            entry<DeviceList> {
                DeviceListScreen(
                    repository = repository,
                    onDeviceClick = { device -> backStack.add(DeviceDetail(device)) },
                    onLogout = {
                        repository.logout()
                        backStack.clear()
                        backStack.add(Login)
                    }
                )
            }
            entry<DeviceDetail> { navKey ->
                DeviceDetailScreen(
                    device = navKey.toDevice(),
                    repository = repository,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
