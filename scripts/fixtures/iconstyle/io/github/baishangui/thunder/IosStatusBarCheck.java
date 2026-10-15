package io.github.baishangui.thunder;

import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.ImageView;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView;
import com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView;
import com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable;

public final class IosStatusBarCheck {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static void config(boolean enabled,int style)throws Exception {
        Bundle b=new Bundle();b.putBoolean("enabled",enabled);b.putInt("status_bar_icon_style",style);
        var field=ShadeHook.class.getDeclaredField("config");field.setAccessible(true);field.set(null,new HookConfig(b));
    }
    private static HookCallback.Param param(Object object,Object... args) {return StatusBarIconCheck.param(object,args);}
    public static final class NativeIcon {public Icon icon=new Icon();}
    public static void main(String[] args)throws Throwable {
        ClassLoader loader=IosStatusBarCheck.class.getClassLoader();
        ShadeHook.install("com.android.systemui","com.android.systemui",loader);
        IosStatusBarMetrics.install(loader);IosSignalStackHook.install(loader);IosStatusBarHook.installBatteryLabels(loader);IosStatusBarHook.installSymbols(loader);IosStatusBarHook.installSignals();IosStatusBarHook.installBattery(loader);
        config(true,1);
        HookCallback symbols=Api102Runtime.callbacks.get(StatusBarIconView.class.getName()+"#getIcon");
        StatusBarIconView view=new StatusBarIconView();NativeIcon source=new NativeIcon();
        Drawable nativeIcon=new Drawable();HookCallback.Param p=param(view,source);p.setResult(nativeIcon);symbols.after(p);
        check(p.getResult() instanceof IosIconDrawable.Replacement,"system slot is wrapped");
        Drawable replacement=(Drawable)p.getResult();replacement.setBounds(0,0,37,21);
        check(replacement.getIntrinsicWidth()==37 && replacement.getIntrinsicHeight()==21,"native layout metrics preserved");
        Canvas c=new Canvas();replacement.draw(c);check(!c.operations.isEmpty() && nativeIcon.draws==0 && c.depth==0,"iOS geometry drawn with balanced canvas");
        replacement.setAlpha(100);ColorFilter filter=new ColorFilter();replacement.setColorFilter(filter);replacement.setTintList(ColorStateList.valueOf(0xff112233));
        check(nativeIcon.getAlpha()==100 && nativeIcon.getColorFilter()==filter,"native alpha and filter preserved");
        c=new Canvas();replacement.draw(c);check(c.colors.contains(0xff112233),"system tint reaches iOS paths");
        config(true,0);replacement.draw(new Canvas());check(nativeIcon.draws==1,"system style restores original drawable");
        config(false,1);replacement.draw(new Canvas());check(nativeIcon.draws==2,"module switch restores original drawable");
        config(true,1);
        view.mNotification=new Object();p=param(view,source);p.setResult(nativeIcon);symbols.after(p);check(p.getResult()==nativeIcon,"notification app identity unchanged");
        view.mNotification=null;source.icon.pkg="other";p=param(view,source);p.setResult(nativeIcon);symbols.after(p);check(p.getResult()==nativeIcon,"foreign resource untouched");
        source.icon.pkg="com.android.systemui";view.mSlot="unknown";p=param(view,source);p.setResult(nativeIcon);symbols.after(p);check(p.getResult()==nativeIcon,"unknown slot untouched");
        HookCallback signals=Api102Runtime.callbacks.get(ImageView.class.getName()+"#setImageResource");
        ImageView image=new ImageView();image.drawable=new Drawable();image.parent=new ModernStatusBarWifiView();image.res.full="com.android.systemui:drawable/stat_signal_wifi_signal_2_os17";
        signals.after(param(image,1));check(image.drawable instanceof IosIconDrawable.Replacement,"Wi-Fi status asset wrapped inside signal view");
        image.parent=null;image.drawable=nativeIcon;signals.after(param(image,1));check(image.drawable==nativeIcon,"same asset outside status bar untouched");
        image.parent=new ModernStatusBarMobileView();image.res.full="com.android.systemui:drawable/stat_sys_signal_5_bar_4_fully";signals.after(param(image,2));check(image.drawable instanceof IosIconDrawable.Replacement,"five-level cellular resource wrapped");
        image.res.full="com.android.systemui:drawable/stat_sys_signal_roaming_signal_4";image.drawable=nativeIcon;signals.after(param(image,3));check(image.drawable==nativeIcon,"roaming warning remains native");
        image.parent=new com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView();image.res.full="com.android.systemui:drawable/stat_sys_alarm";image.drawable=nativeIcon;signals.after(param(image,4));check(image.drawable instanceof IosIconDrawable.Replacement,"modern bindable status slot wrapped");
        int before=replacement.invalidations;IosStatusBarHook.refresh();check(replacement.invalidations>before,"live settings invalidate existing replacement");
        HookCallback battery=Api102Runtime.callbacks.get(HorizontalBatteryContentDrawable.class.getName()+"#draw");
        HorizontalBatteryContentDrawable cell=new HorizontalBatteryContentDrawable();cell.level=18;cell.isShowPercentIn=true;cell.charge=1;
        c=new Canvas();p=param(cell,c);battery.before(p);check(c.operations.contains("text:18") && c.operations.stream().anyMatch(value->value.startsWith("path:")) && c.depth==0,"battery percentage and charge mark drawn");
        check(c.colors.contains(0xff34c759),"battery uses native progress color");
        cell.isShowPercentIn=false;
        for(int level:new int[]{0,1,99,100}) {
            cell.level=level;c=new Canvas();p=param(cell,c);battery.before(p);
            check(c.operations.contains("text:"+level) && c.depth==0,"percentage always internal, native percentage off, level="+level);
        }
        // Solid battery: all body paint is filled and no clipped inset/progress split survives.
        cell.charge=0;
        for(int level:new int[]{0,1,20,75,99,100}) {
            cell.level=level;c=new Canvas();battery.before(param(cell,c));
            check(c.styles.stream().allMatch(style->style==Paint.Style.FILL) && c.clips==0,"solid silhouette without outline or inset, level="+level);
            Rect bounds=new Rect();Paint digits=new Paint();digits.setTextSize(c.textSize);
            String number=Integer.toString(level);digits.getTextBounds(number,0,number.length(),bounds);
            check(c.textAlign==Paint.Align.LEFT && Math.abs(c.textX+(bounds.left+bounds.right)/2f-12.25f)<.01f
                && Math.abs(c.textY+(bounds.top+bounds.bottom)/2f-12)<.01f,"digit ink bounds centered horizontally and vertically, level="+level);
        }
        ImageView sized=new ImageView();sized.drawable=replacement;
        IosStatusBarMetrics.observe(sized);
        check(sized.layout.width==55 && sized.layout.height==55,"status icons enlarged with density");
        var clock=new com.android.systemui.statusbar.policy.Clock();var originalFace=clock.getTypeface();
        HookCallback measure=Api102Runtime.callbacks.get(clock.getClass().getName()+"#onMeasure");
        measure.before(param(clock,0,0));check(clock.getTextSize()==46.75f,"status clock enlarged to 17 dp");
        config(true,0);IosStatusBarHook.refresh();
        check(sized.layout.width==37 && sized.layout.height==21 && clock.getTextSize()==14 && clock.getTypeface()==originalFace,"native icon size and clock font restored");
        config(true,1);IosStatusBarHook.refresh();check(sized.layout.width==55 && clock.getTextSize()==46.75f,"enabling style updates existing views");
        var outside=new android.widget.TextView();var meter=new com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView();
        HookCallback label=Api102Runtime.callbacks.get("com.oplus.systemui.statusbar.pipeline.battery.ui.binder.BatteryViewBinder#bind$updatePercentOutView");
        label.after(param(null,outside,meter,new Object()));check(outside.getVisibility()==android.view.View.GONE,"external percentage suppressed only for horizontal iOS battery");
        config(true,0);IosStatusBarHook.refresh();check(outside.getVisibility()==android.view.View.VISIBLE,"external percentage restored with native style");
        config(true,1);meter.batteryStyleDrawable=new Drawable();IosStatusBarHook.refresh();check(outside.getVisibility()==android.view.View.VISIBLE,"vertical or circular battery retains outside percentage");
        var flow=new kotlinx.coroutines.flow.StateFlowImpl(true);
        var readonly=new kotlinx.coroutines.flow.ReadonlyStateFlow(flow);
        var interactor=new com.oplus.systemui.statusbar.pipeline.mobile.domain.interactor.OplusMobileIconsInteractorExImpl(readonly);
        HookCallback getter=Api102Runtime.callbacks.get(interactor.getClass().getName()+"#isStackableMode");
        p=param(interactor);p.setResult(readonly);getter.after(p);
        check(Boolean.FALSE.equals(flow.getValue()),"separate mode drives native signal flow");
        Bundle stack=new Bundle();stack.putBoolean("enabled",true);stack.putInt("status_bar_icon_style",1);stack.putBoolean("status_bar_stack_signals",true);
        var field=ShadeHook.class.getDeclaredField("config");field.setAccessible(true);field.set(null,new HookConfig(stack));IosStatusBarHook.refresh();
        check(Boolean.TRUE.equals(flow.getValue()),"stack selection updates already observed flow");
        HookCallback setter=Api102Runtime.callbacks.get(flow.getClass().getName()+"#setValue");
        p=param(flow,false);setter.before(p);flow.setValue(p.args[0]);check(Boolean.TRUE.equals(flow.getValue()),"native upstream update retains selected presentation");
        config(false,1);IosStatusBarHook.refresh();check(Boolean.FALSE.equals(flow.getValue()),"module off restores latest native signal mode");
        var unrelated=new kotlinx.coroutines.flow.StateFlowImpl(true);p=param(unrelated,false);setter.before(p);check(Boolean.FALSE.equals(p.args[0]),"unrelated state flow untouched");
        config(true,1);
        cell.level=-1;c=new Canvas();p=param(cell,c);battery.before(p);check(c.operations.isEmpty(),"unknown battery state uses native renderer");
        config(false,1);cell.level=90;c=new Canvas();p=param(cell,c);battery.before(p);check(c.operations.isEmpty(),"disabled module leaves battery native");
        for(var kind:IosStatusIcons.Kind.values()) {
            if(kind!=IosStatusIcons.Kind.BATTERY && IosStatusIcons.paths(kind).length==0)continue;
            var icon=new IosIconDrawable(new IosStatusIcons.Symbol(kind,kind==IosStatusIcons.Kind.BATTERY?75:4,kind==IosStatusIcons.Kind.BATTERY?100:4));
            icon.setBounds(0,0,48,48);c=new Canvas();icon.draw(c);check(!c.operations.isEmpty() && c.depth==0,"preview geometry exists: "+kind);
        }
        System.out.println("Verified "+checks+" iOS icon checks; device rendering remains unverified.");
    }
}
