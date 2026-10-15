package io.github.baishangui.thunder;

import java.util.LinkedHashSet;
import java.util.Set;

/** Stable configuration bits; slot names verified in the collected C17 framework resources. */
final class StatusBarIcons {
    static final int BLUETOOTH = 1, ALARM = 1 << 1, VOLUME = 1 << 2, ZEN = 1 << 3,
        VPN = 1 << 4, HOTSPOT = 1 << 5, ROTATE = 1 << 6, HEADSET = 1 << 7,
        NFC = 1 << 8, HIGH_PERFORMANCE = 1 << 9;
    static final int ALL = (1 << 10) - 1;
    private static final String[] SLOTS = {"bluetooth", "alarm_clock", "volume", "zen",
        "vpn", "hotspot", "rotate", "headset", "nfc", "high_performance"};

    static Set<String> hidden(int mask) {
        Set<String> slots = new LinkedHashSet<>();
        for (int i = 0; i < SLOTS.length; i++) if ((mask & (1 << i)) != 0) slots.add(SLOTS[i]);
        return slots;
    }
}
