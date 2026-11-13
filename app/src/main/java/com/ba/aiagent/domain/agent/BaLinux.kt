package com.ba.aiagent.domain.agent

import android.content.Context
import com.ba.aiagent.data.local.WorkspaceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * BA Linux — our own full Linux environment, built into this app.
 *
 * How it works (the same idea Termux was built on, but ours):
 * - An Alpine Linux rootfs (small, open-source) ships inside the APK.
 * - On first use it is unpacked into the app's private storage.
 * - PRoot (open-source, built from source in our CI) runs the rootfs as
 *   a user-mode sandbox — no root, no mount, no other app.
 * - Everything runs on THIS phone's CPU and RAM. No VPS, no server.
 * - Inside, `apk` is the package manager: `apk add python3 nodejs git`
 *   installs real tools, exactly like Termux's `pkg`.
 *
 * On a device where the sandbox cannot run (anything but 64-bit ARM),
 * the terminal falls back to the plain system shell.
 */
class BaLinux(private val context: Context, private val workspace: WorkspaceStore) {
    val rootfsDir = File(context.filesDir, "ba-linux/rootfs")
    val homeDir = File(context.filesDir, "ba-linux/home")
    private val readyMarker = File(rootfsDir, ".ba-linux-ready")

    private val _state = MutableStateFlow("BA Linux not set up yet")
    val state: StateFlow<String> = _state.asStateFlow()

    private val setupMutex = Mutex()
    @Volatile
    var ready = false
        private set

    /** Guest path of the app workspace inside the sandbox. */
    val guestWorkspace = "/workspace"

    private val prootFile: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
    private val loaderFile: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libproot-loader.so")

    /** True only when the sandbox binaries shipped with the APK. */
    fun isSupported(): Boolean = prootFile.exists() && loaderFile.exists()

    /**
     * Make the sandbox ready (first run: unpack the rootfs, then
     * `apk update` once). Safe to call repeatedly; only the first call
     * does the work. Returns true when the shell can run inside BA Linux.
     */
    suspend fun ensureReady(log: (String) -> Unit): Boolean = setupMutex.withLock {
        if (ready) return true
        if (!isSupported()) {
            _state.value = "BA Linux needs a 64-bit ARM phone — using the system shell instead."
            log("\nBA Linux is not available on this device (needs 64-bit ARM).\nFalling back to the system shell.\n")
            return false
        }
        if (readyMarker.exists() && File(rootfsDir, "bin/sh").exists()) {
            ready = true
            _state.value = "BA Linux ready (Alpine)"
            return true
        }
        return try {
            log("\nSetting up BA Linux (first run) — unpacking Alpine Linux…\n")
            _state.value = "Setting up BA Linux… (unpacking Alpine)"
            rootfsDir.mkdirs()
            homeDir.mkdirs()
            context.assets.open("linux/alpine-aarch64.bin").use { input ->
                TarGz.extract(input, rootfsDir)
            }
            // Minimal network config so apk and friends can reach the internet.
            File(rootfsDir, "etc/resolv.conf").apply {
                parentFile?.mkdirs()
                writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
            }
            File(rootfsDir, "etc/hosts").apply {
                writeText("127.0.0.1 localhost\n::1 localhost\n")
            }
            File(rootfsDir, "workspace").mkdirs()
            File(rootfsDir, "root").mkdirs()
            readyMarker.createNewFile()
            ready = true
            _state.value = "BA Linux ready (Alpine)"
            log("BA Linux ready — Alpine Linux on this phone.\nUpdating package lists (apk update)…\n")
            // Best effort: a failed update does not block the shell.
            val (ok, out) = runGuestCommand("apk update", 120)
            if (ok) log("Package lists updated. Install tools with: apk add python3 nodejs git\n")
            else log("apk update did not finish cleanly — try `apk update` by hand later.\n$out\n")
            true
        } catch (e: Exception) {
            _state.value = "BA Linux setup failed — using the system shell."
            log("\nBA Linux setup failed (${e.message}). Falling back to the system shell.\n")
            false
        }
    }

    /** The full command line that starts the guest shell. */
    fun prootCommand(): List<String> = listOf(
        prootFile.absolutePath,
        "-0",
        "-r", rootfsDir.absolutePath,
        "-b", "${workspace.root.absolutePath}:/workspace",
        "-b", "${homeDir.absolutePath}:/root",
        "-b", "/dev",
        "-b", "/dev/pts",
        "-b", "/proc",
        "-b", "/sys",
        "-w", "/workspace",
        "--link2symlink",
        "--kill-on-exit",
        "/bin/sh"
    )

    /** Environment for the guest shell. */
    fun guestEnv(): Map<String, String> = mapOf(
        "PROOT_LOADER" to loaderFile.absolutePath,
        "HOME" to "/root",
        "TERM" to "xterm-256color",
        "LANG" to "C.UTF-8",
        "TMPDIR" to "/tmp",
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/bin:/usr/sbin:/sbin:/bin"
    )

    /** Run one guest command (used for the first-run `apk update`). */
    private suspend fun runGuestCommand(command: String, timeoutSec: Long): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder(prootCommand() + listOf("-c", command))
                pb.environment().putAll(guestEnv())
                pb.redirectErrorStream(true)
                val proc = pb.start()
                val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
                if (!finished) {
                    proc.destroyForcibly()
                    return@withContext false to "(timed out)"
                }
                val out = proc.inputStream.bufferedReader().readText().takeLast(2000)
                (proc.exitValue() == 0) to out
            } catch (e: Exception) {
                false to (e.message ?: "error")
            }
        }
}
