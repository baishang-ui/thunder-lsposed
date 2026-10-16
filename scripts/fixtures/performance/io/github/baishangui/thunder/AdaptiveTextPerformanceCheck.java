package io.github.baishangui.thunder;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class AdaptiveTextPerformanceCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("PASS: " + message);
    }
    public static void main(String[] args) {
        Bundle data = new Bundle(); data.putBoolean("enabled", true);
        data.putBoolean("shade_tint", true); data.putInt("shade_color", 0xffffffff);
        HookConfig config = new HookConfig(data);
        TextView text = new TextView(); View middle = new View(), panel = new View();
        text.id = View.NO_ID; middle.id = View.NO_ID; middle.background = null;
        panel.id = 42; panel.res.name = "notification_panel";
        text.parent = middle; middle.parent = panel;
        for (int i = 0; i < 100; i++)
            checkBackground(text, config, 0xffffffff);
        check(panel.res.entryNameLookups == 1, "stable resource IDs are resolved once across repeated frames");
        panel.id = 43; panel.res.entryNames.put(43, "unrelated");
        check(AdaptiveTextHook.background(text, config) == null, "changed view ID is reevaluated");
        View replacement = new View(); replacement.id = 42; replacement.res.name = "unrelated";
        middle.parent = replacement;
        check(AdaptiveTextHook.background(text, config) == null, "same ID from different Resources does not reuse a panel match");
        middle.parent = panel; panel.id = 42;
        checkBackground(text, config, 0xffffffff);
        middle.background = new android.graphics.drawable.ColorDrawable(0xff000000);
        checkBackground(text, config, 0xff000000);
        middle.background = new android.graphics.drawable.ColorDrawable(0x558899aa);
        text.background = new android.graphics.drawable.ColorDrawable(0x66332211);
        checkBackground(text, config, AdaptiveTextColor.composite(0x66332211,
            AdaptiveTextColor.composite(0x558899aa, 0xffffffff)));
        text.parent = null;
        check(AdaptiveTextHook.background(text, config) == null, "reparenting and live backgrounds remain visible without stale hierarchy caching");
    }
    private static void checkBackground(TextView text, HookConfig config, int expected) {
        Integer actual = AdaptiveTextHook.background(text, config);
        if (actual == null || actual != expected) throw new AssertionError("Unexpected background: " + actual);
    }
}
