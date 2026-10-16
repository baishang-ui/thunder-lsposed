package io.github.baishangui.thunder;

import java.util.Map;
import java.util.regex.Pattern;

/** Status semantics, including warning states; matching is restricted to audited SystemUI resources. */
final class IosStatusIcons {
    enum Kind { BLUETOOTH, ALARM, SILENT, VIBRATE, NFC, MOON, VPN, HOTSPOT, ROTATION,
        HEADPHONES, REVERSE_CHARGE, PERFORMANCE, AIRPLANE, LOCATION, WIFI, CELLULAR, BATTERY,
        CAST, ETHERNET, WORK, DATA_SAVER, TTY, CAMERA, MICROPHONE, RECORDING, STEALTH,
        ACTIVITY, ACTIVITY_IN, ACTIVITY_OUT, LABEL, NO_SERVICE }
    static final int WARNING=1, ROAMING=2, NO_VOICE=4, CONNECTING=8;
    record Symbol(Kind kind,int level,int maximum,int row,int flags,String label) {
        Symbol(Kind kind,int level,int maximum,int row) { this(kind,level,maximum,row,0,""); }
        Symbol(Kind kind,int level,int maximum) { this(kind,level,maximum,0); }
        Symbol(Kind kind) { this(kind,4,4); }
        Symbol marked(int flags) { return new Symbol(kind,level,maximum,row,flags,label); }
    }
    record Stroke(String path,float width,float scale,float x,float y,boolean evenOdd) { }
    static Stroke[] paths(Kind kind) { return IosIconAssets.paths(kind); }
    private static Symbol label(String text) { return new Symbol(Kind.LABEL,4,4,0,0,text); }
    private static final String PREFIX="com.android.systemui:drawable/";
    private static String name(String resource) {
        if(resource==null)return "";
        return resource.substring(resource.lastIndexOf('/')+1);
    }
    static Symbol slot(String slot,String resource) {
        if(slot==null)return null;
        // A slot can carry several distinct states (offline VPN, headset mic, landscape lock).
        Symbol exact=system(resource!=null&&resource.startsWith(PREFIX)?resource:PREFIX+name(resource));
        if(exact!=null)return exact;
        return switch(slot) {
            case "bluetooth" -> new Symbol(Kind.BLUETOOTH);
            case "nfc" -> new Symbol(Kind.NFC);
            case "high_performance" -> new Symbol(Kind.PERFORMANCE);
            case "alarm_clock" -> new Symbol(Kind.ALARM);
            case "zen", "zen_mode" -> new Symbol(Kind.MOON);
            case "vpn" -> new Symbol(Kind.VPN);
            case "hotspot" -> new Symbol(Kind.HOTSPOT);
            case "rotate" -> new Symbol(Kind.ROTATION);
            case "headset" -> new Symbol(Kind.HEADPHONES);
            case "airplane" -> new Symbol(Kind.AIRPLANE);
            case "location" -> new Symbol(Kind.LOCATION);
            case "cast", "connected_display" -> new Symbol(Kind.CAST);
            case "ethernet" -> new Symbol(Kind.ETHERNET);
            case "managed_profile" -> new Symbol(Kind.WORK);
            case "data_saver" -> new Symbol(Kind.DATA_SAVER);
            case "tty" -> new Symbol(Kind.TTY);
            case "speakerphone" -> label("SPK");
            case "mute" -> new Symbol(Kind.MICROPHONE).marked(WARNING);
            case "sensors_off", "stealth_mode" -> new Symbol(Kind.STEALTH);
            default -> null;
        };
    }
    private static final Pattern WIFI=Pattern.compile("stat_signal_wifi_signal_([0-4])(?:_os17)?");
    private static final Pattern WIFI_AOSP=Pattern.compile("ic_wifi_([0-3])(_error)?");
    private static final Pattern CELL=Pattern.compile("stat_sys_signal_(?:(?:soft_)?(?:(5_bar)_)?signal_|(5_bar)_)?([0-5])(?:_fully)?");
    private static final Pattern OPLUS_CELL=Pattern.compile("stat_signal_(?:signal_lte_single|soft_signal)_([0-4])(?:_os17)?");
    private static final Pattern STACKED=Pattern.compile("stat_signal_(?:lte_|soft_)?signal_stacked_(primary|secondary)_([0-4]|noservice)");
    private static final Pattern ROAM=Pattern.compile("stat_sys_signal_roaming_(5_bar_)?signal_([0-5])");
    private static final Pattern NO_VOICE_SIGNAL=Pattern.compile("stat_signal_(signal_novoice|single_novoice|signal_stacked_novoice|single_stacked_novoice(?:_secondary)?)_([0-4])(?:_os17)?");
    static Symbol signal(String resource,boolean wifi) {
        if(resource==null||!resource.startsWith(PREFIX))return null;
        String n=resource.substring(PREFIX.length());
        if(n.startsWith("stat_signal_")&&n.contains("activity_")) {
            int state=n.contains("inout")?3:n.contains("_in_")||n.endsWith("_in")?1:n.contains("_out_")||n.endsWith("_out")?2:0;
            return new Symbol(Kind.ACTIVITY,state,3);
        }
        if(wifi) {
            var m=WIFI.matcher(n);if(m.matches())return new Symbol(Kind.WIFI,Integer.parseInt(m.group(1)),4);
            m=WIFI_AOSP.matcher(n);if(m.matches())return new Symbol(Kind.WIFI,Integer.parseInt(m.group(1)),3).marked(m.group(2)!=null?WARNING:0);
            if(n.equals("ic_wifi_off"))return new Symbol(Kind.WIFI,0,4).marked(NO_VOICE);
            if(n.equals("ic_wifi_connecting"))return new Symbol(Kind.WIFI,1,4).marked(CONNECTING);
            return switch(n) {
                case "stat_signal_wifi_6" -> label("6");
                case "stat_signal_wifi_6e" -> label("6E");
                case "stat_signal_wifi_7" -> label("7");
                case "stat_signal_wifi_double", "stat_signal_wifi_double_os17" -> label("2×");
                case "stat_signal_wifi_h_passpoint" -> label("H");
                case "stat_signal_wifi_r_passpoint" -> label("R");
                case "stat_signal_wifi_passpoint" -> label("P");
                default -> null;
            };
        }
        var m=CELL.matcher(n);
        if(m.matches()) {
            int maximum=m.group(1)==null&&m.group(2)==null?4:5,level=Integer.parseInt(m.group(3));
            return level<=maximum?new Symbol(Kind.CELLULAR,level,maximum):null;
        }
        m=OPLUS_CELL.matcher(n);if(m.matches())return new Symbol(Kind.CELLULAR,Integer.parseInt(m.group(1)),4);
        m=STACKED.matcher(n);if(m.matches())return new Symbol(m.group(2).equals("noservice")?Kind.NO_SERVICE:Kind.CELLULAR,
            m.group(2).equals("noservice")?0:Integer.parseInt(m.group(2)),4,m.group(1).equals("primary")?1:2);
        m=ROAM.matcher(n);if(m.matches()) {
            int maximum=m.group(1)==null?4:5,level=Integer.parseInt(m.group(2));
            return level<=maximum?new Symbol(Kind.CELLULAR,level,maximum).marked(ROAMING):null;
        }
        m=NO_VOICE_SIGNAL.matcher(n);if(m.matches())return new Symbol(Kind.CELLULAR,Integer.parseInt(m.group(2)),4,
            m.group(1).contains("stacked")?(m.group(1).contains("secondary")?2:1):0).marked(NO_VOICE);
        return switch(n) {
            case "stat_signal_noservice_lte", "stat_signal_noservice_lte_os17", "stat_signal_soft_noservice", "stat_signal_soft_noservice_os17",
                "stat_signal_signal_null_lte", "stat_sys_signal_null" -> new Symbol(Kind.NO_SERVICE,0,4);
            case "stat_signal_roma_lte", "stat_signal_soft_roma_lte", "stat_sys_roaming", "stat_sys_roaming_updated" -> label("R");
            default -> network(n);
        };
    }
    private static final Map<String,String> NETWORK=Map.ofEntries(
        Map.entry("2g","2G"),Map.entry("3g","3G"),Map.entry("3gp","3G+"),Map.entry("4g","4G"),
        Map.entry("4gp","4G+"),Map.entry("4g_plus","4G+"),Map.entry("4_5g","4.5G"),Map.entry("4_5g_plus","4.5G+"),
        Map.entry("5g","5G"),Map.entry("5_dot_5g","5.5G"),Map.entry("5g_a","5G A"),Map.entry("5g_e","5G E"),
        Map.entry("5g_plus","5G+"),Map.entry("5g_plus_plus","5G++"),Map.entry("5g_uc","5G UC"),Map.entry("5g_uwb_global","5G UW"),
        Map.entry("e","E"),Map.entry("g","G"),Map.entry("h","H"),Map.entry("h_p","H+"),Map.entry("h_plus","H+"),
        Map.entry("lte","LTE"),Map.entry("lte_plus","LTE+"),Map.entry("tmo_4g_lte","4G LTE"),
        Map.entry("tmo_4g_plus_lte","4G+ LTE"),Map.entry("tmo_5g","5G"));
    private static Symbol network(String n) {
        String key;
        if(n.startsWith("stat_signal_connected_")) {
            key=n.substring("stat_signal_connected_".length()).replaceFirst("^soft_","").replaceFirst("^big_","")
                .replaceFirst("_lte_big$","").replaceFirst("_big$","");
        } else if(n.startsWith("stat_sys_data_fully_connected_"))key=n.substring("stat_sys_data_fully_connected_".length());
        else if(n.startsWith("stat_signal_vo")||n.startsWith("stat_signal_hd_")||n.equals("stat_signal_cu_no_call_hd")) {
            String text=n.contains("vowifi")?"Wi-Fi":n.contains("vo5g")?"Vo5G":"HD";
            if(n.contains("vo5g1_volte2"))text="5G¹ HD²";
            else if(n.contains("volte1_vo5g2"))text="HD¹ 5G²";
            else if(n.endsWith("_both"))text+="¹²";
            else if(n.endsWith("_sim1"))text+="¹";
            else if(n.endsWith("_sim2"))text+="²";
            return label(text);
        } else if(n.startsWith("stat_signal_letter_vowifi")) {
            return label(n.endsWith("sim1")?"Wi-Fi¹":n.endsWith("sim2")?"Wi-Fi²":n.endsWith("both")?"Wi-Fi¹²":"Wi-Fi");
        } else if(n.equals("ic_vowifi")||n.equals("ic_vowifi_calling"))return label(n.endsWith("calling")?"Wi-Fi☎":"Wi-Fi");
        else return null;
        String text=NETWORK.get(key);return text==null?null:label(text);
    }
    static Symbol system(String resource) {
        if(resource==null||!resource.startsWith(PREFIX))return null;
        Symbol s=signal(resource,true);if(s!=null)return s;
        s=signal(resource,false);if(s!=null)return s;
        String n=name(resource);
        if (n.startsWith("stat_battery_horizontal_") && IosBatteryResources.contains(n)) return new Symbol(Kind.BATTERY);
        return switch(n) {
            case "stat_charge_wireless_reverse" -> new Symbol(Kind.REVERSE_CHARGE);
            case "ic_activity_down" -> new Symbol(Kind.ACTIVITY_IN);
            case "ic_activity_up" -> new Symbol(Kind.ACTIVITY_OUT);
            case "stat_sys_airplane_mode" -> new Symbol(Kind.AIRPLANE);
            case "stat_sys_alarm" -> new Symbol(Kind.ALARM);
            case "stat_sys_bluetooth_acquiring_anim", "stat_sys_data_bluetooth_connected_ing" -> new Symbol(Kind.BLUETOOTH).marked(CONNECTING);
            case "stat_sys_data_bluetooth", "stat_sys_data_bluetooth_connected", "stat_sys_data_bluetooth_connected_stop" -> new Symbol(Kind.BLUETOOTH);
            case "stat_sys_branded_vpn", "stat_sys_vpn_ic" -> new Symbol(Kind.VPN);
            case "stat_sys_no_internet_branded_vpn", "stat_sys_no_internet_vpn_ic" -> new Symbol(Kind.VPN).marked(WARNING);
            case "stat_sys_cast", "stat_sys_connected_display" -> new Symbol(Kind.CAST);
            case "stat_sys_data_saver" -> new Symbol(Kind.DATA_SAVER);
            case "stat_sys_dnd" -> new Symbol(Kind.MOON);
            case "stat_sys_ethernet", "stat_sys_ethernet_fully" -> new Symbol(Kind.ETHERNET);
            case "stat_sys_headset", "stat_sys_headset_mic" -> new Symbol(Kind.HEADPHONES);
            case "stat_sys_high_performance" -> new Symbol(Kind.PERFORMANCE);
            case "stat_sys_hotspot" -> new Symbol(Kind.HOTSPOT);
            case "stat_sys_location" -> new Symbol(Kind.LOCATION);
            case "stat_sys_logging" -> new Symbol(Kind.RECORDING);
            case "stat_sys_managed_profile_status" -> new Symbol(Kind.WORK);
            case "stat_sys_mic_none" -> new Symbol(Kind.MICROPHONE).marked(WARNING);
            case "stat_sys_camera" -> new Symbol(Kind.CAMERA);
            case "stat_sys_nfc" -> new Symbol(Kind.NFC);
            case "stat_sys_ringer_silent" -> new Symbol(Kind.SILENT);
            case "stat_sys_ringer_vibrate", "stat_sys_flavor_one_ringer_vibrate" -> new Symbol(Kind.VIBRATE);
            case "stat_sys_rotate_landscape" -> new Symbol(Kind.ROTATION,1,1);
            case "stat_sys_rotate_portrait" -> new Symbol(Kind.ROTATION);
            case "stat_sys_sos" -> label("SOS");
            case "stat_sys_tool_block_banner_on" -> new Symbol(Kind.STEALTH);
            case "stat_sys_tty_mode" -> new Symbol(Kind.TTY);
            case "stat_stealth_mode" -> new Symbol(Kind.STEALTH);
            default -> null;
        };
    }
    static boolean active(HookConfig config) { return config.enabled&&config.statusIconStyle==1; }
    private IosStatusIcons() { }
}
