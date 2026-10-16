package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import com.oplus.systemui.qs.base.res.drawable.*;
import com.oplus.systemui.qs.base.res.model.TileViewFlag;
import com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar;
import com.oplusos.systemui.common.blurability.*;
import com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable;

/** Regression checks against the separate background/state paths in the exported C17 firmware. */
public final class QsStateCheck {
    static final String B=QsTintCheck.B, TILE=QsTintCheck.TILE, FLAT=QsTintCheck.FLAT;
    static final String SEEK="com.oplus.systemui.qs.base.util.QsSeekBarBlurManager";
    static final String BAR=OplusQsVerticalSeekBar.class.getName();
    static int checks,failures;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;System.out.println("PASS: "+message);}
    static Bundle settings() {
        Bundle b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("qs_card_tint",true);b.putInt("qs_card_color",0x4188aacc);return b;
    }
    static Object mix(BlurConfig c)throws Throwable{return CloudCheck.after(B+"BlurConfig#getPlatformMixConfig",c,c.platformMixConfig);}
    static void tileStates()throws Throwable {
        Bundle b=settings();b.putBoolean("qs_card_corner_enabled",true);b.putInt("qs_card_corner",19);CloudCheck.config(b);
        BlurConfig c=new BlurConfig();c.platformMixConfig=QsTintCheck.multi(0xff3377cc);
        Object nativeMix=c.platformMixConfig;
        c.motionBlurMixConfig=new BlurMixConfig.BlurMixSingle(new MixColor(4,0xff3377cc,0));
        ViewBlurProxy proxy=new ViewBlurProxy(c);AutoBlurDrawable auto=new AutoBlurDrawable(proxy);
        MixColorTileDrawable tile=new MixColorTileDrawable(auto);
        CloudCheck.after(TILE+"#<init>",tile,null,auto,new java.util.LinkedHashMap<>());
        for(TileViewFlag state:new TileViewFlag[]{TileViewFlag.Active,TileViewFlag.Unavailable,TileViewFlag.Inoperable}) {
            tile.setState(new int[]{state.getAttr()});
            check(mix(c)==c.platformMixConfig,state+" retains native tile shader instead of the inactive tint");
            HookCallback.Param p=QsTintCheck.before(TILE+"#draw",tile,new Canvas());
            check(tile.maskColor==0xff112233,state+" retains native tile mask");QsTintCheck.after(TILE+"#draw",p);
            check(CloudCheck.after(B+"BlurConfig#getBlurColor",c,0xff123456).equals(0xff123456),state+" retains native fallback mask");
            Object motion=CloudCheck.after(B+"BlurConfig#getMotionBlurMixConfig",c,c.motionBlurMixConfig);
            check(motion==c.motionBlurMixConfig,state+" retains native motion tint");
            p=QsTintCheck.before(B+"ViewBlurProxy#applyConfigToPlatformBlur",proxy);
            check(c.platformMixConfig==nativeMix,state+" native backend receives native state color");
            check(c.cornerRadius==19f*android.content.res.Resources.getSystem().getDisplayMetrics().density,state+" custom corners remain independent of native state color");
            QsTintCheck.after(B+"ViewBlurProxy#applyConfigToPlatformBlur",p);
            check(c.cornerRadius==47,state+" native corner fields restore after drawing");
        }
        tile.setState(new int[]{TileViewFlag.Inactive.getAttr()});
        check(mix(c)!=c.platformMixConfig,"switching tile off reapplies custom background");
        HookCallback.Param p=QsTintCheck.before(TILE+"#draw",tile,new Canvas());
        check(tile.maskColor==0,"inactive tile removes native mask over custom background");QsTintCheck.after(TILE+"#draw",p);
        GradientTileDrawable flat=new GradientTileDrawable();
        flat.setState(new int[]{TileViewFlag.Active.getAttr()});
        p=QsTintCheck.before(FLAT+"#draw",flat,new Canvas());
        check(flat.colorDrawable.getColor()==0xff998877,"active non-blur tile retains its native highlight");QsTintCheck.after(FLAT+"#draw",p);
        flat.setState(new int[]{TileViewFlag.Inactive.getAttr()});
        p=QsTintCheck.before(FLAT+"#draw",flat,new Canvas());
        check(flat.colorDrawable.getColor()==0x4188aacc,"inactive non-blur tile uses chosen ARGB");QsTintCheck.after(FLAT+"#draw",p);
        Bundle off=settings();off.putBoolean("qs_card_tint",false);CloudCheck.config(off);
        check(mix(c)==c.platformMixConfig,"turning tint off restores inactive native shaders");
    }
    static void notificationTopCards()throws Throwable {
        CloudCheck.config(settings());
        String cardClass="com.oplus.systemui.qs.base.tile.OplusQSHighlightTileView";
        BlurConfig c=new BlurConfig();c.platformMixConfig=QsTintCheck.multi(0xff3377cc);
        MixColorTileDrawable bg=new MixColorTileDrawable(new AutoBlurDrawable(new ViewBlurProxy(c)));
        bg.setState(new int[]{TileViewFlag.Active.getAttr()});
        CloudCheck.after(TILE+"#<init>",bg,null,bg.autoBlurDrawable,new java.util.LinkedHashMap<>());
        com.oplus.systemui.qs.base.tile.OplusQSHighlightTileView card=new com.oplus.systemui.qs.base.tile.OplusQSHighlightTileView(bg);
        CloudCheck.after(cardClass+"#<init>",card,null);
        BlurMixConfig.BlurMixMultiWithShader out=(BlurMixConfig.BlurMixMultiWithShader)mix(c);
        check(out.foregroundShaderParam.topLayerColor==0x4188aacc,
            "notification top 2x1 active card background receives shared material");
        HookCallback.Param p=QsTintCheck.before(TILE+"#draw",bg,new Canvas());
        check(bg.maskColor==0,"notification top 2x1 background clears the native mask");QsTintCheck.after(TILE+"#draw",p);
        check(card.icon.getState()[0]==TileViewFlag.Active.getAttr(),"notification top card icon state remains native");
        GradientTileDrawable flat=new GradientTileDrawable();flat.setState(new int[]{TileViewFlag.Active.getAttr()});
        card.mBgDrawable.setDrawable(flat);
        CloudCheck.after(cardClass+"#onDrawableUpdate",card,null,java.util.List.of(),false);
        p=QsTintCheck.before(FLAT+"#draw",flat,new Canvas());
        check(flat.colorDrawable.getColor()==0x4188aacc,"rebuilt non-blur 2x1 background keeps custom tint in active state");
        p.failure=new IllegalStateException();QsTintCheck.after(FLAT+"#draw",p);
        check(flat.colorDrawable.getColor()==0xff998877,"2x1 non-blur background restores native fill after a failed frame");
        Bundle off=settings();off.putBoolean("qs_card_tint",false);CloudCheck.config(off);
        check(mix(c)==c.platformMixConfig,"turning shared tint off restores notification top card shaders");
        p=QsTintCheck.before(FLAT+"#draw",flat,new Canvas());
        check(flat.colorDrawable.getColor()==0xff998877,"turning shared tint off restores rebuilt notification top card fill");
        QsTintCheck.after(FLAT+"#draw",p);
    }
    static void controlCenterTwoXOne()throws Throwable {
        CloudCheck.config(settings());
        String base="com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView";
        String type=base+"TwoXOne";
        var card=new com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXOne();
        BlurConfig c=new BlurConfig();c.platformMixConfig=QsTintCheck.multi(0xff3377cc);
        MixColorTileDrawable bg=new MixColorTileDrawable(new AutoBlurDrawable(new ViewBlurProxy(c)));
        bg.setState(new int[]{TileViewFlag.Active.getAttr()});
        CloudCheck.after(TILE+"#<init>",bg,null,bg.autoBlurDrawable,new java.util.LinkedHashMap<>());
        card.bgDrawable.setDrawable(bg);
        GradientTileDrawable theme=new GradientTileDrawable();theme.setState(bg.getState());
        card.themeDrawable.setDrawable(theme);card.icon.setState(bg.getState());
        // Without the resizeable owner hook, the generic state guard keeps the system blue.
        if(Api102Runtime.callbacks.containsKey(type+"#<init>"))CloudCheck.after(type+"#<init>",card,null);
        check(((BlurMixConfig.BlurMixMultiWithShader)mix(c)).foregroundShaderParam.topLayerColor==0x4188aacc,
            "control center active 2x1 background uses shared material");
        for(TileViewFlag state:TileViewFlag.values()) {
            bg.setState(new int[]{state.getAttr()});
            check(((BlurMixConfig.BlurMixMultiWithShader)mix(c)).foregroundShaderParam.topLayerColor==0x4188aacc,
                "control center 2x1 "+state+" background keeps shared tint");
        }
        bg.setState(new int[]{TileViewFlag.Active.getAttr()});
        HookCallback.Param p=QsTintCheck.before(TILE+"#draw",bg,new Canvas());
        check(bg.maskColor==0,"control center 2x1 clears the native highlight mask");QsTintCheck.after(TILE+"#draw",p);
        p=QsTintCheck.before(FLAT+"#draw",theme,new Canvas());
        check(theme.colorDrawable.getColor()==0x4188aacc,"2x1 theme transition background uses shared tint");
        QsTintCheck.after(FLAT+"#draw",p);
        check(card.icon.getState()[0]==TileViewFlag.Active.getAttr(),"2x1 icon keeps native state");
        check(QsCardHook.textSurface(card.icon)==0,"2x1 icon is not tagged as a card surface");
        Bundle changed=settings();changed.putInt("qs_card_color",0x80665544);CloudCheck.config(changed);
        int invalidations=bg.invalidations;QsCardHook.refresh();
        check(bg.invalidations>invalidations,"live changes invalidate existing control center 2x1 backgrounds");
        check(((BlurMixConfig.BlurMixMultiWithShader)mix(c)).foregroundShaderParam.topLayerColor==0x80665544,
            "existing control center 2x1 receives changed color and alpha");
        GradientTileDrawable flat=new GradientTileDrawable();flat.setState(bg.getState());card.bgDrawable.setDrawable(flat);
        CloudCheck.after(type+"#onDrawableUpdate",card,null,java.util.List.of(),false);
        p=QsTintCheck.before(FLAT+"#draw",flat,new Canvas());
        check(flat.colorDrawable.getColor()==0x80665544,"rebuilt non-blur control center 2x1 retains shared material");
        QsTintCheck.after(FLAT+"#draw",p);
        GradientTileDrawable rebuiltTheme=new GradientTileDrawable();rebuiltTheme.setState(bg.getState());card.themeDrawable.setDrawable(rebuiltTheme);
        CloudCheck.after(type+"#onDrawableUpdate",card,null,java.util.List.of(),true);
        p=QsTintCheck.before(FLAT+"#draw",rebuiltTheme,new Canvas());
        check(rebuiltTheme.colorDrawable.getColor()==0x80665544,"rebuilt 2x1 theme layer retains shared material");
        QsTintCheck.after(FLAT+"#draw",p);
        BlurConfig rebuilt=new BlurConfig();rebuilt.platformMixConfig=QsTintCheck.multi(0xff224488);
        MixColorTileDrawable rebuiltBlur=new MixColorTileDrawable(new AutoBlurDrawable(new ViewBlurProxy(rebuilt)));
        rebuiltBlur.setState(bg.getState());
        CloudCheck.after(TILE+"#<init>",rebuiltBlur,null,rebuiltBlur.autoBlurDrawable,new java.util.LinkedHashMap<>());
        card.bgDrawable.setDrawable(rebuiltBlur);
        CloudCheck.after(type+"#onDrawableUpdate",card,null,java.util.List.of(),false);
        check(((BlurMixConfig.BlurMixMultiWithShader)mix(rebuilt)).foregroundShaderParam.topLayerColor==0x80665544,
            "rebuilt native blur 2x1 background receives shared material");
        // Attachment is inherited: the base hook must filter out ordinary 1x1/2x2 tiles.
        var ordinary=new com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView();
        GradientTileDrawable nativeFill=new GradientTileDrawable();nativeFill.setState(bg.getState());ordinary.bgDrawable.setDrawable(nativeFill);
        CloudCheck.after(base+"#onAttachedToWindow",ordinary,null);
        p=QsTintCheck.before(FLAT+"#draw",nativeFill,new Canvas());
        check(nativeFill.colorDrawable.getColor()==0xff998877,"other resizeable tile sizes keep native active color");QsTintCheck.after(FLAT+"#draw",p);
        GradientTileDrawable attached=new GradientTileDrawable();attached.setState(bg.getState());card.bgDrawable.setDrawable(attached);
        CloudCheck.after(base+"#onAttachedToWindow",card,null);
        p=QsTintCheck.before(FLAT+"#draw",attached,new Canvas());
        check(attached.colorDrawable.getColor()==0x80665544,"reattached 2x1 background remains connected to shared material");QsTintCheck.after(FLAT+"#draw",p);
        changed.putInt("qs_card_color",0);CloudCheck.config(changed);
        p=QsTintCheck.before(FLAT+"#draw",attached,new Canvas());
        check(attached.colorDrawable.getColor()==0,"2x1 zero opacity clears custom background tint");QsTintCheck.after(FLAT+"#draw",p);
        Bundle off=settings();off.putBoolean("qs_card_tint",false);CloudCheck.config(off);
        check(mix(c)==c.platformMixConfig,"2x1 tint off restores native blur material");
        p=QsTintCheck.before(FLAT+"#draw",attached,new Canvas());
        check(attached.colorDrawable.getColor()==0xff998877,"2x1 tint off restores native flat material");QsTintCheck.after(FLAT+"#draw",p);
    }
    static void sliderBackground()throws Throwable {
        CloudCheck.config(settings());
        check(Api102Runtime.callbacks.containsKey(SEEK+"#getSeekBarBackground"),"slider background factory is hooked separately from progress fill");
        BlurConfig c=new BlurConfig();c.platformMixConfig=QsTintCheck.multi(0x88998877);c.blurRadius=83;
        ViewBlurProxy proxy=new ViewBlurProxy(c);AutoBlurDrawable background=new AutoBlurDrawable(proxy);
        OplusQsVerticalSeekBar view=new OplusQsVerticalSeekBar();
        CloudCheck.after(SEEK+"#getSeekBarBackground",null,background,view,new Rect(),28f,1f,true,false,1f);
        BlurMixConfig.BlurMixMultiWithShader out=(BlurMixConfig.BlurMixMultiWithShader)mix(c);
        check(out.foregroundShaderParam.topLayerColor==0x4188aacc,"volume and brightness background receives selected ARGB");
        check(c.blurRadius==83,"slider tint retains system blur radius");
        BlurConfig active=new BlurConfig();active.platformMixConfig=QsTintCheck.multi(0xffeeeeee);
        check(mix(active)==active.platformMixConfig,"slider progress fill keeps its native contrasting color");
        check(Api102Runtime.callbacks.containsKey(SEEK+"#applySeekBarBgBlurConfig"),"slider background refresh is hooked");
        BlurConfig rebuilt=new BlurConfig();rebuilt.platformMixConfig=QsTintCheck.multi(0xff112233);
        AutoBlurDrawable newBackground=new AutoBlurDrawable(new ViewBlurProxy(rebuilt));
        HookCallback.Param p=QsTintCheck.before(SEEK+"#applySeekBarBgBlurConfig",null,view.getContext(),new Rect(),28f,1f,newBackground,true,false,1f);
        QsTintCheck.after(SEEK+"#applySeekBarBgBlurConfig",p);
        check(mix(rebuilt)!=rebuilt.platformMixConfig,"rebuilt slider background remains connected to custom tint");
        Bundle off=settings();off.putBoolean("enabled",false);CloudCheck.config(off);
        check(mix(c)==c.platformMixConfig,"module off restores native slider shaders");
    }
    static void sliderFallback()throws Throwable {
        CloudCheck.config(settings());
        check(Api102Runtime.callbacks.containsKey(BAR+"#onDraw"),"non-blur slider drawing is hooked");
        OplusQsVerticalSeekBar view=new OplusQsVerticalSeekBar();Drawable theme=view.mInactiveTrackDrawable;
        HookCallback.Param p=QsTintCheck.before(BAR+"#onDraw",view,new Canvas());
        check(view.mBackgroundColor==0x4188aacc && view.mInactiveTrackDrawable==null,"non-blur slider uses custom background within native track geometry");
        check(view.mProgressColor==0xffeeeeee,"non-blur slider progress remains visible");
        p.failure=new IllegalStateException();QsTintCheck.after(BAR+"#onDraw",p);
        check(view.mBackgroundColor==0xff998877 && view.mInactiveTrackDrawable==theme,"slider fallback restores original fields even on drawing failure");
        int invalidations=view.invalidations;QsCardHook.refresh();
        check(view.invalidations>invalidations,"live changes redraw existing sliders");
        Bundle off=settings();off.putBoolean("qs_card_tint",false);CloudCheck.config(off);
        p=QsTintCheck.before(BAR+"#onDraw",view,new Canvas());
        check(view.mBackgroundColor==0xff998877 && view.mInactiveTrackDrawable==theme,"disabled slider tint retains native theme background");QsTintCheck.after(BAR+"#onDraw",p);
        CloudCheck.config(settings());view.isDrawingWithMixColor=true;
        p=QsTintCheck.before(BAR+"#onDraw",view,new Canvas());
        check(view.mBackgroundColor==0xff998877 && view.mInactiveTrackDrawable==theme,"blur slider avoids applying fallback tint twice");QsTintCheck.after(BAR+"#onDraw",p);
    }
    public static void main(String[] args)throws Throwable {
        ShadeHook.install("com.android.systemui","com.android.systemui",QsStateCheck.class.getClassLoader());
        for(String scene:new String[]{"tileStates","notificationTopCards","controlCenterTwoXOne","sliderBackground","sliderFallback"})try {
            QsStateCheck.class.getDeclaredMethod(scene).invoke(null);
        } catch(java.lang.reflect.InvocationTargetException e){failures++;System.out.println("FAIL: "+scene+": "+e.getCause());}
        if(failures>0)throw new AssertionError(failures+" QS regression scenes failed");
        System.out.println("Verified "+checks+" QS state and slider checks; phone rendering remains unverified.");
    }
}
