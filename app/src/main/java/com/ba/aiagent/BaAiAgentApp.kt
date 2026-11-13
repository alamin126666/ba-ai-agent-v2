package com.ba.aiagent

import android.app.Application
import com.ba.aiagent.data.local.AppDatabase
import com.ba.aiagent.data.local.SecureKeyStore
import com.ba.aiagent.data.local.SettingsStore
import com.ba.aiagent.data.local.SkillStore
import com.ba.aiagent.data.local.DesignStore
import com.ba.aiagent.data.local.WorkspaceStore
import com.ba.aiagent.domain.agent.BaTerminal

class BaAiAgentApp : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var keyStore: SecureKeyStore
        private set
    lateinit var workspaceStore: WorkspaceStore
        private set
    lateinit var skillStore: SkillStore
        private set
    lateinit var designStore: DesignStore
        private set
    lateinit var terminal: BaTerminal
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.get(this)
        settingsStore = SettingsStore(this)
        keyStore = SecureKeyStore(this)
        workspaceStore = WorkspaceStore(this)
        skillStore = SkillStore(this, workspaceStore)
        designStore = DesignStore(this)
        terminal = BaTerminal(workspaceStore)
    }
}
