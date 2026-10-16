package io.github.baishangui.thunder;

/** Audited C17 horizontal battery resource branches; the system owns level and charging. */
final class IosBatteryResources {
    static boolean contains(String name) {
        return switch (name) {
            case "stat_battery_horizontal_bg", "stat_battery_horizontal_frame",
                "stat_battery_horizontal_progress", "stat_battery_horizontal_progress_bg",
                "stat_battery_horizontal_percent_out_bg", "stat_battery_horizontal_percent_out_no_padding_bg",
                "stat_battery_horizontal_percent_out_progress", "stat_battery_horizontal_percent_out_no_padding_progress",
                "stat_battery_horizontal_percent_out_no_padding_progress_bg" -> true;
            default -> false;
        };
    }
    private IosBatteryResources() { }
}
