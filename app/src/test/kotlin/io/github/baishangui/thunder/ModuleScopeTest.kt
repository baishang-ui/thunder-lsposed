package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class ModuleScopeTest {
    private val ui = setOf("com.android.systemui", "com.oplus.systemui.plugins")

    @Test fun onlySystemUiAndPluginAreRequired() {
        assertEquals("作用域已完整：系统界面、系统界面插件", ModuleScope.describe(ui))
        assertEquals(ModuleScope.describe(ui), ModuleScope.describe(ui + setOf("android", "system", "com.android.launcher")))
    }
    @Test fun missingPluginStillNeedsAttention() {
        val status = ModuleScope.describe(setOf("com.android.systemui"))
        assertTrue(status.contains("作用域缺少：系统界面插件"))
        assertFalse(status.contains("作用域已完整"))
    }
}
