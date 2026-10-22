package io.github.baishangui.thunder;

import java.util.ArrayList;

/** Live content weights in a stable coordinate space, independent of the host's switch graph. */
final class CloudContentSwitch<T> {
    static final class Layer<T> {
        final T source;
        float opacity,offset;
        private CloudMotion motion;
        private CloudMorphHook.Frame velocity;
        Layer(T source,float opacity,float offset){this.source=source;this.opacity=opacity;this.offset=offset;}
    }
    final ArrayList<Layer<T>> layers=new ArrayList<>();
    private T selected;

    boolean select(T source,int duration,float distance) {
        if(source==selected)return false;
        if(selected==null) {
            selected=source;layers.add(new Layer<>(source,1,0));return false;
        }
        Layer<T> incoming=null;
        for(Layer<T> layer:layers)if(layer.source==source)incoming=layer;
        if(incoming==null){incoming=new Layer<>(source,0,distance);layers.add(incoming);}
        selected=source;
        // A data burst may replace an incoming item before its first frame.
        // A zero weight with zero opacity tangent can never contribute to the
        // outgoing fade. Release that source rather than chaining hidden trees.
        Layer<T> keep=incoming;
        layers.removeIf(layer->layer!=keep&&layer.opacity==0&&
            (layer.velocity==null||layer.velocity.y()==0));
        for(Layer<T> layer:layers) {
            boolean active=layer==incoming;
            // Use identical critical responses for every weight: their sum and
            // its derivative remain one and zero, including A -> B -> A reversals.
            layer.motion=new CloudMotion(state(layer.offset,layer.opacity),state(active?0:-distance,active?1:0),
                layer.velocity,duration,2,0,0,true);
        }
        return true;
    }
    void sample(float fraction) {
        for(Layer<T> layer:layers) {
            if(layer.motion==null)continue;
            var frame=layer.motion.frame(fraction);
            layer.offset=frame.x();layer.opacity=frame.y();layer.velocity=layer.motion.velocity(fraction);
        }
    }
    void finish() {
        layers.removeIf(layer->layer.source!=selected);
        for(Layer<T> layer:layers){layer.opacity=1;layer.offset=0;layer.velocity=null;layer.motion=null;}
    }
    void clear(){layers.clear();selected=null;}
    private static CloudMorphHook.Frame state(float offset,float opacity){return new CloudMorphHook.Frame(offset,opacity,1,1,0,0);}
}
