package com.example.hyperosai

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small, explicit command catalog. Never accept a shell string from the UI or AI.
 * Write actions are limited to three global animation-scale keys and support rollback.
 */
object ShizukuTools {
    private val readOnly = linkedMapOf(
        "Device properties (basic)" to listOf("getprop", "ro.product.model"),
        "Android release" to listOf("getprop", "ro.build.version.release"),
        "HyperOS build fingerprint" to listOf("getprop", "ro.build.display.id"),
        "Display size" to listOf("wm", "size"),
        "Display density" to listOf("wm", "density"),
        "Animator scale" to listOf("settings", "get", "global", "animator_duration_scale"),
        "Transition scale" to listOf("settings", "get", "global", "transition_animation_scale"),
        "Window animation scale" to listOf("settings", "get", "global", "window_animation_scale")
    )

    private val animationKeys = listOf(
        "animator_duration_scale",
        "transition_animation_scale",
        "window_animation_scale"
    )
    private const val BACKUP_PREFS = "hyperos_ai_animation_rollback"
    private const val BACKUP_VALID = "valid"

    data class WriteAction(
        val id: String,
        val title: String,
        val detail: String,
        val rollbackGuidance: String
    )

    val writeActions = listOf(
        WriteAction(
            id = "animations_half",
            title = "Set animation scales to 0.5×",
            detail = "Writes only the three global Android animation-scale settings. This can make transitions feel faster; it does not increase CPU performance.",
            rollbackGuidance = "The app first saves each existing value. Use ‘Rollback last animation change’ to restore those exact values. If the app is removed or the backup is lost, set the three scales back in Developer options or with ADB."
        ),
        WriteAction(
            id = "animations_rollback",
            title = "Rollback last animation change",
            detail = "Restores the exact three animation-scale values captured immediately before the last successful 0.5× change.",
            rollbackGuidance = "If rollback reports a partial failure, reopen Developer options and manually restore Window, Transition, and Animator duration scales using the values printed in the result."
        )
    )

    fun isPermissionGranted(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun isBinderReady(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun commandNames(): List<String> = readOnly.keys.toList()

    @Suppress("DEPRECATION")
    private fun runCommand(args: List<String>): Pair<Int, String> {
        val process = Shizuku.newProcess(args.toTypedArray(), null, null)
        val stdout = process.inputStream.bufferedReader().use { it.readText() }.take(8000)
        val stderr = process.errorStream.bufferedReader().use { it.readText() }.take(2000)
        val exit = process.waitFor()
        return exit to buildString {
            append(stdout.trim().ifBlank { "" })
            if (stderr.isNotBlank()) append(if (isNotEmpty()) "\n" else "").append("stderr: ").append(stderr.trim())
        }
    }

    private fun ensureAccess() {
        check(isBinderReady()) { "Shizuku is not running. Start Shizuku first." }
        check(isPermissionGranted()) { "Grant Shizuku permission first." }
    }

    suspend fun executeReadOnly(name: String): String = withContext(Dispatchers.IO) {
        require(name in readOnly) { "Command is not allowlisted." }
        ensureAccess()
        val (exit, output) = runCommand(readOnly.getValue(name))
        "${output.ifBlank { "(no output)" }}\n\nExit code: $exit"
    }

    suspend fun executeWriteAction(context: Context, actionId: String): String = withContext(Dispatchers.IO) {
        require(writeActions.any { it.id == actionId }) { "Write action is not allowlisted." }
        ensureAccess()
        when (actionId) {
            "animations_half" -> setAnimationScalesToHalf(context)
            "animations_rollback" -> rollbackAnimationScales(context)
            else -> error("Unknown write action.")
        }
    }

    private fun readScale(key: String): String {
        val (exit, output) = runCommand(listOf("settings", "get", "global", key))
        check(exit == 0) { "Could not read $key: $output" }
        val value = output.trim()
        check(isValidScale(value)) {
            "Unexpected value for $key: '$value'. No changes were made."
        }
        return value
    }

    private fun isValidScale(value: String): Boolean {
        if (!value.matches(Regex("(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)"))) return false
        val number = value.toFloatOrNull() ?: return false
        return number.isFinite() && number in 0f..10f
    }

    private fun writeScale(key: String, value: String) {
        require(key in animationKeys) { "Setting key is not allowlisted." }
        require(isValidScale(value)) { "Animation scale value is invalid." }
        val (exit, output) = runCommand(listOf("settings", "put", "global", key, value))
        check(exit == 0) { "Failed to write $key=$value: $output" }
    }

    private fun setAnimationScalesToHalf(context: Context): String {
        val prefs = context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE)
        val previous = animationKeys.associateWith { readScale(it) }
        // Persist rollback values before the first write, so interruption does not lose the backup.
        val backupSaved = prefs.edit().clear().putBoolean(BACKUP_VALID, true).apply {
            previous.forEach { (key, value) -> putString(key, value) }
        }.commit()
        check(backupSaved && prefs.getBoolean(BACKUP_VALID, false)) { "Could not save rollback values; no changes were made." }

        val changed = mutableListOf<String>()
        try {
            animationKeys.forEach { key ->
                // Include the key before issuing the command in case the write succeeds but reports an error.
                changed += key
                writeScale(key, "0.5")
            }
        } catch (failure: Exception) {
            val rollbackErrors = mutableListOf<String>()
            changed.asReversed().forEach { key ->
                runCatching { writeScale(key, previous.getValue(key)) }
                    .onFailure { rollbackErrors += "$key: ${it.message}" }
            }
            return buildString {
                append("Change failed: ${failure.message}\n")
                append("Attempted to restore changed values: ")
                append(if (rollbackErrors.isEmpty()) "success" else rollbackErrors.joinToString())
                append("\nSaved original values: ")
                append(previous.entries.joinToString { "${it.key}=${it.value}" })
            }
        }
        return "Animation scales set to 0.5×.\nPrevious values saved for rollback:\n" +
            previous.entries.joinToString("\n") { "${it.key}=${it.value}" } +
            "\n\nRollback is available in Tools."
    }

    private fun rollbackAnimationScales(context: Context): String {
        val prefs = context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE)
        check(prefs.getBoolean(BACKUP_VALID, false)) { "No saved animation backup exists. Run the 0.5× action first." }
        val previous = animationKeys.associateWith { key ->
            prefs.getString(key, null) ?: error("Backup is incomplete for $key; refusing to guess a value.")
        }
        val restored = mutableListOf<String>()
        val errors = mutableListOf<String>()
        animationKeys.forEach { key ->
            runCatching { writeScale(key, previous.getValue(key)) }
                .onSuccess { restored += key }
                .onFailure { errors += "$key: ${it.message}" }
        }
        if (errors.isEmpty()) {
            prefs.edit().putBoolean(BACKUP_VALID, false).commit()
            "Rollback complete. Restored exact previous values:\n" + previous.entries.joinToString("\n") { "${it.key}=${it.value}" }
        } else {
            "Rollback partially failed. Restored: ${restored.joinToString().ifBlank { "none" }}\nErrors: ${errors.joinToString("; ")}\nOriginal values remain in the app backup where available:\n" +
                previous.entries.joinToString("\n") { "${it.key}=${it.value}" }
        }
    }
}
