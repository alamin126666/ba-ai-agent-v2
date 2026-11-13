package com.ba.aiagent.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ba.aiagent.BaAiAgentApp
import com.ba.aiagent.ui.chat.ChatScreen
import com.ba.aiagent.ui.design.DesignScreen
import com.ba.aiagent.ui.files.FileViewerScreen
import com.ba.aiagent.ui.files.FilesScreen
import com.ba.aiagent.ui.home.HomeScreen
import com.ba.aiagent.ui.models.ModelPickerScreen
import com.ba.aiagent.ui.settings.SettingsScreen
import com.ba.aiagent.ui.skills.SkillsScreen
import com.ba.aiagent.ui.terminal.TerminalScreen

object Routes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val MODELS = "models"
    const val FILES = "files"
    const val FILE_VIEWER = "file_viewer"
    const val TERMINAL = "terminal"
    const val SKILLS = "skills"
    const val DESIGN = "design"
}

@Composable
fun NavGraph(app: BaAiAgentApp) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.HOME
    val tabs = listOf(
        Triple(Routes.HOME, "Chats", Icons.Default.Chat),
        Triple(Routes.FILES, "Files", Icons.Default.Folder),
        Triple(Routes.TERMINAL, "Terminal", Icons.Default.Code),
        Triple(Routes.SKILLS, "Skills", Icons.Default.Build),
        Triple(Routes.SETTINGS, "Settings", Icons.Default.Settings)
    )
    val showBottom = tabs.any { route == it.first }
    Scaffold(
        bottomBar = {
            if (showBottom) {
                NavigationBar {
                    tabs.forEach { (r, label, icon) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = { if (route != r) nav.navigate(r) { popUpTo(Routes.HOME) } },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) { HomeScreen(app, onOpenChat = { id -> nav.navigate("${Routes.CHAT}/$id") }) }
            composable(
                "${Routes.CHAT}/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { back ->
                val id = back.arguments?.getLong("sessionId") ?: 0L
                ChatScreen(app, sessionId = id, onBack = { nav.popBackStack() }, onOpenDesign = { nav.navigate(Routes.DESIGN) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(app, onOpenModels = { nav.navigate(Routes.MODELS) }, onOpenDesign = { nav.navigate(Routes.DESIGN) })
            }
            composable(Routes.MODELS) { ModelPickerScreen(app, onBack = { nav.popBackStack() }) }
            composable(Routes.FILES) { FilesScreen(app, onOpenFile = { path -> nav.navigate("${Routes.FILE_VIEWER}?path=$path") }) }
            composable(
                "${Routes.FILE_VIEWER}?path={path}",
                arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })
            ) { back ->
                val path = back.arguments?.getString("path") ?: ""
                FileViewerScreen(app, path = path, onBack = { nav.popBackStack() })
            }
            composable(Routes.TERMINAL) { TerminalScreen(app) }
            composable(Routes.SKILLS) { SkillsScreen(app) }
            composable(Routes.DESIGN) { DesignScreen(app, onBack = { nav.popBackStack() }) }
        }
    }
}
