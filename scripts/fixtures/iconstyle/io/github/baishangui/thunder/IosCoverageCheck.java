package io.github.baishangui.thunder;

/** Actual resource allowlist coverage; geometry is audited against C17 XML by Python. */
public final class IosCoverageCheck {
    public static void main(String[] args) {
        for (String name : new String[]{"stat_sys_airplane_mode", "stat_sys_alarm", "stat_sys_dnd",
                "stat_sys_data_bluetooth", "stat_sys_data_bluetooth_connected", "stat_sys_headset",
                "stat_sys_headset_mic", "stat_sys_high_performance", "stat_sys_location", "stat_sys_nfc",
                "stat_sys_ringer_silent", "stat_sys_ringer_vibrate", "stat_sys_vpn_ic", "stat_sys_data_saver",
                "stat_battery_horizontal_bg", "stat_battery_vertical_bg", "stat_bt_battery_black_0"})
            IosStatusBarCheck.check(IosStatusResources.replacement("com.android.systemui:drawable/"+name)!=0,"original C17 asset available: "+name);
        for (String name : new String[]{"stat_sys_hotspot", "stat_sys_cast", "stat_sys_rotate_portrait",
                "stat_sys_managed_profile_status", "stat_sys_sos", "ic_wifi_connecting", "ic_wifi_off"})
            IosStatusBarCheck.check(IosStatusResources.replacement("com.android.systemui:drawable/"+name)==0,"unprovided or incompatible resource remains native: "+name);
        for (int level=0;level<5;level++) {
            int primary=IosStatusResources.replacement("com.android.systemui:drawable/stat_signal_lte_signal_stacked_primary_"+level);
            int secondary=IosStatusResources.replacement("com.android.systemui:drawable/stat_signal_lte_signal_stacked_secondary_"+level);
            int wifi=IosStatusResources.replacement("com.android.systemui:drawable/stat_signal_wifi_signal_"+level+"_os17");
            IosStatusBarCheck.check(primary!=0 && secondary!=0 && primary!=secondary && wifi!=0,"native C17 row/state resources: "+level);
        }
        System.out.println("Verified C17-only allowlist, native fallbacks and dual-SIM state resources.");
    }
}
