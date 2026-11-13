package com.ba.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ba.aiagent.ui.navigation.NavGraph
import com.ba.aiagent.ui.theme.BaAiAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as BaAiAgentApp
        setContent {
            val themeMode by app.settingsStore.themeMode.collectAsState(initial = "dark")
            val accent by app.settingsStore.accent.collectAsState(initial = "orange")
            BaAiAgentTheme(darkTheme = themeMode != "light", accent = accent) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph(app = app)
                }
            }
        }
    }
}
