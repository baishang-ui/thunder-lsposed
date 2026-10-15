package com.oplus.systemui.statusbar.pipeline.battery.ui.binder;
import android.widget.*;
import com.oplus.systemui.statusbar.pipeline.battery.ui.view.*;
import com.oplus.systemui.statusbar.pipeline.battery.ui.viewmodel.*;
import com.oplus.systemui.statusbar.pipeline.battery.ui.model.*;
public class BatteryViewBinder {
 public static int lastStyle=-1,rebinds;public static final java.util.Map<ImageView,Integer> styles=new java.util.WeakHashMap<>();
 public static void bind$updateBatteryIconStyle(ImageView icon,StatBatteryMeterView meter,LocationBasedBatteryViewModel model,FrameLayout old,TextView percent,BatteryIconStyle style){
  lastStyle=style.iconStyle;styles.put(icon,style.iconStyle);rebinds++;
  meter.batteryStyleDrawable=style.iconStyle==1?new com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable():new android.graphics.drawable.Drawable();
  icon.layout.width=style.iconStyle==1?80:26;icon.layout.height=style.iconStyle==1?36:48;
  icon.setImageDrawable((android.graphics.drawable.Drawable)meter.batteryStyleDrawable);
 }
}
