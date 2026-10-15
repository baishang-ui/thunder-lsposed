package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class ScopeRestartReportTest {
    private fun report(pid: Int = 42, elapsed: Long = 1000, hooks: String = "cloud-package-entry") =
        ScopeRestartReport(pid, 14, 200, elapsed, hooks, "")

    @Test fun preflightRequiresAFreshReportButAllowsAnOlderInstalledHook() {
        assertTrue(report().copy(version = 13).fresh(200, 1000, 1100))
        assertFalse(report().copy(version = 0).fresh(200, 1000, 1100))
        assertFalse(report(elapsed = 999).fresh(200, 1000, 1100))
        assertFalse(report(elapsed = 1101).fresh(200, 1000, 1100))
        assertFalse(report().copy(revision = 199).fresh(200, 1000, 1100))
        assertFalse(report(pid = 1).fresh(200, 1000, 1100))
    }

    @Test fun hostRestartDoesNotProveThePluginWasLoaded() {
        assertFalse(report(hooks = "remote-settings").pluginLoaded)
        assertFalse(report(hooks = "failed-cloud-package-entry").pluginLoaded)
        assertTrue(report(hooks = "remote-settings, cloud-package-entry").pluginLoaded)
    }

    @Test fun bothIndependentProcessesMustConfirmTheirOwnReplacement() {
        val host = ScopeRestartTargetResult(42, 43)
        val plugin = ScopeRestartTargetResult(52, 53)
        assertTrue(report(43).replacement(host, 14, 200, 1000, 1100))
        assertFalse(report(43).replacement(plugin, 14, 200, 1000, 1100))
        assertFalse(report(52).replacement(plugin, 14, 200, 1000, 1100))
        assertTrue(report(53).replacement(plugin, 14, 200, 1000, 1100))
        assertFalse(report(53).copy(version = 13).replacement(plugin, 14, 200, 1000, 1100))
        assertFalse(report(53).copy(revision = 199).replacement(plugin, 14, 200, 1000, 1100))
        assertFalse(report(53, elapsed = 999).replacement(plugin, 14, 200, 1000, 1100))
    }

    @Test fun protocolRejectsMissingOrAmbiguousProcessResults() {
        val output = "THUNDER_RESTART_HOST_OLD=42\nTHUNDER_RESTART_HOST_NEW=43\nTHUNDER_RESTART_PLUGIN_OLD=52\nTHUNDER_RESTART_PLUGIN_NEW=53\n"
        assertEquals(ScopeRestartTargetResult(52, 53), ScopeRestartTargetResult.parse(output, "PLUGIN"))
        assertNull(ScopeRestartTargetResult.parse(output, "OTHER"))
        assertNull(ScopeRestartTargetResult.parse(output + "THUNDER_RESTART_HOST_NEW=44\n", "HOST"))
        assertNull(ScopeRestartTargetResult.parse(output.replace("HOST_NEW=43", "HOST_NEW=42"), "HOST"))
    }
}
