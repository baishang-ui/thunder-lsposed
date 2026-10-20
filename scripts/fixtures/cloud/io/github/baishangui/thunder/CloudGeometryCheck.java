package io.github.baishangui.thunder;

import android.os.Bundle;
import android.view.View;
import android.graphics.Outline;
import kotlin.Triple;

/** Reproductions from C17: cached scaled outlines, template and SDK geometry. */
public final class CloudGeometryCheck {
    static int checks;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    static HookCallback hook(String key){HookCallback h=Api102Runtime.callbacks.get(key);check(h!=null,"Missing geometry boundary: "+key);return h;}
    static float radius(Object triple){return ((Number)Reflect.callMethod(triple,"e")).floatValue();}
    public static Object sdkAfter(String key,Object object,Object result,Object... args){
        try{return CloudCheck.after(key,object,result,args);}catch(Throwable error){throw new IllegalStateException(error);}
    }
    public static void main(String[] args)throws Throwable {
        CloudHook.install(CloudGeometryCheck.class.getClassLoader());
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("cloud_enabled",true);
        b.putInt("cloud_card_color",0);
        b.putBoolean("cloud_card_corner_enabled",true);b.putInt("cloud_card_corner",32);CloudCheck.config(b);
        var card=new com.oplus.systemui.plugins.seedling.card.ui.view.CardView();
        float density=card.getResources().getDisplayMetrics().density;
        var scale=hook("com.oplus.systemui.plugins.common.util.k#c");
        Triple nativeCorners=new Triple(2,117f,3f);
        var produced=(Triple)CloudCheck.after("com.oplus.systemui.plugins.seedling.util.c#f",null,nativeCorners);
        var p=CloudCheck.param(null,null,card,null,.5f,produced,4);scale.before(p);
        com.oplus.systemui.plugins.common.util.k.c(card,null,.5f,produced,4);scale.after(p);
        var outline=(com.oplus.systemui.plugins.common.util.j)card.getOutlineProvider();
        var draw=hook("com.oplus.systemui.plugins.common.util.j#getOutline");
        var frame=CloudCheck.param(outline,null,card,new Outline());draw.before(frame);
        check(radius(outline.b)==32*density*2,"animated outline retains inverse scale");draw.after(frame);
        b.putBoolean("cloud_card_corner_enabled",false);CloudCheck.config(b);
        frame=CloudCheck.param(outline,null,card,new Outline());draw.before(frame);
        check(radius(outline.b)==234f,"cached animated outline restores unmodified native radius");draw.after(frame);
        b.putBoolean("cloud_card_corner_enabled",true);b.putInt("cloud_card_corner",0);CloudCheck.config(b);
        var press=new com.oplus.systemui.plugins.seedling.card.ui.view.k();press.b=produced;
        var pressHook=hook("com.oplus.systemui.plugins.seedling.card.ui.view.k#b");
        frame=CloudCheck.param(press,null,new android.graphics.Canvas(),400,200);pressHook.before(frame);
        check(radius(press.b)==0,"cached press highlight follows live zero radius");frame.failure=new RuntimeException();pressHook.after(frame);
        check(press.b==produced,"press restores its cache even after draw failure");
        var media=new com.oplus.systemui.plugins.shared.template.component.media.view.MediaBackGroundView();media.parent=card;
        var light=new com.oplus.systemui.plugins.shared.template.component.light.f();light.setCallback(media);
        var lightHook=hook("com.oplus.systemui.plugins.shared.template.component.light.f#draw");
        frame=CloudCheck.param(light,null,new android.graphics.Canvas());lightHook.before(frame);
        check(light.e.d[6]==0&&light.e.d[9]==0,"template shader updates all four corners even at native mask strength");
        light.e.d[0]=900;light.e.e=false;lightHook.after(frame);
        check(light.e.d[6]==117&&light.e.d[9]==117&&light.e.d[0]==900&&light.e.e,"shader restores native corners and dirties uniforms without reverting animation");
        media.parent=null;frame=CloudCheck.param(light,null,new android.graphics.Canvas());lightHook.before(frame);
        check(light.e.d[6]==117,"notification/unowned template is excluded from cloud corners");lightHook.after(frame);
        CloudSdkHook.install(CloudGeometryCheck.class.getClassLoader());
        var sdk=new g7.f2();sdk.n=new com.oplus.ule.lite.components.s1();
        var styleHook=hook("g7.f2#s0");var nativeStyle=new f8.j5(2,20,3);
        p=CloudCheck.param(sdk,nativeStyle,true);styleHook.after(p);
        check(((f8.j5)p.getResult()).a()==0,"SDK expanded content uses card radius in dp");
        sdk.d="Notification";p=CloudCheck.param(sdk,nativeStyle,true);styleHook.after(p);
        check(((f8.j5)p.getResult()).a()==0,"SDK notification entrance uses cloud geometry");
        sdk.d="StatusBar";sdk.n=null;p=CloudCheck.param(sdk,nativeStyle,true);styleHook.after(p);
        check(p.getResult()==nativeStyle,"SDK unexpanded capsule does not inherit card radius");
        sdk.n=new com.oplus.ule.lite.components.s1();sdk.k.setBackground(new e7.sj());
        var expanded=hook("g7.f2#w0");p=CloudCheck.param(sdk,null,new Object(),true);expanded.before(p);expanded.after(p);
        for(int dp:new int[]{12,64,0,32}) {
            b.putInt("cloud_card_corner",dp);CloudCheck.config(b);CloudSdkHook.refresh();
            check(sdk.outlineRadius==dp&&sdk.shaderRadius==dp*density,"existing SDK content and COE shader refresh together: "+dp);
            check(((e7.sj)sdk.k.getBackground()).radius==dp*density&&sdk.l.shaderRadius==dp*density,"existing SDK stroke and independent light shader refresh together: "+dp);
            Outline result=new Outline();sdk.l.a.getOutlineProvider().getOutline(sdk.l.a,result);
            check(result.radius==dp*density,"independent light backend outline refreshes: "+dp);
        }
        b.putBoolean("cloud_card_corner_enabled",false);CloudCheck.config(b);CloudSdkHook.refresh();
        check(sdk.outlineRadius==20&&sdk.shaderRadius==20*density&&sdk.l.shaderRadius==20*density&&((e7.sj)sdk.k.getBackground()).radius==20*density,
            "disabling corners restores all existing SDK consumers to original style");
        b.putBoolean("cloud_card_corner_enabled",true);b.putBoolean("enabled",false);CloudCheck.config(b);CloudSdkHook.refresh();
        check(sdk.outlineRadius==20&&sdk.l.shaderRadius==20*density,"module off restores both SDK backends");
        int writes=sdk.outlineWrites;CloudSdkHook.refresh();
        check(sdk.outlineWrites==writes,"disabled customization does not repeatedly rebuild native SDK geometry");
        b.putBoolean("enabled",true);CloudCheck.config(b);media.parent=card;
        for(String key:new String[]{"com.oplus.systemui.plugins.shared.template.component.view.CustomLottieView#i","com.oplus.systemui.plugins.shared.template.component.view.c#getOutline"}) {
            var mediaHook=hook(key);boolean isOutline=key.endsWith("getOutline");
            p=isOutline?CloudCheck.param(new Object(),null,media,new Outline()):CloudCheck.param(media,null,new android.graphics.Canvas());
            mediaHook.before(p);check(media.t==32*density,"media stroke/outline uses card radius");p.failure=new RuntimeException();mediaHook.after(p);
            check(media.t==117,"media native radius restored on exception");
        }
        // SingleCardPage uses a plain ViewGroup root, not CardView/MediaBackGroundView.
        View pageRoot=new View();pageRoot.parent=card;
        var nativeStroke=new android.graphics.drawable.GradientDrawable();nativeStroke.setCornerRadius(117);
        pageRoot.foreground=nativeStroke;
        var page=new com.oplus.systemui.plugins.shared.template.page.entity.t(pageRoot);
        var createPage=Api102Runtime.callbacks.get("com.oplus.systemui.plugins.shared.template.page.entity.t#<init>");
        if(createPage==null)createPage=new HookCallback(){};
        p=CloudCheck.param(page,null);createPage.after(p);
        var updatePage=Api102Runtime.callbacks.get("com.oplus.systemui.plugins.shared.template.page.entity.s#a");
        if(updatePage==null)updatePage=new HookCallback(){};
        var observer=new com.oplus.systemui.plugins.shared.template.page.entity.s(page);
        for(int dp:new int[]{0,12,64}) {
            b.putInt("cloud_card_corner",dp);CloudCheck.config(b);CloudTemplateHook.refresh();
            p=CloudCheck.param(null,null,pageRoot,null,.5f,nativeCorners,4);
            scale.before(p);com.oplus.systemui.plugins.common.util.k.c(pageRoot,null,.5f,nativeCorners,4);scale.after(p);
            var rootOutline=(com.oplus.systemui.plugins.common.util.j)pageRoot.getOutlineProvider();
            frame=CloudCheck.param(rootOutline,null,pageRoot,new Outline());draw.before(frame);
            check(radius(rootOutline.b)==dp*density*2,"plain template root clipping follows cloud corners and animation scale");draw.after(frame);
            check(nativeStroke.getCornerRadius()==dp*density,"plain template foreground stroke follows live cloud corners");
        }
        View delayedRoot=new View();
        var delayedStroke=new android.graphics.drawable.GradientDrawable();delayedStroke.setCornerRadius(117);delayedRoot.foreground=delayedStroke;
        p=CloudCheck.param(new com.oplus.systemui.plugins.shared.template.page.entity.t(delayedRoot),null);createPage.after(p);
        delayedRoot.parent=card;
        p=CloudCheck.param(null,null,delayedRoot,null,1f,nativeCorners,12);
        scale.before(p);com.oplus.systemui.plugins.common.util.k.c(delayedRoot,null,1f,nativeCorners,12);scale.after(p);
        frame=CloudCheck.param(delayedRoot.getOutlineProvider(),null,delayedRoot,new Outline());draw.before(frame);
        check(delayedStroke.getCornerRadius()==64*density,"template attached after construction synchronizes foreground on its first outline");draw.after(frame);
        var replacementStroke=new android.graphics.drawable.GradientDrawable();replacementStroke.setCornerRadii(new float[]{3,4,5,6,7,8,9,10});
        pageRoot.foreground=replacementStroke;
        p=CloudCheck.param(observer,null,new Object(),null);updatePage.after(p);
        check(replacementStroke.getCornerRadii()==null&&replacementStroke.getCornerRadius()==64*density,"template observer reapplies corners after replacing the foreground");
        check(nativeStroke.getCornerRadius()==117,"replaced native foreground is restored before reuse");
        b.putBoolean("cloud_card_corner_enabled",false);CloudCheck.config(b);CloudTemplateHook.refresh();
        check(java.util.Arrays.equals(replacementStroke.getCornerRadii(),new float[]{3,4,5,6,7,8,9,10}),"disabled corners restore original per-edge foreground radii");
        var rootOutline=(com.oplus.systemui.plugins.common.util.j)pageRoot.getOutlineProvider();
        frame=CloudCheck.param(rootOutline,null,pageRoot,new Outline());draw.before(frame);
        check(radius(rootOutline.b)==234,"disabled template clipping restores native scaled baseline");draw.after(frame);
        b.putBoolean("cloud_card_corner_enabled",true);CloudCheck.config(b);pageRoot.parent=null;CloudTemplateHook.refresh();
        check(java.util.Arrays.equals(replacementStroke.getCornerRadii(),new float[]{3,4,5,6,7,8,9,10}),"unowned template root retains its native foreground");
        frame=CloudCheck.param(rootOutline,null,pageRoot,new Outline());draw.before(frame);
        check(radius(rootOutline.b)==234,"unowned template root retains native clipping");draw.after(frame);
        var lockOwner=new com.oplus.systemui.notification.lockscreen.capsule.CapsuleItemView();
        lockOwner.entry=new com.oplus.systemui.notification.lockscreen.seedling.CapsuleSeedingData();
        pageRoot.parent=lockOwner;media.parent=pageRoot;CloudTemplateHook.refresh();
        check(java.util.Arrays.equals(replacementStroke.getCornerRadii(),new float[]{3,4,5,6,7,8,9,10}),"native capsule content retains its original foreground geometry");
        frame=CloudCheck.param(rootOutline,null,pageRoot,new Outline());draw.before(frame);
        check(radius(rootOutline.b)==234,"native capsule content retains its original scaled clipping");draw.after(frame);
        frame=CloudCheck.param(light,null,new android.graphics.Canvas());lightHook.before(frame);
        check(light.e.d[6]==117,"native capsule template lights retain their original geometry");lightHook.after(frame);
        lockOwner.entry=new Object();CloudTemplateHook.refresh();
        check(java.util.Arrays.equals(replacementStroke.getCornerRadii(),new float[]{3,4,5,6,7,8,9,10}),"ordinary lock capsule reuse restores the template foreground");
        b.putInt("cloud_card_corner",32);CloudCheck.config(b);
        var nativeRead=hook("com.heytap.log.strategy.f#H");p=CloudCheck.param(null,null,new android.content.Context(),2,false);
        CloudHook.class.getDeclaredMethod("beginNativeCorners").invoke(null);nativeRead.before(p);nativeRead.after(p);
        check(CloudHook.readingNativeCorners(),"nested geometry producer preserves outer native read scope");CloudHook.class.getDeclaredMethod("endNativeCorners").invoke(null);
        check(!CloudHook.readingNativeCorners(),"native read scope ends without leaking to resources");
        View fallback=new View();var original=new com.oplus.systemui.plugins.common.util.j(null,nativeCorners);fallback.setOutlineProvider(original);
        var factory=new com.heytap.log.nx.obus.a();var background=hook("com.heytap.log.nx.obus.a#b");
        p=CloudCheck.param(factory,null,fallback,null);background.before(p);factory.b(fallback,null);
        var nativeOutline=fallback.getOutlineProvider();boolean nativeClip=fallback.getClipToOutline();background.after(p);
        check(fallback.getOutlineProvider()!=original&&fallback.getClipToOutline(),"flat background installs custom clipping");
        check(fallback.getOutlineProvider() instanceof com.oplus.systemui.plugins.common.util.j,
            "flat cloud clipping uses the native smooth outline rather than ordinary round-rect clipping");
        var flatOutline=(com.oplus.systemui.plugins.common.util.j)fallback.getOutlineProvider();
        check(flatOutline.b.d().equals(nativeCorners.d())&&flatOutline.b.f().equals(nativeCorners.f()),
            "flat cloud outline retains the same corner type and weight as its native background");
        View scaledFallback=new View();factory.b(scaledFallback,null);
        var bounds=new android.graphics.Rect(7,9,307,209);
        var scaledNative=new com.oplus.systemui.plugins.common.util.j(bounds,new Triple(2,234f,3f));
        scaledFallback.setOutlineProvider(scaledNative);
        Reflect.setAdditionalInstanceField(scaledNative,"thunder.cloud-corner-scale",2f);
        var scaledFrame=CloudCheck.param(factory,null,scaledFallback,null);background.after(scaledFrame);
        var scaledOutline=(com.oplus.systemui.plugins.common.util.j)scaledFallback.getOutlineProvider();
        check(scaledOutline.a==bounds&&radius(scaledOutline.b)==32*density*2,
            "flat smooth clipping preserves the native animation bounds and inverse scale");
        b.putBoolean("cloud_card_corner_enabled",false);CloudCheck.config(b);
        frame=CloudCheck.param(scaledOutline,null,scaledFallback,new Outline());draw.before(frame);
        check(radius(scaledOutline.b)==234f,"flat smooth clipping retains its original native scaled baseline");draw.after(frame);
        b.putBoolean("cloud_card_corner_enabled",true);CloudCheck.config(b);
        b.putBoolean("cloud_enabled",false);CloudCheck.config(b);
        p=CloudCheck.param(factory,null,fallback,null);background.before(p);
        check(fallback.getOutlineProvider()==nativeOutline&&fallback.getClipToOutline()==nativeClip,"background reconstruction restores original outline and clip flag before native work");
        factory.b(fallback,null);background.after(p);
        System.out.println("Verified "+checks+" cross-layer cloud geometry checks.");
    }
}
