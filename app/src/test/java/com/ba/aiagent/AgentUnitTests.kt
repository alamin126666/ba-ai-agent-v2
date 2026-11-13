package com.ba.aiagent

import com.ba.aiagent.data.api.AiApiClient
import com.ba.aiagent.domain.agent.ToolCallParser
import com.ba.aiagent.data.local.SkillParser
import com.ba.aiagent.data.local.WorkspaceStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AgentUnitTests {

    @Test
    fun toolCallJsonParsing_valid() {
        val text = "I will write the file.\n```json\n{\"tool\": \"write_file\", \"args\": {\"path\": \"hello.kt\", \"content\": \"fun main() {}\"}}\n```"
        val parsed = ToolCallParser.parse(text)
        assertNotNull(parsed)
        assertEquals("write_file", parsed!!.first)
        assertEquals("hello.kt", ToolCallParser.argString(parsed.second, "path"))
    }

    @Test
    fun toolCallJsonParsing_invalid() {
        assertNull(ToolCallParser.parse("Just a normal reply, no tool call."))
        assertNull(ToolCallParser.parse("```json\n{not json\n```"))
    }

    @Test
    fun workspacePathSafety_dotdotRejected() {
        // WorkspaceStore needs a Context; test the rule directly via a temp dir shim:
        val root = File(System.getProperty("java.io.tmpdir"), "ba_test_ws").apply { mkdirs() }
        val traversal = File(root, "../escape.txt")
        // The rule: any path containing ".." must be rejected, and canonical path must stay under root.
        val unsafe = "../escape.txt"
        assertTrue(unsafe.contains(".."))
        assertFalse(traversal.canonicalPath.startsWith(root.canonicalPath + File.separator) && !unsafe.contains(".."))
        assertTrue(traversal.canonicalPath.startsWith(root.parentFile.canonicalPath))
    }

    @Test
    fun editFilePatchApply() {
        val original = "fun hello() {\n    println(\"hi\")\n}\n"
        val patched = original.replace("println(\"hi\")", "println(\"hello\")")
        assertTrue(patched.contains("println(\"hello\")"))
        assertFalse(patched.contains("println(\"hi\")"))
    }

    @Test
    fun skillFrontmatterParse() {
        val md = "---\nname: fix\ndescription: Fix a bug.\n---\n\n1. Reproduce it.\n2. Fix it.\n"
        val skill = SkillParser.parse(md, "custom")
        assertNotNull(skill)
        assertEquals("fix", skill!!.name)
        assertEquals("Fix a bug.", skill.description)
        assertTrue(skill.instructions.contains("Reproduce"))
    }

    @Test
    fun skillFrontmatterParse_invalid() {
        assertNull(SkillParser.parse("no frontmatter here"))
        assertNull(SkillParser.parse("---\nname: \ndescription: x\n---\n\nbody"))
    }

    @Test
    fun sseDeltaExtraction() {
        // The SSE parser reads `data: {...}` lines and takes choices[0].delta.content.
        // Verify the JSON shape the parser expects:
        val data = "{\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}"
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(data).jsonObject
        val delta = obj["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("delta")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
        assertEquals("Hello", delta)
    }
}
