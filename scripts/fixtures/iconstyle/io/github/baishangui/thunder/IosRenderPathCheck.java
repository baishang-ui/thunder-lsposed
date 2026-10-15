package io.github.baishangui.thunder;

import android.graphics.*;
import android.graphics.drawable.*;
import android.widget.ImageView;
import com.android.settingslib.graph.SignalDrawable;
import com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView;
import com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView;

public final class IosRenderPathCheck {
    static int checks;
    static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
    static void config(boolean active) throws Exception { IosStatusBarCheck.config(true,active?1:0); }
    static HookCallback.Param param(Object object,Object... args) { return StatusBarIconCheck.param(object,args); }
    public static class SignalContext extends android.content.Context {
        public final android.content.res.Resources resources=new android.content.res.Resources();
        @Override public android.content.res.Resources getResources(){return resources;}
    }
    public static class ThrowingCanvas extends Canvas {
        @Override public void drawPath(Path path,Paint paint) { throw new IllegalStateException("renderer failed"); }
    }
    public static class BatteryModel {
        public int level=75,charge=0,color=0xffeeeeee,fill=0xff34c759;
        public int getPowerLevel(){return level;} public int getChargeIconId(){return charge;}
        public int getTintColor(){return color;} public int getProgressColor(){return fill;}
    }
    public static class Charge { public boolean isVisible; }
    public static void main(String[] args) throws Throwable {
        ShadeHook.install("com.android.systemui","com.android.systemui",IosRenderPathCheck.class.getClassLoader()); config(true);
        HookCallback loader=Api102Runtime.callbacks.get("androidx.appcompat.content.res.AppCompatResources#getDrawable");
        var context=new SignalContext(); context.resources.full="com.android.systemui:drawable/stat_signal_wifi_signal_3_os17";
        var nativeIcon=new Drawable(); var load=param(null,context,1); load.setResult(nativeIcon); loader.after(load);
        check(load.getResult()==nativeIcon,"resource loader only records provenance");
        var wifi=new ImageView(); wifi.parent=new com.android.systemui.statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView(); wifi.drawable=nativeIcon;
        Canvas canvas=new Canvas(); IosStatusBarCheck.draw(wifi,canvas); check(!canvas.operations.isEmpty(),"loaded drawable survives mResource reset and still renders");
        var stacked=new VectorDrawable(); stacked.setBounds(0,0,48,40);
        context.resources.full="com.android.systemui:drawable/stat_signal_signal_stacked_primary_3";
        load=param(null,context,2); load.setResult(stacked); loader.after(load);
        check(load.getResult()==stacked,"Compose painter retains the native vector object");
        HookCallback stackedDraw=Api102Runtime.callbacks.get(VectorDrawable.class.getName()+"#draw");
        check(stackedDraw!=null,"native stacked vector has a drawing boundary without Compose hooks");
        canvas=new Canvas(); var stackedCall=new IosStatusBarCheck.DrawCall(stacked,canvas); stackedDraw.before(stackedCall);
        check(stackedCall.replaced && !canvas.operations.isEmpty() && canvas.depth==0,"stacked artwork paints without replacing painter input");
        stacked.setTintList(android.content.res.ColorStateList.valueOf(0xff123456));
        canvas=new Canvas(); stackedDraw.before(new IosStatusBarCheck.DrawCall(stacked,canvas));
        check(canvas.colors.contains(0xff123456),"Compose vector native tint reaches the custom paths");
        config(false); canvas=new Canvas(); stackedCall=new IosStatusBarCheck.DrawCall(stacked,canvas); stackedDraw.before(stackedCall);
        check(!stackedCall.replaced && canvas.operations.isEmpty(),"cached Compose painter switches back to native drawing"); config(true);
        int invalidations=stacked.invalidations; IosStatusBarHook.refresh(); check(stacked.invalidations>invalidations,"style toggle invalidates native Compose drawable callback");
        var untagged=new VectorDrawable(); canvas=new Canvas(); stackedCall=new IosStatusBarCheck.DrawCall(untagged,canvas); stackedDraw.before(stackedCall);
        check(!stackedCall.replaced && canvas.operations.isEmpty(),"unrelated vector drawables remain native");
        var signal=new SignalDrawable(); signal.setLevel(5<<8|3); var image=new ImageView(); image.drawable=signal; image.parent=new ModernStatusBarMobileView();
        signal.mForegroundPaint.setColor(0x64112233); signal.mForegroundPaint.setAlpha(100);
        canvas=new Canvas(); IosStatusBarCheck.draw(image,canvas); check(!canvas.operations.isEmpty() && canvas.alphas.contains(100),"dynamic native signal level and alpha read without changing object type");
        signal.setLevel(2<<16|5<<8|3); canvas=new Canvas(); IosStatusBarCheck.draw(image,canvas); check(canvas.operations.contains("text:!"),"dynamic connectivity warning preserved");
        signal.setLevel(9<<16|5<<8|3); canvas=new Canvas(); IosStatusBarCheck.draw(image,canvas); check(canvas.operations.isEmpty(),"unknown signal encoding falls back");
        var meter=new StatBatteryMeterView(); var model=new BatteryModel(); meter.batteryIcon=model;
        HookCallback battery=Api102Runtime.callbacks.get(meter.getClass().getName()+"#dispatchDraw");
        Object originalDrawable=meter.batteryStyleDrawable; Object originalStyle=meter.batteryIconStyle;
        for(int style:new int[]{1,3,4,5}) {
            meter.batteryIconStyle=new com.oplus.systemui.statusbar.pipeline.battery.ui.model.BatteryIconStyle(style);
            for(int level:new int[]{0,1,20,99,100}) {
                model.level=level; canvas=new Canvas(); battery.before(param(meter,canvas));
                check(canvas.operations.contains("text:"+level) && canvas.depth==0,"real battery level in existing meter cell: "+style+"/"+level);
                check(meter.batteryIcon==model && meter.batteryStyleDrawable==originalDrawable && meter.layout.width==37,"no battery model, drawable or layout replacement");
            }
        }
        model.charge=1; canvas=new Canvas(); battery.before(param(meter,canvas)); check(canvas.colors.contains(model.fill),"native battery fill retained during charge");
        for(int mode:new int[]{0,2}) {
            meter.batteryIconStyle=new com.oplus.systemui.statusbar.pipeline.battery.ui.model.BatteryIconStyle(mode);
            canvas=new Canvas(); battery.before(param(meter,canvas)); check(canvas.operations.isEmpty(),"hidden/percent-only battery mode stays native");
        }
        meter.batteryIconStyle=originalStyle;
        var reverse=new Charge(); reverse.isVisible=true; meter.batteryReverse=reverse;
        canvas=new Canvas(); battery.before(param(meter,canvas)); check(canvas.operations.isEmpty(),"reverse-charge badge retains native draw path"); meter.batteryReverse=null;
        meter.hidden=true; canvas=new Canvas(); battery.before(param(meter,canvas)); check(canvas.operations.isEmpty(),"hidden meter remains hidden"); meter.hidden=false;
        model.level=-1; canvas=new Canvas(); battery.before(param(meter,canvas)); check(canvas.operations.isEmpty(),"unknown battery state stays native"); model.level=75;
        config(false); canvas=new Canvas(); battery.before(param(meter,canvas)); check(canvas.operations.isEmpty(),"battery off restores original without binder re-entry"); config(true);
        image.drawable=new Drawable(); image.mResource=1; image.res.full="com.android.systemui:drawable/stat_sys_signal_5_bar_4_fully";
        var broken=new ThrowingCanvas(); var fallback=IosStatusBarCheck.draw(image,broken); check(!fallback.replaced && broken.depth==0,"canvas restored after renderer throws");
        canvas=new Canvas(); fallback=IosStatusBarCheck.draw(image,canvas); check(!fallback.replaced && canvas.operations.isEmpty(),"failed view is quarantined instead of repeatedly throwing");
        canvas=new Canvas(); IosStatusBarCheck.draw(wifi,canvas); check(!canvas.operations.isEmpty(),"one failed view does not disable healthy icons");
        check(image.drawable.getClass()==Drawable.class,"native caller can still use its exact expected drawable class");
        System.out.println("Verified "+checks+" render-state and failure fallback checks.");
    }
}
