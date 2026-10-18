package io.github.baishangui.thunder;

import java.util.Map;
import java.util.HashMap;

/** Rendering logic's framework-neutral callback contract; registered by Api102Runtime. */
public abstract class HookCallback {
    protected void beforeHookedMethod(Param param) throws Throwable { }
    protected void afterHookedMethod(Param param) throws Throwable { }
    public final void before(Param param) throws Throwable { beforeHookedMethod(param); }
    public final void after(Param param) throws Throwable { afterHookedMethod(param); }

    public abstract static class Param {
        public final Object thisObject;
        public final Object[] args;
        private Map<String,Object> extras;
        protected Param(Object object, Object[] args, Map<String,Object> extras) {
            this.thisObject = object; this.args = args; this.extras = extras;
        }
        public abstract Object getResult();
        public abstract void setResult(Object value);
        public abstract boolean hasThrowable();
        public final void setObjectExtra(String key, Object value) {
            if(extras==null)extras=new HashMap<>();
            extras.put(key,value);
        }
        public final Object getObjectExtra(String key) { return extras==null?null:extras.get(key); }
    }
}
