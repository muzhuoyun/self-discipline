package com.selfdiscipline.app.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.selfdiscipline.app.data.Category
import java.time.LocalDate

private const val ROUTE_HOME = "home"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_ACHIEVEMENTS = "achievements"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_REPORT_HISTORY = "report_history"
private const val ROUTE_DETAIL = "detail/{date}/{category}"

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val onMainScreen = currentRoute != ROUTE_DETAIL && currentRoute != ROUTE_SETTINGS &&
        currentRoute != ROUTE_REPORT_HISTORY

    Scaffold(
        bottomBar = {
            if (onMainScreen) {
                NavigationBar {
                    NavItem(ROUTE_HOME, "今日", Icons.Filled.Star, currentRoute) {
                        navController.navigate(ROUTE_HOME) { standardNav(navController, this) }
                    }
                    NavItem(ROUTE_HISTORY, "历史", Icons.Filled.ShowChart, currentRoute) {
                        navController.navigate(ROUTE_HISTORY) { standardNav(navController, this) }
                    }
                    NavItem(ROUTE_ACHIEVEMENTS, "成就", Icons.Filled.EmojiEvents, currentRoute) {
                        navController.navigate(ROUTE_ACHIEVEMENTS) { standardNav(navController, this) }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    vm = vm,
                    onCategoryClick = { category ->
                        navController.navigate("detail/${LocalDate.now()}/${category.key}")
                    },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                )
            }
            composable(ROUTE_HISTORY) {
                HistoryScreen(
                    vm = vm,
                    onEditCategory = { date, category ->
                        navController.navigate("detail/$date/${category.key}")
                    },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onOpenReportHistory = { navController.navigate(ROUTE_REPORT_HISTORY) },
                )
            }
            composable(ROUTE_REPORT_HISTORY) {
                ReportHistoryScreen(vm = vm, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_ACHIEVEMENTS) {
                AchievementsScreen(vm = vm, onOpenSettings = { navController.navigate(ROUTE_SETTINGS) })
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(vm = vm, onBack = { navController.popBackStack() })
            }
            composable(ROUTE_DETAIL) { entry ->
                val date = entry.arguments?.getString("date")
                val categoryKey = entry.arguments?.getString("category")
                if (date != null && categoryKey != null) {
                    CategoryDetailScreen(
                        vm = vm,
                        date = LocalDate.parse(date),
                        category = Category.fromKey(categoryKey),
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

/** 底部导航项：选中时保持单一实例，返回时恢复各自的状态 */
@Composable
private fun RowScope.NavItem(
    route: String,
    label: String,
    icon: ImageVector,
    currentRoute: String?,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = currentRoute == route,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}

/** 底部导航：回到首页栈底、单实例、恢复各自状态 */
private fun standardNav(navController: NavController, builder: androidx.navigation.NavOptionsBuilder) {
    builder.popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    builder.launchSingleTop = true
    builder.restoreState = true
}
