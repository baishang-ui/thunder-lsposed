package com.oplus.systemui.plugins.seedling.animatorscenes;
import com.oplus.systemui.plugins.seedling.engine.animatorengine.h;

/** C17 B0.invoke wraps the concrete scene and a parallel fraction-condition clock. */
public class B0 {
    public final Object b;
    private final h scene;
    public final Z condition=new Z();
    public B0(Object builder,h scene){b=builder;this.scene=scene;}
    public Object invoke(){h wrapper=new h();wrapper.o.add(scene);wrapper.o.add(condition);return wrapper;}
}
