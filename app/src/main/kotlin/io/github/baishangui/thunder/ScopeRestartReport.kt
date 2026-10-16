package io.github.baishangui.thunder

/** One immutable provider snapshot; never combine fields from different reports. */
internal data class ScopeRestartReport(
    val pid: Int, val version: Int, val revision: Long, val elapsed: Long,
    val hooks: String, val error: String
) {
    val pluginLoaded: Boolean get() = "cloud-package-entry" in hooks.split(',').map(String::trim)

    fun fresh(expectedRevision: Long, started: Long, now: Long): Boolean =
        pid > 1 && version > 0 && revision >= expectedRevision && elapsed in started..now

    fun replacement(target: ScopeRestartTargetResult, expectedVersion: Int, expectedRevision: Long,
                    started: Long, now: Long): Boolean =
        pid == target.new && pid != target.old && version == expectedVersion &&
            fresh(expectedRevision, started, now)

    companion object {
        fun from(values: Map<String, *>): ScopeRestartReport = ScopeRestartReport(
            values["pid"] as? Int ?: -1, values["hook_version"] as? Int ?: 0,
            values["revision"] as? Long ?: -1, values["elapsed"] as? Long ?: -1,
            values["hooks"] as? String ?: "", values["error"] as? String ?: ""
        )
    }
}

internal data class ScopeRestartTargetResult(val old: Int, val new: Int) {
    companion object {
        fun parse(output: String, target: String): ScopeRestartTargetResult? {
            // A different pidof result alone cannot prove that the original process exited.
            if (output.lineSequence().count { it == "THUNDER_RESTART_${target}_EXITED=1" } != 1) return null
            fun pid(suffix: String): Int? = output.lineSequence()
                .filter { it.startsWith("THUNDER_RESTART_${target}_$suffix=") }.toList()
                .singleOrNull()?.substringAfter('=')?.toIntOrNull()?.takeIf { it > 1 }
            val old = pid("OLD") ?: return null
            val new = pid("NEW") ?: return null
            return if (old == new) null else ScopeRestartTargetResult(old, new)
        }
    }
}

/** Wait from each current process report, never from a previous button press. */
internal object ScopeRestartWindow {
    private const val STABLE_MILLIS = 65_000L
    fun remaining(confirmedAt: Long, now: Long): Long =
        (STABLE_MILLIS - (now - confirmedAt)).coerceIn(0, STABLE_MILLIS)
}
