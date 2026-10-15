package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class SystemUiRestartWindowTest {
    @Test fun aNewlyConfirmedProcessMustSurviveTheFrameworkObservationWindow() {
        assertEquals(65_000L, SystemUiRestartWindow.remaining(100_000, 100_000))
        assertEquals(5_000L, SystemUiRestartWindow.remaining(100_000, 160_000))
        assertEquals(0L, SystemUiRestartWindow.remaining(100_000, 165_000))
        assertEquals(0L, SystemUiRestartWindow.remaining(100_000, 200_000))
    }
    @Test fun aReplacementProcessCannotReuseThePreviousProcessesWaitingTime() {
        assertEquals(0L, SystemUiRestartWindow.remaining(100_000, 200_000))
        assertEquals(65_000L, SystemUiRestartWindow.remaining(200_000, 200_000))
    }
    @Test fun anInvalidFutureTimestampCannotSkipTheWait() {
        assertEquals(65_000L, SystemUiRestartWindow.remaining(200_000, 100_000))
    }
}
