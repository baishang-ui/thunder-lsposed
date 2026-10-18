package com.oplus.systemui.qs.base.res;
public class SepQSResPool {
    public static final SepQSResPool INSTANCE=new SepQSResPool();
    public Value getHighLightIconBg(){return new Value();}
    public static class Value {public Builder getValue(){return new Builder();}}
    // A covariant return generates the same synthetic bridge as the firmware builder.
    public static class Builder implements com.oplus.systemui.qs.base.res.model.QSDrawableBuilder {
        public android.graphics.drawable.ColorDrawable build(android.view.View view,boolean blur,Integer color){return new Selection();}
    }
    public static class Selection extends android.graphics.drawable.ColorDrawable {
        public Object pathProvider;public int pathUpdates;public Selection(){super(0xff778899);}public void setPathProvider(Object value){pathProvider=value;}public void invalidatePath(){pathUpdates++;}
    }
}
