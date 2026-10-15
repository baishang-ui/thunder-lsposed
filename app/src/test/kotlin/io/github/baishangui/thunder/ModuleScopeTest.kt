package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class ModuleScopeTest {
    private val ui = setOf("com.android.systemui", "com.oplus.systemui.plugins")

    @Test fun frameworkUiScopeDoesNotEnableSystemServer() {
        val status = ModuleScope.describe(ui + "android", followApp = true, supportsSystem = true)
        assertTrue(status.contains("作用域缺少"))
        assertTrue(status.contains("system"))
        assertFalse(status.contains("作用域已完整"))
        assertTrue(status.contains("android"))
    }
    @Test fun serverScopeIsOnlyRequiredWhenMotionIsRequested() {
        assertEquals(ui, ModuleScope.required(false).keys)
        assertEquals(ui + setOf("system", "com.android.launcher"), ModuleScope.required(true).keys)
        assertTrue(ModuleScope.describe(ui, false, true).contains("作用域已完整"))
        assertTrue(ModuleScope.describe(ui + setOf("system", "com.android.launcher"), true, true).contains("作用域已完整"))
    }
    @Test fun serverWithoutLauncherCannotClaimMotionScopeIsComplete() {
        val status = ModuleScope.describe(ui + "system", true, true)
        assertTrue(status.contains("系统桌面"))
        assertFalse(status.contains("作用域已完整"))
    }
    @Test fun missingCapabilityCannotBeReportedAsComplete() {
        val status = ModuleScope.describe(ui + "system", true, false)
        assertFalse(status.contains("作用域已完整"))
        assertTrue(status.contains("未声明系统进程 Hook 能力"))
        assertTrue(ModuleScope.describe(ui, false, false).contains("作用域已完整"))
    }
}
