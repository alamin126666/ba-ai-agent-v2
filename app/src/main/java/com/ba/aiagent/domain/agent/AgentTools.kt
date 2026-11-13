package com.ba.aiagent.domain.agent

import android.content.Context
import android.content.Intent
import com.ba.aiagent.data.local.WorkspaceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ToolResult(val ok: Boolean, val output: String)

object ToolCallParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Extract a {"tool": ..., "args": {...}} call from a ```json block, or null. */
    fun parse(text: String): Pair<String, JsonObject>? {
        val start = text.indexOf("```json")
        val block = if (start >= 0) {
            val end = text.indexOf("```", start + 7)
            if (end < 0) return null
            text.substring(start + 7, end)
        } else {
            val s = text.indexOf("{\"tool\"")
            if (s < 0) return null
            text.substring(s)
        }
        return try {
            val obj = json.parseToJsonElement(block.trim()).jsonObject
            val tool = obj["tool"]?.jsonPrimitive?.content ?: return null
            val args = obj["args"]?.jsonObject ?: JsonObject(emptyMap())
            tool to args
        } catch (_: Exception) { null }
    }

    fun argString(args: JsonObject, key: String): String =
        args[key]?.jsonPrimitive?.content ?: ""
}

class AgentTools(
    private val workspace: WorkspaceStore,
    private val shell: ShellRunner
) {
    val names = listOf(
        "list_files", "read_file", "write_file", "create_file", "edit_file",
        "delete_file", "search_workspace", "run_command", "git_status"
    )

    suspend fun run(tool: String, args: kotlinx.serialization.json.JsonObject): ToolResult {
        return when (tool) {
            "list_files" -> {
                val rel = ToolCallParser.argString(args, "path")
                val files = workspace.listFiles(rel)
                ToolResult(true, files.joinToString("\n") { "${it.name} (${it.length()} bytes)" }.ifEmpty { "(empty)" })
            }
            "read_file" -> {
                val path = ToolCallParser.argString(args, "path")
                val content = workspace.read(path)
                if (content == null) ToolResult(false, "File not found or unsafe path: $path")
                else ToolResult(true, content)
            }
            "write_file" -> {
                val path = ToolCallParser.argString(args, "path")
                val content = ToolCallParser.argString(args, "content")
                if (workspace.write(path, content)) ToolResult(true, "Wrote $path (${content.length} chars)")
                else ToolResult(false, "Could not write (unsafe path?): $path")
            }
            "create_file" -> {
                val path = ToolCallParser.argString(args, "path")
                val content = ToolCallParser.argString(args, "content")
                val existing = workspace.read(path)
                if (existing != null) ToolResult(false, "File already exists: $path")
                else if (workspace.write(path, content)) ToolResult(true, "Created $path")
                else ToolResult(false, "Could not create (unsafe path?): $path")
            }
            "edit_file" -> {
                val path = ToolCallParser.argString(args, "path")
                val old = ToolCallParser.argString(args, "old_text")
                val new = ToolCallParser.argString(args, "new_text")
                val content = workspace.read(path) ?: return ToolResult(false, "File not found: $path")
                if (!content.contains(old)) return ToolResult(false, "old_text not found in $path")
                if (workspace.write(path, content.replace(old, new, ignoreCase = false))) {
                    ToolResult(true, "Patched $path")
                } else ToolResult(false, "Could not write $path")
            }
            "delete_file" -> {
                val path = ToolCallParser.argString(args, "path")
                if (workspace.delete(path)) ToolResult(true, "Deleted $path")
                else ToolResult(false, "Could not delete: $path")
            }
            "search_workspace" -> {
                val q = ToolCallParser.argString(args, "query")
                val hits = workspace.search(q)
                ToolResult(true, hits.joinToString("\n") { it.name }.ifEmpty { "No matches for '$q'" })
            }
            "run_command" -> {
                val cmd = ToolCallParser.argString(args, "command")
                shell.run(cmd)
            }
            "git_status" -> {
                val out = shell.run("git status --short --branch 2>&1 || echo 'Not a git folder'")
                out
            }
            else -> ToolResult(false, "Unknown tool: $tool")
        }
    }
}

/** Local shell. Dangerous commands are refused here — the UI also asks first. */
class ShellRunner(private val workspace: WorkspaceStore) {
    private val blocked = listOf("rm -rf", "rm -rf /", "format", "su", "mkfs", "dd if=", ":(){", "shutdown", "reboot")

    fun isDangerous(command: String): Boolean {
        val c = command.trim().lowercase()
        return blocked.any { c.contains(it) } || c.startsWith("rm -rf")
    }

    suspend fun run(command: String): ToolResult = withContext(Dispatchers.IO) {
        if (isDangerous(command)) {
            return@withContext ToolResult(false, "Blocked dangerous command: $command")
        }
        try {
            val proc = ProcessBuilder("sh", "-c", command)
                .directory(workspace.root)
                .redirectErrorStream(true)
                .start()
            val finished = proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return@withContext ToolResult(false, "Command timed out after 30s: $command")
            }
            val out = proc.inputStream.bufferedReader().readText()
            ToolResult(proc.exitValue() == 0, out.ifBlank { "(no output, exit ${proc.exitValue()})" })
        } catch (e: Exception) {
            ToolResult(false, "Shell error: ${e.message}")
        }
    }

    /** Send a command to installed Termux via RUN_COMMAND. Returns false if Termux/Termux:API is missing. */
    fun sendToTermux(context: Context, command: String): Boolean {
        return try {
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            }
            context.startService(intent)
            true
        } catch (_: Exception) { false }
    }
}
