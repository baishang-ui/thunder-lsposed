package io.github.baishangui.thunder;

import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.widget.ImageView;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView;
import android.os.Bundle;

public final class IosStatusBarCheck {
    static int checks;
    static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
    static HookCallback.Param param(Object object,Object... args) { return StatusBarIconCheck.param(object,args); }
    public static final class NativeIcon { public Icon icon=new Icon(); }
    static void config(boolean enabled,int style) throws Exception {
        Bundle b=new Bundle(); b.putBoolean("enabled",enabled); b.putInt("status_bar_icon_style",style);
        var field=ShadeHook.class.getDeclaredField("config"); field.setAccessible(true); field.set(null,new HookConfig(b));
    }
    static final class DrawCall extends HookCallback.Param {
        boolean replaced;
        DrawCall(Object object,Object... arguments) { thisObject=object; args=arguments; }
        @Override public void setResult(Object result) { super.setResult(result); replaced=true; }
    }
    static DrawCall draw(ImageView image,Canvas canvas) throws Throwable {
        var call=new DrawCall(image,canvas);
        Api102Runtime.callbacks.get((image instanceof StatusBarIconView ? StatusBarIconView.class : ImageView.class).getName()+"#onDraw").before(call);
        return call;
    }
    public static void main(String[] args) throws Throwable {
        ShadeHook.install("com.android.systemui","com.android.systemui",IosStatusBarCheck.class.getClassLoader());
        config(true,1);
        HookCallback binding=Api102Runtime.callbacks.get(StatusBarIconView.class.getName()+"#getIcon");
        StatusBarIconView image=new StatusBarIconView(); NativeIcon source=new NativeIcon(); Drawable nativeIcon=new Drawable();
        Drawable.Callback callback=new ImageView(); nativeIcon.setCallback(callback); nativeIcon.setBounds(2,3,30,19);
        image.drawable=nativeIcon; image.actualWidth=40; image.actualHeight=24;
        var call=param(image,source); call.setResult(nativeIcon); binding.after(call);
        check(call.getResult()==nativeIcon && nativeIcon.getCallback()==callback,"binding retains exact native object and callback");
        Canvas canvas=new Canvas(); var drawing=draw(image,canvas);
        check(drawing.replaced,"successful custom draw suppresses the original exactly once");
        check(!canvas.operations.isEmpty() && nativeIcon.draws==0 && canvas.depth==0,"view paints iOS symbol with balanced canvas");
        check(image.drawable==nativeIcon && nativeIcon.getBounds().left==2,"draw keeps native drawable and bounds");
        check(image.layout.width==37 && image.getScaleType()==ImageView.ScaleType.FIT_END && image.mIconScale==.6f,"layout, scale type and host scale stay native");
        nativeIcon.setAlpha(100); var filter=new ColorFilter(); nativeIcon.setColorFilter(filter);
        image.tint=ColorStateList.valueOf(0xff112233); canvas=new Canvas(); draw(image,canvas);
        check(canvas.colors.contains(0xff112233) && canvas.alphas.contains(100),"current native tint and alpha used");
        check(nativeIcon.getColorFilter()==filter,"native color filter unchanged");
        for(int state:new int[]{0,1}) {
            config(true,state); canvas=new Canvas(); draw(image,canvas);
            check(state==1 ? !canvas.operations.isEmpty() : canvas.operations.isEmpty(),"style switch changes drawing without rebinding");
        }
        config(false,1); canvas=new Canvas(); drawing=draw(image,canvas); check(!drawing.replaced && canvas.operations.isEmpty(),"module off delegates drawing to host"); config(true,1);
        image.mNotification=new Object(); canvas=new Canvas(); draw(image,canvas); check(canvas.operations.isEmpty(),"notification identity excluded even with tagged drawable");
        image.mNotification=null; source.icon.pkg="foreign"; call=param(image,source); call.setResult(nativeIcon); binding.after(call);
        canvas=new Canvas(); draw(image,canvas); check(canvas.operations.isEmpty(),"foreign source cannot inherit a stale slot tag");
        source.icon.pkg="com.android.systemui"; call=param(image,source); call.setResult(nativeIcon); binding.after(call);
        image.mVisibleState=1; canvas=new Canvas(); draw(image,canvas); check(canvas.operations.isEmpty(),"native overflow dot rendering preserved");
        image.mVisibleState=0; image.mIconAppearAmount=0; canvas=new Canvas(); draw(image,canvas); check(canvas.operations.isEmpty(),"fully hidden native icon stays hidden");
        image.mIconAppearAmount=1;
        var wifi=new ImageView(); wifi.parent=new ModernStatusBarWifiView(); wifi.drawable=new Drawable(); wifi.mResource=1;
        wifi.res.full="com.android.systemui:drawable/stat_signal_wifi_signal_2_os17";
        canvas=new Canvas(); draw(wifi,canvas); check(!canvas.operations.isEmpty(),"XML/resource signal drawn from original source ID");
        Drawable original=wifi.drawable; wifi.parent=null; canvas=new Canvas(); draw(wifi,canvas);
        check(canvas.operations.isEmpty() && wifi.drawable==original,"same resource outside status bar stays native");
        wifi.parent=new ModernStatusBarWifiView(); wifi.res.full="other:drawable/stat_signal_wifi_signal_2_os17";
        canvas=new Canvas(); draw(wifi,canvas); check(canvas.operations.isEmpty(),"foreign signal resource excluded");
        var unknown=new ImageView(); unknown.parent=new com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView();
        unknown.drawable=new Drawable(); canvas=new Canvas(); drawing=draw(unknown,canvas);
        check(!drawing.replaced && canvas.operations.isEmpty(),"modern slot without resource provenance is left native");
        wifi.res.full="com.android.systemui:drawable/stat_signal_wifi_signal_2_os17";
        wifi.actualWidth=0; canvas=new Canvas(); drawing=draw(wifi,canvas);
        check(!drawing.replaced,"zero-width native view cannot emit custom artwork");
        wifi.actualWidth=40; wifi.actualHeight=24; wifi.setPadding(2,3,4,1);
        canvas=new Canvas(); draw(wifi,canvas);
        check(canvas.pathBounds.stream().allMatch(b->b[0]>=2 && b[1]>=3 && b[2]<=36 && b[3]<=23),"artwork stays within native padded cell");
        config(true,0); canvas=new Canvas(); draw(wifi,canvas); int before=wifi.animationInvalidations;
        IosStatusBarHook.refresh(); check(wifi.animationInvalidations==before+1,"observed views repaint after setting changes, including when previously disabled");
        check(Api102Runtime.callbacks.get(ImageView.class.getName()+"#setImageDrawable")==null,"no ImageView setter recursion");
        System.out.println("Verified "+checks+" native view drawing checks.");
    }
}
