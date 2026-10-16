package io.github.baishangui.thunder

internal data class ScopeRestartTargetResult(val old: Int, val new: Int) {
    companion object {
        fun parse(output: String): ScopeRestartTargetResult? {
            // A different pidof result alone cannot prove that the original process exited.
            if (output.lineSequence().count { it == "THUNDER_RESTART_HOST_EXITED=1" } != 1) return null
            fun pid(suffix: String): Int? = output.lineSequence()
                .filter { it.startsWith("THUNDER_RESTART_HOST_$suffix=") }.toList()
                .singleOrNull()?.substringAfter('=')?.toIntOrNull()?.takeIf { it > 1 }
            val old = pid("OLD") ?: return null
            val new = pid("NEW") ?: return null
            return if (old == new) null else ScopeRestartTargetResult(old, new)
        }
    }
}
