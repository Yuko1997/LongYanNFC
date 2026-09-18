package com.longyannfc.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.net.URLEncoder
import com.longyannfc.app.ui.screens.*

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Amiibo : Screen("amiibo")
    object Card : Screen("card")
    object Editor : Screen("editor")
    object Settings : Screen("settings")
    object Login : Screen("login")
    object Supercard : Screen("supercard")
    object Dfu : Screen("dfu")
    object Detection : Screen("detection")
    object ReadCard : Screen("readcard")
    object WriteCard : Screen("writecard")
    object RwCard : Screen("rwcard")
    object VCard : Screen("vcard")
    object VCardSimple : Screen("vcardsimple")
    object Keys : Screen("keys")
    object User : Screen("user")
    object Help : Screen("help")
    object Log : Screen("log")
    object Function : Screen("function")
    object Diff : Screen("diff")
    object History : Screen("history")
}

@Composable
fun LongYanNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route,
        modifier = modifier
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
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onNavigateToDetection = { navController.navigate(Screen.Detection.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Amiibo.route) { AmiiboScreen() }
        composable(Screen.Card.route) { CardScreen() }
        composable(
            route = "card/{dumpId}",
            arguments = listOf(navArgument("dumpId") { type = NavType.LongType })
        ) { backStackEntry ->
            CardScreen(dumpId = backStackEntry.arguments?.getLong("dumpId") ?: 0L)
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
                liveInfo = liveInfo
            )
        }
        composable(Screen.Editor.route) { EditorScreen() }
        composable(
            route = "editor/{dumpId}",
            arguments = listOf(navArgument("dumpId") { type = NavType.LongType })
        ) { backStackEntry ->
            EditorScreen(dumpId = backStackEntry.arguments?.getLong("dumpId") ?: 0L)
        }
        composable(Screen.History.route) {
            CardHistoryScreen(
                onOpen = { id -> navController.navigate("editor/$id") }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToKeys = { navController.navigate(Screen.Keys.route) }
            )
        }
        composable(Screen.Login.route) { LoginScreen() }
        composable(Screen.Supercard.route) { SupercardScreen() }
        composable(Screen.Dfu.route) { DfuScreen() }
        composable(Screen.Detection.route) { DetectionScreen() }
        composable(Screen.ReadCard.route) { ReadCardScreen() }
        composable(Screen.WriteCard.route) { WriteCardScreen() }
        composable(Screen.RwCard.route) { RwCardScreen() }
        composable(Screen.VCard.route) { VCardScreen() }
        composable(Screen.VCardSimple.route) { VCardSimpleScreen() }
        composable(Screen.Keys.route) { KeysScreen() }
        composable(Screen.User.route) { UserScreen() }
        composable(Screen.Help.route) { HelpScreen() }
        composable(Screen.Log.route) { LogScreen() }
        composable(Screen.Function.route) { FunctionScreen() }
        composable(Screen.Diff.route) { DiffScreen() }
    }
}
