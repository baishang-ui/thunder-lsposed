package io.github.baishangui.thunder;

import android.os.Bundle;
import android.view.View;
import android.graphics.Outline;
import android.graphics.drawable.*;
import com.oplus.view.ViewRootManager;
import com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleView;
import com.oplus.systemui.plugins.seedling.card.ui.view.CardBackgroundView;

/** C17 BackgroundUtils is shared by CardBackgroundView and InstantContainer's content views. */
public final class CloudLayerCheck {
    private static final String BACKGROUND="com.heytap.log.nx.obus.a#b";
    private static final String CARD="com.oplus.systemui.plugins.seedling.card.ui.view.CardBackgroundView";
    private static final String CAPSULE="com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleView";
    private static int checks;
    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
        checks++;System.out.println("PASS: "+message);
    }
    private static void apply(View view,ViewRootManager manager)throws Throwable {
        com.heytap.log.nx.obus.a factory=new com.heytap.log.nx.obus.a();
        factory.b(view,manager);
        CloudCheck.after(BACKGROUND,factory,null,view,manager);
    }
    public static void main(String[] args)throws Throwable {
        ShadeHook.install("com.android.systemui","com.android.systemui",CloudLayerCheck.class.getClassLoader());
        CloudHook.install(CloudLayerCheck.class.getClassLoader());
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("cloud_enabled",true);
        b.putBoolean("cloud_corner_enabled",true);b.putInt("cloud_corner",9);
        b.putBoolean("cloud_blur",true);b.putInt("cloud_radius",87);
        b.putBoolean("cloud_tint",true);b.putInt("cloud_color",0x416688aa);CloudCheck.config(b);
        float density=new View().getResources().getDisplayMetrics().density;

        CardBackgroundView card=new CardBackgroundView();
        GradientDrawable fill=new GradientDrawable();
        com.oplus.systemui.plugins.seedling.util.d overlay=new com.oplus.systemui.plugins.seedling.util.d(new kotlin.Triple(2,117f,3f));
        card.setBackground(new LayerDrawable(new Drawable[]{new DrawableWrapper(fill),overlay}));
        CloudCheck.after(CARD+"#onAttachedToWindow",card,null);
        check(fill.radius==9*density && ((Number)overlay.d.e()).floatValue()==9*density,
            "card fill and translucent gradient match native blur corner");
        check(overlay.d.d().equals(2) && overlay.d.f().equals(3f),"custom radius preserves firmware smooth corner parameters");

        check(Api102Runtime.callbacks.containsKey(BACKGROUND),"shared background producer covers lock screen instant content");
        View lock=new View();ViewRootManager manager=new ViewRootManager(lock);apply(lock,manager);
        LayerDrawable layers=(LayerDrawable)lock.getBackground();
        com.oplus.systemui.plugins.seedling.util.d lockOverlay=(com.oplus.systemui.plugins.seedling.util.d)layers.getDrawable(1);
        check(manager.corner==9*density && ((Number)lockOverlay.d.e()).floatValue()==9*density,
            "lock screen blur and translucent overlay share selected corner");
        check(lockOverlay.g==null,"custom card tint removes the independent gradient shader");
        check(manager.radius==87 && manager.params.mode==1,"lock screen receives the same blur and tint settings");
        apply(lock,manager);
        lockOverlay=(com.oplus.systemui.plugins.seedling.util.d)((LayerDrawable)lock.getBackground()).getDrawable(1);
        check(((Number)lockOverlay.d.e()).floatValue()==9*density,"rebuilt lock screen backgrounds retain custom corners");

        View fallback=new View();apply(fallback,null);Outline outline=new Outline();fallback.outlineProvider.getOutline(fallback,outline);
        check(((GradientDrawable)fallback.getBackground()).radius==9*density && outline.radius==9*density && fallback.clip,
            "non-blur fallback fill and clipping outline share custom corners");
        check(outline.right==fallback.getWidth() && outline.bottom==fallback.getHeight(),"fallback outline uses current view bounds");

        // A manager can exist while the native producer chooses its flat fallback.
        CardBackgroundView flatCard=new CardBackgroundView();
        GradientDrawable lower=new GradientDrawable(),upper=new GradientDrawable();
        ColorDrawable solid=new ColorDrawable(0xff998877);
        flatCard.setBackground(new LayerDrawable(new Drawable[]{lower,new DrawableWrapper(upper),solid}));
        CloudCheck.after(CARD+"#onAttachedToWindow",flatCard,null);
        check(flatCard.c.params==null,"unused manager does not turn fallback into a material path");
        check(lower.getColor().getDefaultColor()==0 && upper.getColor().getDefaultColor()==0 && solid.getColor()==0x416688aa,
            "flat background receives selected tint once across nested gradient and solid fills");
        check(ResourceOverrides.color("com.oplus.systemui.plugins:color/card_background_blend_color",ShadeHook.snapshot())==null &&
            ResourceOverrides.color("com.oplus.systemui.plugins:color/card_background_mix_color",ShadeHook.snapshot())==null &&
            ResourceOverrides.color("com.oplus.systemui.plugins:color/capsule_background_color",ShadeHook.snapshot())==null,
            "native background resources stay intact instead of injecting tint into multiple inputs");

        // Both lock and status bar inflate CapsuleView; verify separate instances receive identical geometry.
        CapsuleView statusCapsule=new CapsuleView(),lockCapsule=new CapsuleView();
        CloudCheck.after(CAPSULE+"#onAttachedToWindow",statusCapsule,null);ViewRootManager statusManager=ViewRootManager.last;
        CloudCheck.after(CAPSULE+"#onAttachedToWindow",lockCapsule,null);ViewRootManager lockManager=ViewRootManager.last;
        check(statusManager.corner==lockManager.corner && lockManager.corner==9*density && lockManager.radius==87,
            "status bar and lock screen capsules use one configuration");

        b.putBoolean("cloud_blur",false);b.putBoolean("cloud_tint",false);CloudCheck.config(b);
        View cornerOnly=new View();ViewRootManager cornerManager=new ViewRootManager(cornerOnly);cornerManager.radius=44;apply(cornerOnly,cornerManager);
        check(cornerManager.corner==9*density && cornerManager.radius==44 && cornerManager.params==null,
            "corner-only customization preserves native blur and tint");
        b.putInt("cloud_corner",0);CloudCheck.config(b);apply(fallback,null);outline=new Outline();fallback.outlineProvider.getOutline(fallback,outline);
        check(outline.radius==0 && ((GradientDrawable)fallback.getBackground()).radius==0,"zero radius clears fill and clipping corners together");

        b.putBoolean("cloud_corner_enabled",false);CloudCheck.config(b);apply(lock,manager);
        lockOverlay=(com.oplus.systemui.plugins.seedling.util.d)((LayerDrawable)lock.getBackground()).getDrawable(1);
        check(((Number)lockOverlay.d.e()).floatValue()==117f,"disabled custom corner preserves native gradient geometry");
        b.putBoolean("cloud_enabled",false);b.putBoolean("cloud_blur",true);b.putBoolean("cloud_tint",true);CloudCheck.config(b);
        View disabled=new View();ViewRootManager disabledManager=new ViewRootManager(disabled);apply(disabled,disabledManager);
        check(disabledManager.corner==0 && disabledManager.radius==0 && disabledManager.params==null,"disabled cloud leaves shared native material untouched");

        b.putBoolean("cloud_enabled",true);b.putBoolean("cloud_corner_enabled",true);b.putInt("cloud_corner",9);CloudCheck.config(b);
        String geometry="com.oplus.systemui.plugins.seedling.util.c#f";
        check(Api102Runtime.callbacks.containsKey(geometry),"shared geometry producer is hooked for parent outlines and animations");
        kotlin.Triple nativeCorners=new kotlin.Triple(2,117f,3f);
        kotlin.Triple corners=(kotlin.Triple)CloudCheck.after(geometry,null,nativeCorners);
        check(((Number)corners.e()).floatValue()==9*density && corners.d().equals(2) && corners.f().equals(3f),
            "parent clipping and animated bounds receive the same radius with native smoothing");
        com.oplus.systemui.plugins.seedling.plugin.ContextHandler.instance.context=null;
        check(CloudCheck.after(geometry,null,nativeCorners)==nativeCorners,"geometry waits for a plugin context before converting dp");
        com.oplus.systemui.plugins.seedling.plugin.ContextHandler.instance.context=new android.content.Context();
        b.putBoolean("cloud_corner_enabled",false);CloudCheck.config(b);
        check(CloudCheck.after(geometry,null,nativeCorners)==nativeCorners,"system geometry is preserved without custom corners");
        b.putBoolean("cloud_corner_enabled",true);b.putBoolean("enabled",false);CloudCheck.config(b);
        check(CloudCheck.after(geometry,null,nativeCorners)==nativeCorners,"module off preserves system geometry");
        System.out.println("Verified "+checks+" cloud layer checks; real rendering still requires a phone.");
    }
}
