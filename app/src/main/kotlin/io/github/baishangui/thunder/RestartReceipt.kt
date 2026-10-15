package io.github.baishangui.thunder

internal data class RestartReceipt(val oldPids: Set<Int>, val newPids: Set<Int>) {
    fun accepts(pid: Int, elapsed: Long, started: Long): Boolean =
        pid in newPids && elapsed >= started

    companion object {
        fun parse(output: String): RestartReceipt? {
            fun pids(prefix: String): Set<Int>? {
                val lines = output.lineSequence().filter { it.startsWith(prefix) }.toList()
                if (lines.size != 1) return null
                val tokens = lines.single().removePrefix(prefix).trim().split(Regex("\\s+"))
                val values = tokens.map { it.toIntOrNull()?.takeIf { pid -> pid > 0 } ?: return null }
                return values.toSet().takeIf { it.isNotEmpty() }
            }
            val old = pids("THUNDER_RESTART_OLD=") ?: return null
            val new = pids("THUNDER_RESTART_NEW=") ?: return null
            return if (old.intersect(new).isEmpty()) RestartReceipt(old, new) else null
        }
    }
}
