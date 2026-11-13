package com.ba.aiagent.domain.agent

import com.ba.aiagent.data.api.AiApiClient
import com.ba.aiagent.data.api.ChatMsg
import com.ba.aiagent.data.api.Providers
import com.ba.aiagent.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class AgentStep(val tool: String, val args: String, val status: String, val result: String)

/**
 * Claude Code style tool loop: max 25 steps. The model replies with a final
 * answer OR one ```json tool call. Dangerous tools need [approve] to return
 * true first — the UI shows the diff/command and asks the user.
 */
class AgentEngine(
    private val api: AiApiClient,
    private val tools: AgentTools
) {
    suspend fun runAgentTurn(
        providerId: String,
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        userText: String,
        temperature: Double,
        maxTokens: Int,
        systemPrompt: String,
        approve: suspend (tool: String, summary: String) -> Boolean,
        onStep: (AgentStep) -> Unit,
        onToken: (String) -> Unit
    ) {
        val provider = Providers.byId(providerId)
        val convo = history.filter { it.role == "user" || it.role == "assistant" }
            .map { ChatMsg(it.role, it.content) }.toMutableList()
        convo.add(ChatMsg("user", userText))
        val toolDesc = """
            You have these tools. To use one, reply with ONLY a ```json block:
            {"tool": "name", "args": {...}}
            Tools: list_files {path?}, read_file {path}, write_file {path, content},
            create_file {path, content}, edit_file {path, old_text, new_text},
            delete_file {path}, search_workspace {query}, run_command {command},
            git_status {}.
            Otherwise reply with the final answer in Markdown.
        """.trimIndent()
        val fullSystem = systemPrompt + "\n\n" + toolDesc

        var steps = 0
        while (steps < 25) {
            steps++
            var reply = StringBuilder()
            api.chatStream(provider, apiKey, model, convo, temperature, maxTokens, fullSystem)
                .collect { token ->
                    reply.append(token)
                    onToken(token)
                }
            val text = reply.toString()
            val call = ToolCallParser.parse(text)
            if (call == null) return // final answer already streamed
            val (tool, args) = call
            val summary = args.toString().take(300)
            onStep(AgentStep(tool, summary, "Running", ""))
            val dangerous = tool in setOf("write_file", "edit_file", "delete_file", "run_command")
            if (dangerous) {
                val ok = try { approve(tool, summary) } catch (_: Exception) { false }
                if (!ok) {
                    onStep(AgentStep(tool, summary, "Failed", "User denied this tool call"))
                    convo.add(ChatMsg("assistant", text))
                    convo.add(ChatMsg("user", "User denied this tool call: $tool. Continue without it."))
                    continue
                }
            }
            val result = tools.run(tool, args)
            onStep(AgentStep(tool, summary, if (result.ok) "Done" else "Failed", result.output.take(2000)))
            convo.add(ChatMsg("assistant", text))
            convo.add(ChatMsg("user", "Tool $tool result (${if (result.ok) "ok" else "failed"}):\n${result.output.take(4000)}"))
        }
    }
}
