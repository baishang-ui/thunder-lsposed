package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class SettingsReceiptTest {
    @Test fun oldHookOrOldReportCannotConfirmLiveDelivery() {
        assertFalse(SettingsReceipt.accepts(9, 10, 200, 200, 1500, 1000))
        assertFalse(SettingsReceipt.accepts(10, 10, 199, 200, 1500, 1000))
        assertFalse(SettingsReceipt.accepts(10, 10, 200, 200, 999, 1000))
    }
    @Test fun currentOrNewerConfigurationCanBeConfirmedInTheExistingProcess() {
        assertTrue(SettingsReceipt.accepts(10, 10, 200, 200, 1000, 1000))
        assertTrue(SettingsReceipt.accepts(10, 10, 201, 200, 1500, 1000))
    }
}
