package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class ModuleScopeTest {
    private val ui = setOf("com.android.systemui", "com.oplus.systemui.plugins", "com.android.settings")

    @Test fun allThreeHostsAreRequired() {
        assertTrue(ModuleScope.complete(ui))
        assertTrue(ModuleScope.complete(ui + "android"))
        assertEquals("作用域已完整：系统界面、系统界面插件、设置", ModuleScope.describe(ui))
        assertEquals(ModuleScope.describe(ui), ModuleScope.describe(ui + setOf("android", "system", "com.android.launcher")))
    }
    @Test fun settingsScopeIsRequiredForEmbeddedEntry() {
        val status = ModuleScope.describe(setOf("com.android.systemui", "com.oplus.systemui.plugins"))
        assertFalse(ModuleScope.complete(setOf("com.android.systemui", "com.oplus.systemui.plugins")))
        assertEquals("作用域缺少：设置。请在 LSPosed 的 thunder 作用域中勾选，勾选后正常重启手机。", status)
    }
    @Test fun missingPluginStillNeedsAttention() {
        assertFalse(ModuleScope.complete(setOf("com.android.systemui")))
        assertFalse(ModuleScope.complete(setOf("com.oplus.systemui.plugins", "com.android.settings")))
        val status = ModuleScope.describe(setOf("com.android.systemui"))
        assertTrue(status.contains("作用域缺少：系统界面插件"))
        assertFalse(status.contains("作用域已完整"))
    }
}
