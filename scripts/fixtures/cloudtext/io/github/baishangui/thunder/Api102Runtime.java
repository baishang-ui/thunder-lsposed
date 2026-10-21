package io.github.baishangui.thunder;
final class Api102Runtime {
 static HookCallback callback;static int replays;
 static void findAndHookMethod(Class<?> c,String name,Object...args){callback=(HookCallback)args[args.length-1];}
 static Object invokeOriginal(java.lang.reflect.Method method,Object receiver,Object...args){
  if(method.getDeclaringClass()!=android.widget.TextView.class||!method.getName().equals("onDraw"))throw new AssertionError("wrong base replay");
  replays++;((android.widget.TextView)receiver).originalOnDraw((android.graphics.Canvas)args[0]);return null;
 }
}
