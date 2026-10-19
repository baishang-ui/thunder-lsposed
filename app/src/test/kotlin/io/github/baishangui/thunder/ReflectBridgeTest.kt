package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class ReflectBridgeTest {
    open class Builder {
        open fun title(text: CharSequence): Builder = this
    }
    class VendorBuilder : Builder() {
        var text: CharSequence = ""
        override fun title(text: CharSequence): VendorBuilder { this.text = text; return this }
    }
    class Overloads {
        fun title(text: String): String = text
        fun title(text: CharSequence): CharSequence = text
    }
    @Test fun covariantVendorBuilderCallsIgnoreCompilerBridge() {
        val builder = VendorBuilder()
        assertSame(builder, Reflect.callMethod(builder, "title", "颜色"))
        assertEquals("颜色", builder.text)
        assertSame(builder, Reflect.callMethod(builder, "title", "数值"))
        assertEquals("数值", builder.text)
    }
    @Test fun realAmbiguousOverloadsAreStillRejected() {
        assertThrows(IllegalStateException::class.java) {
            Reflect.callMethod(Overloads(), "title", "颜色")
        }
    }
}
