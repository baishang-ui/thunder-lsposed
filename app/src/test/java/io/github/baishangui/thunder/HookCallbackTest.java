package io.github.baishangui.thunder;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class HookCallbackTest {
    private static class Param extends HookCallback.Param {
        Object result;
        Param(Map<String,Object> extras){super(null,new Object[0],extras);}
        @Override public Object getResult(){return result;}
        @Override public void setResult(Object value){result=value;}
        @Override public boolean hasThrowable(){return false;}
    }
    @Test public void unusedExtrasNeedNoMap()throws Exception {
        Param param=new Param(null);
        assertNull(param.getObjectExtra("absent"));
        var field=HookCallback.Param.class.getDeclaredField("extras");field.setAccessible(true);
        assertNull(field.get(param));
    }
    @Test public void beforeAndAfterShareOnlyTheirOwnInvocationExtras()throws Throwable {
        HookCallback callback=new HookCallback(){
            @Override protected void beforeHookedMethod(HookCallback.Param p){p.setObjectExtra("saved",p.getResult());}
            @Override protected void afterHookedMethod(HookCallback.Param p){p.setResult(p.getObjectExtra("saved"));}
        };
        Param outer=new Param(null),inner=new Param(null);
        outer.setResult("outer");inner.setResult("inner");
        callback.before(outer);callback.before(inner);
        outer.setResult(null);inner.setResult(null);
        callback.after(inner);callback.after(outer);
        assertEquals("outer",outer.getResult());assertEquals("inner",inner.getResult());
        outer.setObjectExtra("saved",null);assertNull(outer.getObjectExtra("saved"));
    }
    @Test public void suppliedMapRemainsShared() {
        Map<String,Object> extras=new HashMap<>();extras.put("existing",42);
        Param param=new Param(extras);assertEquals(42,param.getObjectExtra("existing"));
        param.setObjectExtra("next",true);assertEquals(true,extras.get("next"));
    }
}
