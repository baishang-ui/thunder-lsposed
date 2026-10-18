package com.oplus.systemui.plugins.qs.customize.view.tile;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.List;

/** API-shaped stand-in only. Vendor spring/Lottie rendering is checked on firmware/device. */
public class OplusQSThreeStageLayout extends FrameLayout {
    public static boolean failConstruction;
    public final List<View> frameLayoutViews=new ArrayList<>(),iconViews=new ArrayList<>();
    public final List<Lottie> lottieViews=new ArrayList<>();
    public final View indicatorView=new View();
    public final Motion indicatorSpringTranslationX=new Motion(),springAnimationBounce=new Motion();
    public final List<Motion> iconSpringAnimatorSets=new ArrayList<>();
    public final List<Scale> scaleSyncAnimatorHelper=new ArrayList<>();
    public final List<Alpha> iconAlphaSpringAnimatorSets=new ArrayList<>();
    public final List<Press> pressScaleHelper=new ArrayList<>();
    public int selectedIndex=-1,selectionUpdates,drawableUpdates;public boolean isEditMode,cleared,disableTheme;
    public OplusQSThreeStageLayout(android.content.Context context) {
        super(context);if(failConstruction)throw new IllegalStateException("native shortcut initialization failed");addView(indicatorView);
        for(int i=0;i<3;i++) {
            final int index=i;FrameLayout frame=new FrameLayout(context);
            frame.setOnClickListener(v->{if(isEditMode||!frame.isEnabled()||index==selectedIndex)return;selectedIndex=index;setTag(2-index);if(click!=null)click.onClick(this);});
            frameLayoutViews.add(frame);addView(frame);iconViews.add(new ImageView(context));lottieViews.add(new Lottie());
            iconSpringAnimatorSets.add(new Motion());scaleSyncAnimatorHelper.add(new Scale());iconAlphaSpringAnimatorSets.add(new Alpha());pressScaleHelper.add(new Press());
        }
    }
    public void setSelectedIndex(int mode,CharSequence description){selectedIndex=2-mode;selectionUpdates++;frameLayoutViews.get(selectedIndex).setContentDescription(description);}
    public void setEditMode(boolean edit){isEditMode=edit;}
    public void updateContainerLayoutParams(int start,int end){}
    public void updateIconBgDrawable(){}
    public void onIconDrawableUpdate(boolean theme){drawableUpdates++;indicatorView.setBackground(new android.graphics.drawable.ColorDrawable(0xff778899));}
    public void tintColor(Integer color){}
    public int getActiveIconColor(boolean active){return active?0xffeeeeee:0xff777777;}
    public void clearRunnable(){cleared=true;}
    public void setTranslationXAndScale(){}
    public static class Motion {public boolean cancelled;public void cancel(){cancelled=true;}}
    public static class Scale {public final Motion spring=new Motion();private Motion getSpringAnim(){return spring;}}
    public static class Alpha {public final Motion alphaSpringAnim=new Motion(),colorSpringAnim=new Motion();}
    public static class Press {public final Motion spring=new Motion();public Motion getSpringAnimation(){return spring;}}
    public static class Lottie extends View {public boolean cancelled;public void cancelAnimation(){cancelled=true;}}
}
