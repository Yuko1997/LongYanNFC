package com.longyannfc.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.longyannfc.app.R
import com.longyannfc.app.ui.components.AppBottomBar
import com.longyannfc.app.ui.components.AppTab
import com.longyannfc.app.ui.screens.*
import java.net.URLEncoder

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Card : Screen("card")
    object Editor : Screen("editor")
    object Settings : Screen("settings")
    object History : Screen("history")
    object Supercard : Screen("supercard")
    object Dfu : Screen("dfu")
    object Detection : Screen("detection")
    object ReadCard : Screen("readcard")
    object WriteCard : Screen("writecard")
    object RwCard : Screen("rwcard")
    object VCard : Screen("vcard")
    object VCardSimple : Screen("vcardsimple")
    object Keys : Screen("keys")
    object Help : Screen("help")
    object Log : Screen("log")
    object Function : Screen("function")
    object Diff : Screen("diff")
}

@Composable
fun LongYanNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val tabs = appTabs()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = tabs.any { it.route == currentRoute }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // 统一在此消费系统栏 inset：顶部由各页面的 AppTopBar 处理，
        // 底部与横向由这里处理，页面内部不再重复消费。
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
        ),
        bottomBar = {
            if (isTopLevel) {
                AppBottomBar(
                    tabs = tabs,
                    currentRoute = currentRoute,
                    onSelect = { tab ->
                        if (tab.route != currentRoute) {
                            navController.navigate(tab.route) {
                                // 切换顶级目的地时保留各自的状态与滚动位置
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Main.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            composable(Screen.Main.route) {
                MainScreen(
                    onNavigateToCard = { navController.navigate(Screen.Card.route) },
                    onNavigateToCardDump = { id -> navController.navigate("card/$id") },
                    onNavigateToLiveCard = { info ->
                        LiveCardHolder.set(info)
                        navController.navigate("card/live/${URLEncoder.encode(info.uidHex, "UTF-8")}")
                    },
                    onNavigateToEditor = { navController.navigate(Screen.Editor.route) },
                    onNavigateToEditorDump = { id -> navController.navigate("editor/$id") },
                    onNavigateToWriteCard = { navController.navigate(Screen.WriteCard.route) }
                )
            }

            composable(Screen.Card.route) {
                CardScreen(dumpId = 0L, onBack = { navController.popBackStack() })
            }
            composable(
                route = "card/{dumpId}",
                arguments = listOf(navArgument("dumpId") { type = NavType.LongType })
            ) { backStackEntry ->
                CardScreen(
                    dumpId = backStackEntry.arguments?.getLong("dumpId") ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "card/live/{uid}",
                arguments = listOf(navArgument("uid") { type = NavType.StringType })
            ) {
                val liveInfo = remember { LiveCardHolder.info }
                DisposableEffect(Unit) {
                    onDispose { LiveCardHolder.info = null }
                }
                CardScreen(
                    dumpId = 0L,
                    liveInfo = liveInfo,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Editor.route) {
                EditorScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "editor/{dumpId}",
                arguments = listOf(navArgument("dumpId") { type = NavType.LongType })
            ) { backStackEntry ->
                EditorScreen(
                    dumpId = backStackEntry.arguments?.getLong("dumpId") ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.History.route) {
                CardHistoryScreen(
                    onOpen = { id -> navController.navigate("editor/$id") }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToKeys = { navController.navigate(Screen.Keys.route) },
                    onNavigateToLog = { navController.navigate(Screen.Log.route) },
                    onNavigateToHelp = { navController.navigate(Screen.Help.route) },
                    onNavigateToSupercard = { navController.navigate(Screen.Supercard.route) },
                    onNavigateToDfu = { navController.navigate(Screen.Dfu.route) },
                    onNavigateToReadWrite = { navController.navigate(Screen.RwCard.route) },
                    onNavigateToFunctions = { navController.navigate(Screen.Function.route) }
                )
            }

            composable(Screen.Supercard.route) {
                SupercardScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Dfu.route) {
                DfuScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Detection.route) {
                DetectionScreen()
            }
            composable(Screen.ReadCard.route) {
                ReadCardScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.WriteCard.route) {
                WriteCardScreen(
                    onNavigateToEditor = { id -> navController.navigate("editor/$id") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.RwCard.route) {
                RwCardScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.VCard.route) {
                VCardScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.VCardSimple.route) {
                VCardSimpleScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Keys.route) {
                KeysScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Help.route) {
                HelpScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Log.route) {
                LogScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Function.route) {
                FunctionScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Diff.route) {
                DiffScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** 顶级目的地：读卡器、侦测、历史、设置 */
@Composable
private fun appTabs(): List<AppTab> = listOf(
    AppTab(Screen.Main.route, stringResource(R.string.tab_device), Icons.Default.Bluetooth),
    AppTab(Screen.Detection.route, stringResource(R.string.tab_detect), Icons.Default.Search),
    AppTab(Screen.History.route, stringResource(R.string.tab_history), Icons.Default.FolderOpen),
    AppTab(Screen.Settings.route, stringResource(R.string.tab_settings), Icons.Default.Settings)
)

