package com.oplus.systemui.plugins.qs.customize.view.tile;
import android.widget.FrameLayout;
import android.content.Context;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.oplusos.systemui.common.model.SpanSize;
public class OplusQSIconView extends FrameLayout {
    public final Proxy iconViewProxy=new Proxy();public BooleanState state;public int animatedUpdates;public boolean recycled,themeDisabled;
    public android.content.res.ColorStateList submittedTint;
    public void disableTheme(boolean value){themeDisabled=value;}
    public OplusQSIconView(Context c,SpanSize span,boolean background){super(c);}
    public void setIcon(BooleanState next,boolean animate){if(animate&&state!=null&&state.state!=next.state)animatedUpdates++;submittedTint=iconViewProxy.tint;state=next;}
    public void setIconScaleRatio(float ratio){}
    public void onRecycle(){recycled=true;}
    public static class Proxy {public boolean detail;public android.content.res.ColorStateList tint;public void setDetailTile(boolean value){detail=value;}public void setTintList(android.content.res.ColorStateList value){tint=value;} }
}
