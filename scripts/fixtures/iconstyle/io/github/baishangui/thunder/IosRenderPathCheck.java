package io.github.baishangui.thunder;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.FrameLayout;
import com.android.settingslib.graph.SignalDrawable;
import com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView;
import com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable;

public final class IosRenderPathCheck {
    static int failures,checks;
    static void check(boolean value,String message){checks++;System.out.println((value?"PASS: ":"FAIL: ")+message);if(!value)failures++;}
    static void config(boolean active)throws Exception {
        Bundle b=new Bundle();b.putBoolean("enabled",active);b.putInt("status_bar_icon_style",1);
        var f=ShadeHook.class.getDeclaredField("config");f.setAccessible(true);f.set(null,new HookConfig(b));
    }
    static HookCallback.Param param(Object self,Object... args){return StatusBarIconCheck.param(self,args);}
    static final class SignalContext extends Context {
        final android.content.res.Resources resources=new android.content.res.Resources();
        @Override public android.content.res.Resources getResources(){return resources;}
    }
    public static void main(String[] args)throws Throwable {
        ShadeHook.install("com.android.systemui","com.android.systemui",IosRenderPathCheck.class.getClassLoader());config(true);
        for(String[] state:new String[][]{{"bluetooth","stat_sys_data_bluetooth_connected"},{"volume","stat_sys_ringer_silent"},
                {"volume","stat_sys_ringer_vibrate"},{"nfc","stat_sys_nfc"},{"high_performance","stat_sys_high_performance"}}) {
            var symbol=IosStatusIcons.slot(state[0],state[1]);
            check(symbol!=null && IosStatusIcons.paths(symbol.kind()).length>0,"mapped status has drawable geometry: "+state[0]+"/"+state[1]);
        }
        var silent=IosStatusIcons.slot("volume","stat_sys_ringer_silent");
        var vibrate=IosStatusIcons.slot("volume","stat_sys_ringer_vibrate");
        check(silent!=null && vibrate!=null && silent.kind()!=vibrate.kind(),"silent and vibrate retain different meanings");
        var wifiGlyph=new IosIconDrawable(new IosStatusIcons.Symbol(IosStatusIcons.Kind.WIFI));wifiGlyph.setBounds(0,0,24,24);
        Canvas wifiCanvas=new Canvas();wifiGlyph.draw(wifiCanvas);
        float wifiLeft=wifiCanvas.pathBounds.stream().map(x->x[0]).min(Float::compare).orElse(0f);
        check(Math.abs(wifiLeft-2)<.4f,"Wi-Fi published silhouette retains its reference width without a second scale");
        var resourceHook=Api102Runtime.callbacks.get(ImageView.class.getName()+"#setImageResource");
        for(String name:new String[]{"stat_signal_signal_lte_single_3_os17","stat_signal_soft_signal_2_os17"}) {
            ImageView image=new ImageView();image.parent=new ModernStatusBarMobileView();image.drawable=new Drawable();
            image.res.full="com.android.systemui:drawable/"+name;resourceHook.after(param(image,15));
            check(image.drawable instanceof IosIconDrawable.Replacement,"ColorOS independent signal resource replaced: "+name);
        }
        HookCallback scope=Api102Runtime.callbacks.get("com.android.systemui.statusbar.pipeline.shared.ui.composable.OplusStackedMobileIconKt#OplusStackedMobileIcon");
        HookCallback load=Api102Runtime.callbacks.get("androidx.appcompat.content.res.AppCompatResources#getDrawable");
        check(scope!=null && load!=null,"real C17 Compose stacked drawable loader is intercepted");
        if(scope!=null && load!=null) {
            SignalContext ctx=new SignalContext();ctx.resources.full="com.android.systemui:drawable/stat_signal_signal_stacked_primary_3";
            Drawable original=new Drawable();HookCallback.Param s=param(null);scope.before(s);
            var p=param(null,ctx,1);p.setResult(original);load.after(p);Drawable primary=(Drawable)p.getResult();
            ctx.resources.full="com.android.systemui:drawable/stat_signal_signal_stacked_secondary_2";
            p=param(null,ctx,2);p.setResult(new Drawable());load.after(p);Drawable secondary=(Drawable)p.getResult();
            check(primary instanceof IosIconDrawable.Replacement && secondary instanceof IosIconDrawable.Replacement,"both actual Compose layers receive replacement artwork");
            int stackedPixels=Math.round(20*ctx.resources.getDisplayMetrics().density);
            check(primary.getIntrinsicWidth()==stackedPixels && primary.getIntrinsicHeight()==stackedPixels,
                "Compose painter measures stacked signals at 20 dp instead of native 19 by 16 dp");
            primary.setBounds(0,0,24,24);secondary.setBounds(0,0,24,24);
            Canvas first=new Canvas(),second=new Canvas();primary.draw(first);secondary.draw(second);
            float maxTop=first.pathBounds.stream().map(x->x[3]).max(Float::compare).orElse(100f);
            float minBottom=second.pathBounds.stream().map(x->x[1]).min(Float::compare).orElse(0f);
            check(maxTop<minBottom,"primary and secondary occupy separate rows in the same overlaid Compose viewport");
            ctx.resources.full="com.android.systemui:drawable/stat_signal_signal_stacked_primary_noservice";
            p=param(null,ctx,3);p.setResult(original);load.after(p);check(p.getResult()==original,"stacked no-service badge remains native");
            scope.after(s);
            ctx.resources.full="com.android.systemui:drawable/stat_signal_signal_stacked_primary_3";
            p=param(null,ctx,4);p.setResult(original);load.after(p);check(p.getResult()==original,"same resource outside status Compose is unchanged");
            config(false);primary.draw(new Canvas());check(original.draws==1,"cached Compose painter restores original drawing when disabled");
            check(primary.getIntrinsicWidth()==original.getIntrinsicWidth() && primary.getIntrinsicHeight()==original.getIntrinsicHeight(),"Compose native intrinsic metrics restored when disabled");config(true);
        }
        HookCallback dynamic=Api102Runtime.callbacks.get(SignalDrawable.class.getName()+"#draw");
        check(dynamic!=null,"direct SignalDrawable path is intercepted");
        if(dynamic!=null) {
            var icon=new SignalDrawable();var image=new ImageView();image.parent=new ModernStatusBarMobileView();icon.setCallback(image);
            icon.setLevel(5<<8|3);icon.setBounds(0,0,24,24);Canvas c=new Canvas();dynamic.before(param(icon,c));
            check(!c.operations.isEmpty() && c.depth==0,"separate cellular direct drawable renders custom signal with native level");
            icon.mForegroundPaint.setColor(0x64112233);icon.mForegroundPaint.setAlpha(100);
            c=new Canvas();dynamic.before(param(icon,c));check(c.alphas.contains(100),"native cellular alpha is applied once during status bar fades");
            icon.setLevel(2<<16|5<<8|3);c=new Canvas();dynamic.before(param(icon,c));check(c.operations.isEmpty(),"dynamic warning state stays native");
            icon.setLevel(5<<8|3);image.parent=null;c=new Canvas();dynamic.before(param(icon,c));check(c.operations.isEmpty(),"dynamic signals outside status bar stay native");
        }
        ImageView wifi=new ImageView();wifi.drawable=new IosIconDrawable.Replacement(new Drawable(),new IosStatusIcons.Symbol(IosStatusIcons.Kind.WIFI));
        wifi.layout=new FrameLayout.LayoutParams(37,21,85);IosStatusBarMetrics.observe(wifi);
        check(wifi.getScaleType()==ImageView.ScaleType.FIT_CENTER,"Wi-Fi no longer inherits FIT_END bottom alignment");
        check((((FrameLayout.LayoutParams)wifi.layout).gravity&112)==16,"signal view aligns to vertical center within full-height parent");
        config(false);IosStatusBarMetrics.refresh();
        check(wifi.getScaleType()==ImageView.ScaleType.FIT_END && ((FrameLayout.LayoutParams)wifi.layout).gravity==85,"original image alignment restored on disable");config(true);
        var clock=new com.android.systemui.statusbar.policy.Clock();clock.mShowSeconds=true;
        HookCallback measure=Api102Runtime.callbacks.get(clock.getClass().getName()+"#onMeasure");
        check(measure!=null,"clock font changes happen before C17 computes its fixed seconds width");
        if(measure!=null){
            measure.before(param(clock,0,0));
            check(clock.mSecondsFixedWidth==0,"font change invalidates native fixed-width cache");
            int ws=android.view.View.MeasureSpec.makeMeasureSpec(120,android.view.View.MeasureSpec.AT_MOST);
            int hs=android.view.View.MeasureSpec.makeMeasureSpec(40,android.view.View.MeasureSpec.EXACTLY);
            measure.before(param(clock,ws,hs));
            check(clock.computeSecondsFixedLayoutWidth()<=120 && clock.getPaint().getFontMetricsInt().bottom-clock.getPaint().getFontMetricsInt().top<=40,
                "entire seconds clock fits parent width and height at enlarged density");
            clock.text="上午 11:59:59";measure.before(param(clock,ws,hs));
            check(clock.computeSecondsFixedLayoutWidth()<=120,"localized AM/PM text also fits");
            int changes=clock.fontChanges;measure.before(param(clock,ws,hs));
            check(clock.fontChanges==changes,"repeated measurement does not alternate large and fitted fonts or request layout again");
            config(false);IosStatusBarMetrics.refresh();
            check(clock.getTextSize()==14 && clock.mSecondsFixedWidth==0,"restoring native font also resets seconds width");config(true);
        }
        var battery=new HorizontalBatteryContentDrawable();battery.setBounds(0,0,320,180);battery.rect=new RectF(80,60,240,120);battery.noPercentRectF=battery.rect;
        HookCallback drawBattery=Api102Runtime.callbacks.get(battery.getClass().getName()+"#draw");
        Canvas normal=new Canvas();drawBattery.before(param(battery,normal));float[] body=normal.pathBounds.get(0);
        check(body[2]-body[0]>280,"battery uses full drawable bounds instead of vendor inset progress rect");
        battery.paint.setAlpha(100);Canvas faded=new Canvas();drawBattery.before(param(battery,faded));
        check(faded.alphas.get(1)==50,"battery terminal keeps half opacity during native fades, matching preview");
        battery.paint.setAlpha(255);
        battery.charge=1;Canvas charging=new Canvas();drawBattery.before(param(battery,charging));float[] charged=charging.pathBounds.get(0);
        check(Math.abs((body[2]-body[0])-(charged[2]-charged[0]))<.1f,"charging mark does not shrink the battery body");
        if(failures>0)throw new AssertionError(failures+" real-render-path regressions failed");
        System.out.println("Verified "+checks+" render-path checks; phone pixels still require device validation.");
    }
}
