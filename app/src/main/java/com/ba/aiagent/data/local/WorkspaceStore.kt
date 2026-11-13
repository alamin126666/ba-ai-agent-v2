package com.ba.aiagent.data.local

import android.content.Context
import java.io.File

/** All agent-created files live ONLY under the workspace. `..` is always rejected. */
class WorkspaceStore(context: Context) {
    val root: File = File(context.filesDir, "ba_ai_agent_workspace").apply { mkdirs() }
    val skillsDir: File = File(root, "skills").apply { mkdirs() }

    fun isSafe(path: String): Boolean {
        if (path.contains("..")) return false
        if (path.startsWith("/")) return false
        return true
    }

    fun resolve(path: String): File? {
        if (!isSafe(path)) return null
        val f = File(root, path)
        return try {
            if (!f.canonicalPath.startsWith(root.canonicalPath)) null else f
        } catch (_: Exception) { null }
    }

    fun listFiles(relative: String = ""): List<File> {
        val dir = if (relative.isBlank()) root else resolve(relative) ?: return emptyList()
        return dir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
    }

    fun read(path: String): String? {
        val f = resolve(path) ?: return null
        return if (f.exists() && f.isFile) runCatching { f.readText() }.getOrNull() else null
    }

    fun write(path: String, content: String): Boolean {
        val f = resolve(path) ?: return false
        return runCatching {
            f.parentFile?.mkdirs()
            f.writeText(content)
            true
        }.getOrDefault(false)
    }

    fun delete(path: String): Boolean {
        val f = resolve(path) ?: return false
        return runCatching { f.delete() }.getOrDefault(false)
    }

    fun search(query: String): List<File> {
        val q = query.lowercase()
        return root.walkTopDown().filter { it.isFile }.filter { f ->
            f.name.lowercase().contains(q) ||
                (f.length() < 200_000 && runCatching { f.readText().lowercase().contains(q) }.getOrDefault(false))
        }.toList()
    }
}
