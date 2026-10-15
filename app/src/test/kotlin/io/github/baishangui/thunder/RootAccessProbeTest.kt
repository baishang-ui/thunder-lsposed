package io.github.baishangui.thunder

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RootAccessProbeTest {
    private fun probe(command: String): RootAccessState = RootAccessProbe.check {
        ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
    }

    @Test fun requiresSuccessfulCommandAndRootUid() {
        assertEquals(RootAccessState.Granted, probe("printf '0\\n'"))
        assertEquals(RootAccessState.Denied, probe("printf '2000\\n'"))
        assertEquals(RootAccessState.Denied, probe("printf '0\\n'; exit 1"))
        assertEquals(RootAccessState.Denied, probe("exit 0"))
    }

    @Test fun missingSuIsUnavailable() {
        assertEquals(RootAccessState.Unavailable, RootAccessProbe.check { throw IOException("su not found") })
    }

    @Test fun timeoutStopsTheVerificationProcess() {
        val process = ProcessBuilder("sh", "-c", "exec sleep 10").start()
        assertEquals(RootAccessState.TimedOut, RootAccessProbe.check(timeoutMillis = 50) { process })
        process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
        assertFalse(process.isAlive)
    }
}
