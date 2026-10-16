package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class ScopeRestartTargetResultTest {
    private val output = "THUNDER_RESTART_HOST_OLD=42\nTHUNDER_RESTART_HOST_EXITED=1\nTHUNDER_RESTART_HOST_NEW=43\n"

    @Test fun acceptsOnlyAConfirmedSystemUiReplacement() {
        assertEquals(ScopeRestartTargetResult(42, 43), ScopeRestartTargetResult.parse(output))
        assertNull(ScopeRestartTargetResult.parse(output.replace("HOST_NEW=43", "HOST_NEW=42")))
        assertNull(ScopeRestartTargetResult.parse(output.replace("HOST_OLD=42", "HOST_OLD=1")))
        assertNull(ScopeRestartTargetResult.parse(output.replace("HOST_NEW=43", "HOST_NEW=0")))
        assertNull(ScopeRestartTargetResult.parse(output.replace("HOST_NEW=43", "HOST_NEW=43 44")))
        assertNull(ScopeRestartTargetResult.parse(output.replace("HOST", "PLUGIN")))
    }

    @Test fun rejectsMissingOrAmbiguousProcessResults() {
        assertNull(ScopeRestartTargetResult.parse(output + "THUNDER_RESTART_HOST_NEW=44\n"))
        assertNull(ScopeRestartTargetResult.parse(output + "THUNDER_RESTART_HOST_OLD=42\n"))
        assertNull(ScopeRestartTargetResult.parse(output.replace("THUNDER_RESTART_HOST_EXITED=1\n", "")))
        assertNull(ScopeRestartTargetResult.parse(output + "THUNDER_RESTART_HOST_EXITED=1\n"))
        assertNull(ScopeRestartTargetResult.parse(output.replace("THUNDER_RESTART_HOST_NEW=43\n", "")))
    }
}
