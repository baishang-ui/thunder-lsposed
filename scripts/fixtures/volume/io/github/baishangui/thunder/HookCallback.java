package io.github.baishangui.thunder;
public class HookCallback {
 public static class Param { public Object thisObject;public Object[] args=new Object[0];public Throwable failure;private Object result;public boolean returnEarly;
 public Object getResult(){return result;}public void setResult(Object v){result=v;returnEarly=true;}public java.util.Map<String,Object> extras=new java.util.HashMap<>();public void setObjectExtra(String k,Object v){extras.put(k,v);}public Object getObjectExtra(String k){return extras.get(k);}public boolean hasThrowable(){return failure!=null;} }
 protected void afterHookedMethod(Param p)throws Throwable{} protected void beforeHookedMethod(Param p)throws Throwable{}
 public void after(Param p)throws Throwable{afterHookedMethod(p);}public void before(Param p)throws Throwable{beforeHookedMethod(p);}
}