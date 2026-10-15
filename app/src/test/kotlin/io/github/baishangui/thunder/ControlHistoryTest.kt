package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class ControlHistoryTest {
    @Test fun repeatedEditsCanReturnToValueBeforeFirstEdit() {
        val h=ControlHistory()
        assertNull(h.original("panel:radius",100))
        h.record("panel:radius",100,110)
        h.record("panel:radius",110,120)
        assertEquals(100,h.original("panel:radius",120))
        assertNull(h.original("panel:radius",100))
    }
    @Test fun importedOrResetValueCanReturnToPreviouslyDisplayedValue() {
        val h=ControlHistory()
        h.register("panel:radius",130)
        h.register("panel:radius",100)
        assertEquals(130,h.original("panel:radius",100))
        assertNull(h.original("panel:radius",130))
    }
    @Test fun controlsAndPagesHaveIndependentUndoValues() {
        val h=ControlHistory()
        h.record("panel:radius",100,80)
        h.record("banner:radius",40,60)
        h.record("cloud:switch",false,true)
        assertEquals(100,h.original("panel:radius",80))
        assertEquals(40,h.original("banner:radius",60))
        assertEquals(false,h.original("cloud:switch",true))
    }
}
