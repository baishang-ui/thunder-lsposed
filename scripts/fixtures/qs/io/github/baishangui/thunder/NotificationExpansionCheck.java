package io.github.baishangui.thunder;

import android.os.Bundle;
import android.view.View;
import com.oplusos.systemui.common.blurability.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Replays the firmware's row-radius feedback and separate stack background consumers. */
public final class NotificationExpansionCheck {
    static final String B="com.oplusos.systemui.common.blurability.";
    static final String STACK=B+"platformblur.MultiPlatformBlurHelper#<init>";
    static final String DRAW=B+"platformblur.PlatformBlurDrawable#applyBlurConfig";
    static final ArrayList<String> failures=new ArrayList<>();
    static int count;
    static void check(boolean ok,String label) {
        count++;System.out.println((ok?"PASS: ":"FAIL: ")+label);
        if(!ok)failures.add(label);
    }
    public static final class Row extends com.android.systemui.statusbar.notification.row.ExpandableNotificationRow {
        public float top=1f,bottom=1f;
        public int refreshed;
        public float getTopRoundness(){return top;}
        public float getBottomRoundness(){return bottom;}
        public void applyRoundnessAndInvalidate(){refreshed++;}
    }
    public static final class StackHelper {
        public View view;
        public Map<Integer,BlurConfig> configMap=new LinkedHashMap<>();
        public int applied;
        public void applyBlurConfig(){applied++;}
    }
    static BlurConfig config() {
        BlurConfig c=new BlurConfig();c.platformMixConfig=QsTintCheck.multi(0x88998877);return c;
    }
    static HookCallback.Param before(String key,Object owner,Object... args)throws Throwable {
        HookCallback.Param p=CloudCheck.param(owner,null,args);
        HookCallback callback=Api102Runtime.callbacks.get(key);
        if(callback!=null)callback.before(p);
        return p;
    }
    static void after(String key,HookCallback.Param p)throws Throwable {
        HookCallback callback=Api102Runtime.callbacks.get(key);
        if(callback!=null)callback.after(p);
    }
    public static void main(String[] args)throws Throwable {
        ShadeHook.install("com.android.systemui","com.android.systemui",NotificationExpansionCheck.class.getClassLoader());
        Bundle b=new Bundle();b.putBoolean("enabled",true);
        b.putBoolean("notification_card_corner_enabled",true);b.putInt("notification_card_corner",13);
        b.putBoolean("notification_card_blur",true);b.putInt("notification_card_radius",73);
        b.putBoolean("notification_card_tint",true);b.putInt("notification_card_color",0x416688aa);
        b.putBoolean("banner_corner_enabled",true);b.putInt("banner_corner",25);
        CloudCheck.config(b);
        Row row=new Row();View background=row.mBackgroundNormal;background.parent=row;
        BlurConfig c=config();ViewBlurProxy proxy=new ViewBlurProxy(c);
        NotificationStyleCheck.Manager manager=new NotificationStyleCheck.Manager();manager.proxy=proxy;
        CloudCheck.after(NotificationStyleCheck.M+"requireBlurProxyForView",manager,proxy,background,NotificationStyleCheck.CardType.NOTIFICATION,1f,null);
        float density=background.getResources().getDisplayMetrics().density;
        for(int corner:new int[]{13,30,0,7}) {
            b.putInt("notification_card_corner",corner);CloudCheck.config(b);
            for(float progress:new float[]{1f,.75f,.5f,0f,.5f,1f}) {
                row.top=progress;row.bottom=1f-progress;
                // setRadius -> NotificationBackgroundViewExtImp.updateRadius writes the row's
                // already-customized pixel radii back into the native BlurConfig edge fields.
                c.leftTopCornerRadius=c.rightTopCornerRadius=((Number)CloudCheck.after(
                    "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow#getTopCornerRadius",row,47*row.top)).floatValue();
                c.leftBottomCornerRadius=c.rightBottomCornerRadius=((Number)CloudCheck.after(
                    "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow#getBottomCornerRadius",row,47*row.bottom)).floatValue();
                HookCallback.Param outer=before(B+"ViewBlurProxy#applyBlurConfig",proxy);
                HookCallback.Param inner=before(B+"ViewBlurProxy#applyConfigToPlatformBlur",proxy);
                check(Math.abs(c.leftTopCornerRadius-corner*density*row.top)<.001f &&
                    Math.abs(c.rightBottomCornerRadius-corner*density*row.bottom)<.001f,
                    "row radius is applied once at corner="+corner+", roundness="+progress);
                after(B+"ViewBlurProxy#applyConfigToPlatformBlur",inner);
                after(B+"ViewBlurProxy#applyBlurConfig",outer);
                check(c.cornerRadius==47 && c.leftTopCornerRadius==corner*density*row.top,
                    "nested consumers restore native radius and latest animation edges");
            }
        }
        b.putInt("notification_card_corner",13);CloudCheck.config(b);row.top=1;row.bottom=.5f;
        Row otherRow=new Row();otherRow.top=.25f;otherRow.bottom=0f;
        View otherBackground=otherRow.mBackgroundNormal;otherBackground.parent=otherRow;
        BlurConfig other=config();ViewBlurProxy otherProxy=new ViewBlurProxy(other);
        CloudCheck.after(NotificationStyleCheck.M+"requireBlurProxyForView",manager,otherProxy,otherBackground,NotificationStyleCheck.CardType.NOTIFICATION,1f,null);
        HookCallback.Param first=before(B+"ViewBlurProxy#applyConfigToPlatformBlur",proxy);
        HookCallback.Param second=before(B+"ViewBlurProxy#applyConfigToPlatformBlur",otherProxy);
        check(c.leftTopCornerRadius==13*density && c.leftBottomCornerRadius==6.5f*density &&
            other.leftTopCornerRadius==3.25f*density && other.leftBottomCornerRadius==0,
            "interleaved notifications keep their own animation roundness");
        after(B+"ViewBlurProxy#applyConfigToPlatformBlur",second);after(B+"ViewBlurProxy#applyConfigToPlatformBlur",first);
        before(NotificationStyleCheck.M+"blurForHeadsUp",manager,background,true);
        first=before(B+"ViewBlurProxy#applyConfigToMotionBlur",proxy);
        check(c.leftTopCornerRadius==25*density && c.leftBottomCornerRadius==12.5f*density,
            "heads-up switches radius without rescaling the row animation fraction");
        after(B+"ViewBlurProxy#applyConfigToMotionBlur",first);
        before(NotificationStyleCheck.M+"cancelBlurForHeadsUp",manager,background,true);
        StackHelper stack=new StackHelper();stack.view=background;
        stack.configMap.put(1,config());stack.configMap.put(2,config());
        HookCallback.Param created=CloudCheck.param(stack,null,background,null,null);after(STACK,created);
        for(BlurConfig layer:stack.configMap.values()) {
            Object nativeMix=layer.platformMixConfig;
            HookCallback.Param draw=before(DRAW,new Object(),layer,1f);
            check(layer.cornerRadius==13*density && layer.leftBottomCornerRadius==6.5f*density && layer.blurRadius==73,
                "stack layer receives the same corner, row roundness and blur as its main notification");
            check(((BlurMixConfig.BlurMixMultiWithShader)layer.platformMixConfig).foregroundShaderParam.topLayerColor==0x416688aa,
                "stack layer receives the notification tint during a direct drawable update");
            draw.failure=new IllegalStateException("renderer failure");after(DRAW,draw);
            check(layer.cornerRadius==47 && layer.blurRadius==0 && layer.platformMixConfig==nativeMix,
                "stack native material restores even after a failed draw");
        }
        int applied=stack.applied,refreshed=row.refreshed;NotificationCardHook.refresh();
        check(stack.applied>applied,"live settings also rebuild cached stack backgrounds");
        check(row.refreshed>refreshed,"live settings refresh notification clipping with stack material");
        b.putInt("notification_card_color",0);b.putInt("notification_card_corner",0);b.putInt("notification_card_radius",151);CloudCheck.config(b);
        for(BlurConfig layer:stack.configMap.values()) {
            HookCallback.Param draw=before(DRAW,new Object(),layer,1f);
            var mix=(BlurMixConfig.BlurMixMultiWithShader)layer.platformMixConfig;
            check(layer.cornerRadius==0 && layer.leftTopCornerRadius==0 && layer.blurRadius==151 &&
                mix.foregroundShaderParam.topMode==0 && mix.backgroundShaderParam.topMode==0,
                "existing stack layers follow zero tint, square corners and a new blur radius");after(DRAW,draw);
        }
        BlurConfig unrelated=config();Object unrelatedMix=unrelated.platformMixConfig;
        HookCallback.Param untracked=before(DRAW,new Object(),unrelated,1f);
        check(unrelated.cornerRadius==47 && unrelated.blurRadius==0 && unrelated.platformMixConfig==unrelatedMix,
            "direct drawable hook preserves unrelated system materials");after(DRAW,untracked);
        b.putBoolean("enabled",false);CloudCheck.config(b);
        for(BlurConfig layer:stack.configMap.values()) {
            HookCallback.Param draw=before(DRAW,new Object(),layer,1f);
            check(layer.cornerRadius==47 && layer.blurRadius==0,"module off restores native stack material");after(DRAW,draw);
        }
        if(!failures.isEmpty())throw new AssertionError(failures.size()+" expansion regressions: "+failures);
        System.out.println("Verified "+count+" notification expansion checks; device rendering remains unverified.");
    }
}
