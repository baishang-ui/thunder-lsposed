package io.github.baishangui.thunder;

import java.util.regex.Pattern;

/** Status semantics and resource matching. Artwork uses regular curves at Apple reference proportions, with companion Android states. */
final class IosStatusIcons {
    enum Kind { BLUETOOTH, ALARM, SILENT, VIBRATE, NFC, MOON, VPN, HOTSPOT, ROTATION,
        HEADPHONES, PERFORMANCE, AIRPLANE, LOCATION, WIFI, CELLULAR, BATTERY }
    record Symbol(Kind kind, int level, int maximum, int row) {
        Symbol(Kind kind,int level,int maximum) { this(kind,level,maximum,0); }
        Symbol(Kind kind) { this(kind, 4, 4); }
    }
    record Stroke(String path, float width, float scale, float x, float y, boolean evenOdd) { }
    static Stroke[] paths(Kind kind) { return IosIconAssets.paths(kind); }

    static Symbol slot(String slot, String resource) {
        if (slot == null) return null;
        return switch (slot) {
            case "bluetooth" -> new Symbol(Kind.BLUETOOTH);
            case "volume" -> volume(resource);
            case "nfc" -> new Symbol(Kind.NFC);
            case "high_performance" -> new Symbol(Kind.PERFORMANCE);
            case "alarm_clock" -> new Symbol(Kind.ALARM);
            case "zen" -> new Symbol(Kind.MOON);
            case "vpn" -> new Symbol(Kind.VPN);
            case "hotspot" -> new Symbol(Kind.HOTSPOT);
            case "rotate" -> new Symbol(Kind.ROTATION);
            case "headset" -> new Symbol(Kind.HEADPHONES);
            case "airplane" -> new Symbol(Kind.AIRPLANE);
            case "location" -> new Symbol(Kind.LOCATION);
            default -> null;
        };
    }
    private static Symbol volume(String resource) {
        if(resource==null)return null;
        String name=resource.substring(resource.lastIndexOf('/')+1);
        return switch(name) {
            case "stat_sys_ringer_vibrate", "stat_sys_ringer_vibrate_os17", "stat_sys_flavor_one_ringer_vibrate" -> new Symbol(Kind.VIBRATE);
            case "stat_sys_ringer_silent", "stat_sys_ringer_silent_os17" -> new Symbol(Kind.SILENT);
            default -> null;
        };
    }
    // Only plain signal assets. Error, roaming, no-service and carrier badges remain native.
    private static final Pattern WIFI = Pattern.compile("stat_signal_wifi_signal_([0-4])(?:_os17)?");
    private static final Pattern CELL = Pattern.compile("stat_sys_signal_(?:(?:soft_)?(?:(5_bar)_)?signal_|(5_bar)_)?([0-5])(?:_fully)?");
    private static final Pattern OPLUS_CELL = Pattern.compile("stat_signal_(?:signal_lte_single|soft_signal)_([0-4])(?:_os17)?");
    private static final Pattern STACKED = Pattern.compile("stat_signal_(?:lte_|soft_)?signal_stacked_(primary|secondary)_([0-4])");
    static Symbol signal(String resource, boolean wifi) {
        String prefix = "com.android.systemui:drawable/";
        if (!resource.startsWith(prefix)) return null;
        String name = resource.substring(prefix.length());
        var match = WIFI.matcher(name);
        if (wifi) return match.matches() ? new Symbol(Kind.WIFI,Integer.parseInt(match.group(1)),4) : null;
        match = CELL.matcher(name);
        if (match.matches()) {
            int max = match.group(1) == null && match.group(2) == null ? 4 : 5, level = Integer.parseInt(match.group(3));
            return level <= max ? new Symbol(Kind.CELLULAR,level,max) : null;
        }
        match = OPLUS_CELL.matcher(name);
        if(match.matches())return new Symbol(Kind.CELLULAR,Integer.parseInt(match.group(1)),4);
        match = STACKED.matcher(name);
        return match.matches() ? new Symbol(Kind.CELLULAR,Integer.parseInt(match.group(2)),4,match.group(1).equals("primary")?1:2) : null;
    }
    static boolean active(HookConfig config) { return config.enabled && config.statusIconStyle == 1; }
    private IosStatusIcons() { }
}
