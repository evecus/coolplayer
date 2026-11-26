package com.coolplayer.music.ui.tv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.coolplayer.music.player.PlayerConnection
import com.coolplayer.music.ui.adaptive.LocalAdaptive
import com.coolplayer.music.ui.audiofx.AudioFxScreen
import com.coolplayer.music.ui.component.TvAccent
import com.coolplayer.music.ui.component.TvAccentSoft
import com.coolplayer.music.ui.component.TvTextDark
import com.coolplayer.music.ui.component.lxFocusable
import com.coolplayer.music.ui.library.LibraryViewModel
import com.coolplayer.music.ui.library.MusicCategory
import com.coolplayer.music.ui.library.ScanSettingsScreen
import com.coolplayer.music.ui.library.TvGroupDetailScreen
import com.coolplayer.music.ui.library.TvLibraryScreen
import com.coolplayer.music.ui.player.TvPlayerScreen
import com.coolplayer.music.ui.playlist.TvPlaylistScreen
import com.coolplayer.music.ui.search.TvSearchScreen
import kotlinx.coroutines.delay

private const val ROUTE_HOME = "tv_home"
private const val ROUTE_PLAYLISTS = "tv_playlists"
private const val ROUTE_SEARCH = "tv_search"
private const val ROUTE_SCAN = "tv_scan"
private const val ROUTE_AUDIOFX = "tv_audiofx"
private const val ROUTE_PLAYER = "tv_player"
private const val ROUTE_GROUP = "tv_group/{category}/{key}"

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem(ROUTE_HOME, "音乐库", Icons.Default.LibraryMusic),
    NavItem(ROUTE_PLAYLISTS, "歌单", Icons.AutoMirrored.Filled.QueueMusic),
    NavItem(ROUTE_SEARCH, "搜索", Icons.Default.Search),
    NavItem(ROUTE_SCAN, "扫描", Icons.Default.LibraryMusic),
)

/** chromeless 路由：不显示左侧导航栏（全屏播放页、分组详情） */
private val chromelessRoutes = setOf(ROUTE_PLAYER)
private fun isGroupRoute(route: String?) = route?.startsWith("tv_group/") == true

/**
 * TV 主框架：左侧导航栏 + 内容 NavHost + 双击返回退出。
 * 设备形态与字号由 [com.coolplayer.music.ui.adaptive.ProvideAdaptive] 在 MainActivity 注入。
 */
@Composable
fun TvMainScreen() {
    val context = LocalContext.current
    val activity = context.findActivity()
    val navController = rememberNavController()
    val vm: LibraryViewModel = viewModel(viewModelStoreOwner = activity as ComponentActivity)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(Unit) {
        PlayerConnection.bind(context)
        vm.init()
    }

    // ── 双击返回退出（抄 FMusic）──
    // BackHandler 注册在最外层：非根页面先由 NavHost 回退；只有停在根页面
    // 才走"第一次提示、2 秒内再按停止播放并退出"的逻辑。
    var exitHint by remember { mutableStateOf(false) }
    var lastBackAt by remember { mutableStateOf(0L) }
    LaunchedEffect(exitHint) {
        if (exitHint) {
            delay(2000L)
            exitHint = false
        }
    }
    BackHandler {
        if (currentRoute != null && currentRoute != ROUTE_HOME) {
            navController.popBackStack()
            return@BackHandler
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (exitHint && now - lastBackAt <= 2000L) {
            // 停止播放并关闭前台服务（不再后台放歌），再结束整个应用。
            PlayerConnection.stopPlayback(context)
            activity?.finishAffinity()
        } else {
            lastBackAt = now
            exitHint = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val showNav = currentRoute !in chromelessRoutes && !isGroupRoute(currentRoute)

        Row(modifier = Modifier.fillMaxSize()) {
            // ── 左侧导航栏 ──
            if (showNav) {
                LeftNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(ROUTE_HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenPlayer = { navController.navigate(ROUTE_PLAYER) },
                )
            }

            // ── 内容区 ──
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                NavHost(navController = navController, startDestination = ROUTE_HOME) {
                    composable(ROUTE_HOME) {
                        TvLibraryScreen(
                            vm = vm,
                            onOpenGroup = { category, key ->
                                navController.navigate("tv_group/$category/$key")
                            },
                        )
                    }
                    composable(ROUTE_PLAYLISTS) { TvPlaylistScreen(allSongs = vm.allSongs.collectAsState().value) }
                    composable(ROUTE_SEARCH) { TvSearchScreen(allSongs = vm.allSongs.collectAsState().value) }
                    composable(ROUTE_SCAN) { ScanSettingsScreen(vm = vm, onBack = { navController.popBackStack() }) }
                    composable(ROUTE_AUDIOFX) {
                        val player by PlayerConnection.player.collectAsState()
                        player?.let {
                            AudioFxScreen(eqProcessor = it.eqProcessor, onBack = { navController.popBackStack() })
                        }
                    }
                    composable(ROUTE_PLAYER) {
                        TvPlayerScreen(
                            onBack = { navController.popBackStack() },
                            onOpenAudioFx = { navController.navigate(ROUTE_AUDIOFX) },
                        )
                    }
                    composable(
                        route = ROUTE_GROUP,
                        arguments = listOf(
                            navArgument("category") { type = NavType.IntType },
                            navArgument("key") { type = NavType.StringType },
                        ),
                    ) { entry ->
                        val category = entry.arguments?.getInt("category") ?: 0
                        val key = entry.arguments?.getString("key") ?: ""
                        TvGroupDetailScreen(
                            vm = vm,
                            category = category,
                            groupKey = key,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }

        // ── 双击退出提示（抄 FMusic 底部浮层，不抢焦点、不拦截按键）──
        if (exitHint) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 72.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    color = Color(0xFFE8EAF6),
                    contentColor = TvTextDark,
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                ) {
                    Text(
                        "再按一次退出APP",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TvTextDark,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LeftNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val player = PlayerConnection.player.collectAsState().value
    val hasContent = player?.hasContent?.collectAsState()?.value ?: false
    val title = player?.title?.collectAsState()?.value ?: ""
    val coverBytes = player?.coverBytes?.collectAsState()?.value
    val coverBitmap = remember(coverBytes) {
        coverBytes?.let {
            runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
        }
    }

    Column(
        modifier = Modifier
            .width(180.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        // 品牌
        Text(
            "Cool Player",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        )

        // 顶部播放入口
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .lxFocusable(shape = CircleShape)
                .clickable(onClick = onOpenPlayer)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            if (coverBitmap != null) {
                // 有封面：把方形封面裁剪铺满圆形入口
                Image(
                    bitmap = coverBitmap.asImageBitmap(),
                    contentDescription = "播放页",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 无封面：显示音符图标
                Icon(Icons.Default.MusicNote, contentDescription = "播放页", tint = TvTextDark, modifier = Modifier.size(30.dp))
            }
        }
        if (hasContent) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }

        Spacer(Modifier.size(16.dp))

        navItems.forEach { item ->
            NavItemRow(
                item = item,
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
            )
        }

        Spacer(Modifier.weight(1f))

        // EQ 快捷入口
        NavItemRow(
            item = NavItem(ROUTE_AUDIOFX, "音效 EQ", Icons.Default.GraphicEq),
            selected = currentRoute == ROUTE_AUDIOFX,
            onClick = { onNavigate(ROUTE_AUDIOFX) },
        )
    }
}

@Composable
private fun NavItemRow(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) TvAccentSoft else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .lxFocusable(shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            tint = if (selected) TvAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            item.label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            // 当前项：浅色高亮背景 + 固定深蓝紫文字，杜绝白字压浅底看不清。
            color = if (selected) TvAccent else MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
    return ctx as? Activity
}
