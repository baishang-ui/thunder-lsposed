package io.github.baishangui.thunder;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.TypedValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Standalone, name and target-package constrained flow-cloud and notification overrides. */
final class ResourceOverrides {
    private static final Map<Resources, Map<Integer,String>> names = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String,Integer> CLOUD = new HashMap<>();
    static {
        CLOUD.put("capsule_background_color", 0xFF000000);
        CLOUD.put("capsule_moon_dark_color", 0xFF000000);
        CLOUD.put("capsule_moon_light_color", 0xFF000000);
        CLOUD.put("capsule_stroke_default_color", 0x3AFFFFFF);
        CLOUD.put("card_background_blend_color", 0x2A585858);
        CLOUD.put("card_background_color_above_blur", 0x00000000);
        CLOUD.put("card_background_color_no_blur", 0x50282828);
        CLOUD.put("card_background_mix_color", 0x20282828);
        CLOUD.put("cardview_dark_background", 0x20282828);
        CLOUD.put("cardview_light_background", 0x50282828);
        CLOUD.put("card_background_gradient_bottom_color", 0x40000000);
        CLOUD.put("card_background_gradient_middle_color", 0x40000000);
        CLOUD.put("card_background_gradient_top_color", 0x40000000);
        CLOUD.put("coui_color_primary_text_blue_dark", 0xFFFFA800);
        CLOUD.put("coui_color_primary_on_popup_blue_dark", 0xFFFFA800);
    }
    static void install() {
        for (String getter : new String[]{"getDimension", "getDimensionPixelSize", "getDimensionPixelOffset"}) {
            Api102Runtime.findAndHookMethod(Resources.class, getter, int.class, new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    if (p.hasThrowable()) return;
                    try {
                        Resources r=(Resources)p.thisObject;
                        Integer dp=dimension(name(r,(Integer)p.args[0]), ShadeHook.snapshot());
                        if(dp==null)return;
                        float px=dp*r.getDisplayMetrics().density;
                        if ("getDimension".equals(getter)) p.setResult(px);
                        else if ("getDimensionPixelOffset".equals(getter)) p.setResult((int)px);
                        else p.setResult(Math.round(px));
                        ShadeHook.resourceHit("resources");
                    } catch (Resources.NotFoundException ignored) { }
                }
            });
        }
        for(String getter:new String[]{"getColor","getColorStateList"}) {
            HookCallback callback=new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    if(p.hasThrowable())return;
                    try {
                        Integer color=color(name((Resources)p.thisObject,(Integer)p.args[0]),ShadeHook.snapshot());
                        if(color!=null) { p.setResult("getColor".equals(getter)?color:ColorStateList.valueOf(color));ShadeHook.resourceHit("cloud"); }
                    } catch(Resources.NotFoundException ignored) { }
                }
            };
            Api102Runtime.findAndHookMethod(Resources.class,getter,int.class,callback);
            Api102Runtime.findAndHookMethod(Resources.class,getter,int.class,Resources.Theme.class,callback);
        }
        Api102Runtime.findAndHookMethod(Resources.class,"getValue",int.class,TypedValue.class,boolean.class,new HookCallback() {
            @Override protected void afterHookedMethod(HookCallback.Param p) {
                if(p.hasThrowable())return;
                try {
                    String resource=name((Resources)p.thisObject,(Integer)p.args[0]);
                    Integer c=color(resource,ShadeHook.snapshot());
                    Integer dp=dimension(resource,ShadeHook.snapshot());
                    if(c!=null||dp!=null) {
                        TypedValue out=(TypedValue)p.args[1];out.string=null;
                        out.type=c!=null?TypedValue.TYPE_INT_COLOR_ARGB8:TypedValue.TYPE_DIMENSION;
                        out.data=c!=null?c:((dp << TypedValue.COMPLEX_MANTISSA_SHIFT) | TypedValue.COMPLEX_UNIT_DIP);
                        ShadeHook.resourceHit(c!=null?"cloud":"resources");
                    }
                } catch(Resources.NotFoundException ignored) { }
            }
        });
        // Layout XML often resolves through TypedArray instead of Resources.getColor.
        for(String getter:new String[]{"getColor","getColorStateList","getDimension","getDimensionPixelSize","getDimensionPixelOffset"}) {
            HookCallback callback=new HookCallback() {
                @Override protected void afterHookedMethod(HookCallback.Param p) {
                    if(p.hasThrowable()||!ShadeHook.snapshot().enabled)return;
                    TypedArray array=(TypedArray)p.thisObject;int id=array.getResourceId((Integer)p.args[0],0);
                    if(id==0)return;
                    try {
                        String resource=name(array.getResources(),id);
                        if(getter.startsWith("getColor")) {
                            Integer c=color(resource,ShadeHook.snapshot());
                            if(c!=null) {p.setResult("getColor".equals(getter)?c:ColorStateList.valueOf(c));ShadeHook.resourceHit("cloud-xml");}
                        } else {
                            Integer dp=dimension(resource,ShadeHook.snapshot());if(dp==null)return;
                            float px=dp*array.getResources().getDisplayMetrics().density;
                            if("getDimension".equals(getter))p.setResult(px);
                            else if("getDimensionPixelOffset".equals(getter))p.setResult((int)px);
                            else p.setResult(Math.round(px));
                            ShadeHook.resourceHit("resources-xml");
                        }
                    } catch(Resources.NotFoundException ignored) { }
                }
            };
            if("getColorStateList".equals(getter))Api102Runtime.findAndHookMethod(TypedArray.class,getter,int.class,callback);
            else Api102Runtime.findAndHookMethod(TypedArray.class,getter,int.class,"getDimension".equals(getter)?float.class:int.class,callback);
        }
    }

    private static String name(Resources resources,int id) {
        synchronized(names) {
            Map<Integer,String> cache=names.computeIfAbsent(resources,k->new HashMap<>());
            String value=cache.get(id);
            if(value==null) { value=resources.getResourceName(id);cache.put(id,value); }
            return value;
        }
    }
    static Integer dimension(String name,HookConfig c) {
        if(!c.enabled)return null;
        if(c.margins && ("com.android.systemui:dimen/notification_side_paddings_single".equals(name)
                || "com.android.systemui:dimen/notification_side_paddings_dual".equals(name)
                || "com.android.systemui:dimen/notification_side_paddings_split".equals(name))) return c.margin;
        if(c.cloud && name.startsWith("com.oplus.systemui.plugins:dimen/")) {
            String key=name.substring("com.oplus.systemui.plugins:dimen/".length());
            if(c.cloudHeightEnabled && (key.equals("capsule_height") || key.equals("capsule_max_height_size")
                || key.equals("capsule_max_height_size_high") || key.equals("capsule_layout_max_height")
                || key.equals("capsule_layout_max_height_high") || key.equals("capsule_layout_max_height_small")))return c.cloudHeight;
            if(c.cloudCornerEnabled && (key.equals("capsule_corner_radius") || key.equals("capsule_card_corner_radius")
                || key.equals("round_corner_radius_fluid_cloud") || key.equals("round_corner_radius_fluid_cloud_lite")))return c.cloudCorner;
            if(key.equals("capsule_layout_max_height"))return 34;
        }
        return null;
    }
    static Integer color(String name,HookConfig c) {
        if(!c.enabled||!c.cloud||!name.startsWith("com.oplus.systemui.plugins:color/"))return null;
        String key=name.substring("com.oplus.systemui.plugins:color/".length());
        if(c.cloudArea.tint && CLOUD.containsKey(key) && (key.contains("background") || key.equals("cardview_dark_background")
                || key.equals("cardview_light_background") || key.equals("capsule_moon_dark_color") || key.equals("capsule_moon_light_color")))return c.cloudArea.color;
        return CLOUD.get(key);
    }
}
