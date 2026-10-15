package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class RestartReceiptTest {
    @Test fun shellSuccessWithoutNewProcessIsNotConfirmation() {
        assertNull(RestartReceipt.parse("THUNDER_RESTART_OLD=123\n"))
        assertNull(RestartReceipt.parse("THUNDER_RESTART_OLD=123\nTHUNDER_RESTART_NEW=123\n"))
        assertNull(RestartReceipt.parse("THUNDER_RESTART_OLD=123 124\nTHUNDER_RESTART_NEW=124 125\n"))
    }
    @Test fun oldReportCannotConfirmNewConfiguration() {
        val receipt = RestartReceipt.parse("THUNDER_RESTART_OLD=123\nTHUNDER_RESTART_NEW=456\n")!!
        assertFalse(receipt.accepts(123, 1500, 1000))
        assertFalse(receipt.accepts(456, 999, 1000))
        assertTrue(receipt.accepts(456, 1000, 1000))
    }
    @Test fun rootNoiseAndMultipleNewProcessesAreHandled() {
        val receipt = RestartReceipt.parse("root granted\nTHUNDER_RESTART_OLD=123 124\nTHUNDER_RESTART_NEW=456 457\n")!!
        assertEquals(setOf(123, 124), receipt.oldPids)
        assertTrue(receipt.accepts(457, 1010, 1000))
    }
    @Test fun invalidOrAmbiguousPidOutputIsRejected() {
        listOf("", "THUNDER_RESTART_OLD=-1\nTHUNDER_RESTART_NEW=2", "THUNDER_RESTART_OLD=1\nTHUNDER_RESTART_NEW=nope",
            "THUNDER_RESTART_OLD=1\nTHUNDER_RESTART_NEW=0", "THUNDER_RESTART_OLD=1\nTHUNDER_RESTART_NEW=2\nTHUNDER_RESTART_NEW=3")
            .forEach { assertNull(RestartReceipt.parse(it)) }
    }
}
