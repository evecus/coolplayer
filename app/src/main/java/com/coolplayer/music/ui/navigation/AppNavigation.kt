package com.coolplayer.music.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.coolplayer.music.ui.home.HomeScreen
import com.coolplayer.music.ui.library.GroupDetailScreen
import com.coolplayer.music.ui.library.LibraryViewModel
import com.coolplayer.music.ui.player.PlayerScreen
import com.coolplayer.music.ui.playlist.PlaylistDetailScreen
import com.coolplayer.music.ui.playlist.PlaylistScreen
import com.coolplayer.music.ui.settings.SettingsScreen

/**
 * 路由常量。
 */
object AppRoutes {
    const val HOME = "home"
    const val PLAYER = "player"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST_DETAIL = "playlist/{id}"
    const val SETTINGS = "settings"
    const val GROUP_DETAIL = "group/{category}/{key}"
}

object AppNavigator {
    fun toPlayer(navController: NavHostController) {
        navController.navigate(AppRoutes.PLAYER)
    }

    fun toPlaylists(navController: NavHostController) {
        navController.navigate(AppRoutes.PLAYLISTS)
    }

    fun toPlaylistDetail(navController: NavHostController, id: Long) {
        navController.navigate("playlist/$id")
    }

    fun toSettings(navController: NavHostController) {
        navController.navigate(AppRoutes.SETTINGS)
    }

    fun toGroupDetail(navController: NavHostController, category: Int, key: String) {
        val encoded = java.net.URLEncoder.encode(key, "UTF-8")
        navController.navigate("group/$category/$encoded")
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = AppRoutes.HOME) {

        composable(AppRoutes.HOME) {
            HomeScreen(navController = navController)
        }

        composable(AppRoutes.PLAYER) {
            PlayerScreen(
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.PLAYLISTS) {
            PlaylistScreen(
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoutes.PLAYLIST_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            PlaylistDetailScreen(
                playlistId = id,
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoutes.GROUP_DETAIL,
            arguments = listOf(
                navArgument("category") { type = NavType.IntType },
                navArgument("key") { type = NavType.StringType }
            )
        ) { entry ->
            val category = entry.arguments?.getInt("category") ?: 0
            val rawKey = entry.arguments?.getString("key") ?: ""
            val key = java.net.URLDecoder.decode(rawKey, "UTF-8")
            GroupDetailScreen(
                category = category,
                groupKey = key,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
