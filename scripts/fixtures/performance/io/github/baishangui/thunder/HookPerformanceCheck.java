package io.github.baishangui.thunder;

import java.lang.management.ManagementFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/** Host-JVM microbenchmark. Allocation evidence only; this does not measure ART or GPU work. */
public class HookPerformanceCheck {
    private static volatile Object sink;
    static class Target { private int value = 1000; private Object read() { return this; } }
    private static void measure(String label, Runnable operation) {
        int iterations = 200000;
        for (int i = 0; i < iterations; i++) operation.run();
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().getId();
        for (int round = 0; round < 3; round++) {
            long allocated = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
            for (int i = 0; i < iterations; i++) operation.run();
            long elapsed = System.nanoTime() - start;
            long bytes = bean.getThreadAllocatedBytes(thread) - allocated;
            System.out.printf("%s round=%d ns/op=%.1f bytes/op=%.1f%n", label, round + 1,
                (double) elapsed / iterations, (double) bytes / iterations);
        }
    }
    public static void main(String[] args) {
        Target target = new Target();
        measure("field+no-arg-method", () -> {
            sink = Reflect.getObjectField(target, "value");
            sink = Reflect.callMethod(target, "read");
        });
        Bundle data = new Bundle(); data.putBoolean("enabled", true);
        data.putBoolean("shade_tint", true); data.putInt("shade_color", 0xffffffff);
        HookConfig config = new HookConfig(data);
        TextView text = new TextView(); View middle = new View(), panel = new View();
        middle.background = null; panel.id = 42; panel.res.name = "notification_panel";
        text.parent = middle; middle.parent = panel;
        measure("panel-text-background", () -> sink = AdaptiveTextHook.background(text, config));
    }
}
