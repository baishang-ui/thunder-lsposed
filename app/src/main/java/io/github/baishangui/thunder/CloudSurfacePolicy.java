package io.github.baishangui.thunder;

/** Pure settled-surface lifecycle and native endpoint geometry rules. */
final class CloudSurfacePolicy {
    static boolean keep(boolean module,boolean cloud,boolean animation,float scale){return module&&cloud&&animation&&scale>0;}
    static float radius(float width,float height,float nativeRadius,boolean custom,int dp,float density){return Math.max(0,Math.min(custom?dp*density:nativeRadius,Math.min(width,height)/2f));}
    static final class CapsuleBackground<T> {
        private T current;
        T resolve(T incoming,java.util.function.Supplier<T> fallback) {
            if(incoming!=null)current=incoming;
            else if(current==null)current=fallback.get();
            return current;
        }
    }
}
