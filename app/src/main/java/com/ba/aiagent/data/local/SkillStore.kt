package com.ba.aiagent.data.local

import android.content.Context
import com.ba.aiagent.domain.model.Skill
import kotlinx.serialization.json.Json

/** SKILL.md format (same as Claude Code): frontmatter name + description, then Markdown instructions. */
object SkillParser {
    fun parse(markdown: String, source: String = "custom"): Skill? {
        val trimmed = markdown.trim()
        if (!trimmed.startsWith("---")) return null
        val end = trimmed.indexOf("\n---", 3)
        if (end < 0) return null
        val front = trimmed.substring(3, end)
        val body = trimmed.substring(end + 4).trim()
        val name = front.lines().firstOrNull { it.trim().startsWith("name:") }
            ?.substringAfter("name:")?.trim()?.trim('"', '\'') ?: return null
        val description = front.lines().firstOrNull { it.trim().startsWith("description:") }
            ?.substringAfter("description:")?.trim()?.trim('"', '\'') ?: ""
        if (name.isBlank() || body.isBlank()) return null
        return Skill(name = name, description = description, instructions = body, source = source)
    }

    fun render(skill: Skill): String =
        "---\nname: ${skill.name}\ndescription: ${skill.description}\n---\n\n${skill.instructions}\n"
}

class SkillStore(private val context: Context, private val workspace: WorkspaceStore) {
    fun builtIn(): List<Skill> = listOf(
        Skill("plan", "Plan a task before doing it — write steps + todo list first.", "1. Restate the goal in one line.\n2. List the files you will touch.\n3. Write a numbered todo list.\n4. Ask the user to approve the plan before acting.\n5. Work the list in order, ticking each step off."),
        Skill("fix", "Fix a bug: reproduce it, find the cause, fix it, verify the fix.", "1. Reproduce the bug or read the exact error.\n2. Find the smallest code that causes it.\n3. Fix only that layer.\n4. Re-run / re-read to verify the fix works.\n5. Say what changed and how to retest."),
        Skill("review", "Review code for bugs, security issues and style.", "Check: correctness, null/edge cases, security (keys, injection, traversal), performance, naming, dead code. Give findings as: Must fix / Should fix / Nice to have, with file + line."),
        Skill("refactor", "Clean refactor with no behaviour change.", "Keep behaviour identical. Small steps, one file at a time. Keep tests passing. Say what moved and why."),
        Skill("explain", "Explain code or a project in simple words.", "Start with what it does in one sentence, then how it works step by step, then the tricky parts. Use short sentences."),
        Skill("test", "Write and run tests for changed code.", "Cover the happy path + one edge case + one failure case. Name tests after the behaviour they check."),
        Skill("commit", "Write a clean git commit message + summary.", "Subject under 50 chars, imperative mood. Body: what changed, why, how to test."),
        Skill("new", "Scaffold a new project or file from a description.", "Ask the missing choices first (name, language, platform). Then create the smallest complete structure that runs."),
        Skill("docs", "Write or update README and docs.", "What it is, how to install, how to run, how to build. No lorem ipsum."),
        Skill("ui", "Build a mobile screen in the active design system.", "Load the active DESIGN.md theme tokens first. Build the screen in that style: colours, radius, font, spacing. Keep it usable on a phone.")
    )

    fun custom(): List<Skill> {
        val dir = workspace.skillsDir
        return dir.listFiles()?.filter { it.isDirectory }?.mapNotNull { d ->
            val f = java.io.File(d, "SKILL.md")
            if (f.exists()) SkillParser.parse(runCatching { f.readText() }.getOrNull() ?: "", "custom") else null
        } ?: emptyList()
    }

    fun all(): List<Skill> = builtIn() + custom()

    fun saveCustom(skill: Skill): Boolean {
        val dir = java.io.File(workspace.skillsDir, skill.name.lowercase().replace(" ", "-"))
        return runCatching {
            dir.mkdirs()
            java.io.File(dir, "SKILL.md").writeText(SkillParser.render(skill))
            true
        }.getOrDefault(false)
    }

    fun deleteCustom(name: String): Boolean {
        val dir = java.io.File(workspace.skillsDir, name.lowercase().replace(" ", "-"))
        return runCatching { dir.deleteRecursively() }.getOrDefault(false)
    }

    /** Pick skills whose description matches the user's message. */
    fun matchFor(message: String): List<Skill> {
        val m = message.lowercase()
        val triggers = mapOf(
            "fix" to listOf("fix", "bug", "error", "crash", "broken", "not working"),
            "review" to listOf("review", "check my code"),
            "refactor" to listOf("refactor", "clean up"),
            "explain" to listOf("explain", "what does", "how does"),
            "test" to listOf("test", "write a test"),
            "commit" to listOf("commit", "git commit"),
            "new" to listOf("new project", "create a project", "scaffold"),
            "docs" to listOf("readme", "document"),
            "ui" to listOf("screen", "ui", "design a", "login page", "make a page"),
            "plan" to listOf("plan", "steps")
        )
        return all().filter { skill ->
            triggers[skill.name]?.any { m.contains(it) } == true
        }
    }
}
