package pui.c17.shade

import org.junit.Assert.*
import org.junit.Test

class RestartReceiptTest {
    @Test fun shellSuccessWithoutNewProcessIsNotConfirmation() {
        assertNull(RestartReceipt.parse("PUI_RESTART_OLD=123\n"))
        assertNull(RestartReceipt.parse("PUI_RESTART_OLD=123\nPUI_RESTART_NEW=123\n"))
        assertNull(RestartReceipt.parse("PUI_RESTART_OLD=123 124\nPUI_RESTART_NEW=124 125\n"))
    }
    @Test fun oldReportCannotConfirmNewConfiguration() {
        val receipt = RestartReceipt.parse("PUI_RESTART_OLD=123\nPUI_RESTART_NEW=456\n")!!
        assertFalse(receipt.accepts(123, 1500, 1000))
        assertFalse(receipt.accepts(456, 999, 1000))
        assertTrue(receipt.accepts(456, 1000, 1000))
    }
    @Test fun rootNoiseAndMultipleNewProcessesAreHandled() {
        val receipt = RestartReceipt.parse("root granted\nPUI_RESTART_OLD=123 124\nPUI_RESTART_NEW=456 457\n")!!
        assertEquals(setOf(123, 124), receipt.oldPids)
        assertTrue(receipt.accepts(457, 1010, 1000))
    }
    @Test fun invalidOrAmbiguousPidOutputIsRejected() {
        listOf("", "PUI_RESTART_OLD=-1\nPUI_RESTART_NEW=2", "PUI_RESTART_OLD=1\nPUI_RESTART_NEW=nope",
            "PUI_RESTART_OLD=1\nPUI_RESTART_NEW=0", "PUI_RESTART_OLD=1\nPUI_RESTART_NEW=2\nPUI_RESTART_NEW=3")
            .forEach { assertNull(RestartReceipt.parse(it)) }
    }
}
