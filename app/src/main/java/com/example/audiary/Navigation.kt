package com.example.audiary

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.audiary.explore.*
import com.example.audiary.library.*
import com.example.audiary.diary.*
import com.example.audiary.song.*
import com.example.audiary.spotify.*

@Composable fun AudiaryNav(app: AudiaryApp) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val tabs = listOf("explore", "library", "diary")
    val icons = listOf(Icons.Outlined.Explore, Icons.Outlined.LibraryMusic, Icons.Outlined.AutoStories)
    val current = entry?.destination?.route
    val favorites: FavoritesViewModel = viewModel(factory = viewModelFactory { initializer { FavoritesViewModel(app.favorites) } })
    val error by favorites.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); favorites.clearError() } }
    fun song(id: String, memory: String = "") {
        val path = if (memory.isNotEmpty()) "song/${Uri.encode(id)}?memory=${Uri.encode(memory)}" else "song/${Uri.encode(id)}"
        nav.navigate(path)
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (current in tabs) NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, route ->
                    NavigationBarItem(selected = current == route,
                        onClick = { nav.navigate(route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        } },
                        icon = { Icon(icons[i], null, Modifier.size(22.dp)) },
                        label = { Text(route.replaceFirstChar { it.uppercase() }) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surface,
                            selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary))
                }
            }
        }) { padding ->
        NavHost(nav, startDestination = "explore", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable("explore") {
                val vm: ExploreViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        ExploreViewModel(
                            app.music,
                            app.spotifyApi,
                            app.spotifyAuth,
                            app.database,
                            app.discoverySourceManager
                        )
                    }
                })
                ExploreScreen(vm, onSongClick = { song(it) })
            }
            composable("library") {
                val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(app.music) } })
                val account: AccountViewModel = viewModel(factory = viewModelFactory { initializer { AccountViewModel(app.spotifyAuth) } })
                val spotify: SpotifyLibraryViewModel = viewModel(factory = viewModelFactory { initializer { SpotifyLibraryViewModel(app.spotifyApi, app.spotifyAuth, app.database) } })
                LibraryScreen(
                    vm = vm,
                    favoritesVm = favorites,
                    account = account,
                    spotifyVm = spotify,
                    onSong = { song(it) },
                    onExploreSource = { source ->
                        app.discoverySourceManager.setSource(source)
                        nav.navigate("explore") {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("diary") {
                val vm: DiaryViewModel = viewModel(factory = viewModelFactory { initializer { DiaryViewModel(app.diary) } })
                val editor: NoteEditorViewModel = viewModel(factory = viewModelFactory { initializer { NoteEditorViewModel(app.diary, createSavedStateHandle()) } })
                DiaryScreen(vm, editor, onMemory = { id, note -> song(id, note) })
            }
            composable("song/{songId}?memory={memory}", arguments = listOf(
                navArgument("songId") { type = NavType.StringType },
                navArgument("memory") { type = NavType.StringType; defaultValue = "" }
            )) { backStack ->
                val id = backStack.arguments?.getString("songId").orEmpty()
                val vm: SongViewModel = viewModel(factory = viewModelFactory { initializer { SongViewModel(id, app.music, app.diary) } })
                val editor: NoteEditorViewModel = viewModel(factory = viewModelFactory { initializer { NoteEditorViewModel(app.diary, createSavedStateHandle()) } })
                SongScreen(vm, editor, favorites, backStack.arguments?.getString("memory")?.takeIf { it.isNotEmpty() },
                    onBack = { nav.popBackStack() })
            }
        }
    }
}
