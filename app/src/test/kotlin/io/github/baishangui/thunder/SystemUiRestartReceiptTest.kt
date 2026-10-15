package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class SystemUiRestartReceiptTest {
    @Test fun aFreshReportFromTheOriginalProcessIsNotARestart() {
        assertFalse(SystemUiRestartReceipt.accepts(11, 11, 200, 200, 1500, 1000, 42, setOf(42), setOf(43)))
    }
    @Test fun aNewPidWithoutTheCurrentHookAndConfigurationIsNotSuccess() {
        assertFalse(SystemUiRestartReceipt.accepts(10, 11, 200, 200, 1500, 1000, 43, setOf(42), setOf(43)))
        assertFalse(SystemUiRestartReceipt.accepts(11, 11, 199, 200, 1500, 1000, 43, setOf(42), setOf(43)))
        assertFalse(SystemUiRestartReceipt.accepts(11, 11, 200, 200, 999, 1000, 43, setOf(42), setOf(43)))
        assertFalse(SystemUiRestartReceipt.accepts(11, 11, 200, 200, 1500, 1000, 44, setOf(42), setOf(43)))
    }
    @Test fun onlyTheReplacementProcessCanConfirmTheRestart() {
        assertTrue(SystemUiRestartReceipt.accepts(11, 11, 200, 200, 1500, 1000, 43, setOf(42), setOf(43)))
        assertTrue(SystemUiRestartReceipt.accepts(11, 11, 201, 200, 1500, 1000, 43, setOf(42), setOf(43)))
    }
}
