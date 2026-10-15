package io.github.baishangui.thunder;

import android.content.SharedPreferences;
import android.util.Log;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.HashMap;
import io.github.libxposed.api.XposedInterface;

/** Registers real API 102 interceptor chains, with invocation-local state. */
public final class Api102Runtime {
    private static XposedInterface api;
    static void bind(XposedInterface framework) { api = framework; }
    static SharedPreferences remotePreferences() { return api.getRemotePreferences("settings"); }
    static String framework() { return api.getFrameworkName()+" "+api.getFrameworkVersion()+" / API "+api.getApiVersion(); }
    public static void findAndHookMethod(String name, ClassLoader loader, String method, Object... signature) {
        findAndHookMethod(Reflect.findClass(name, loader), method, signature);
    }
    public static void findAndHookMethod(Class<?> type, String name, Object... signature) {
        Class<?>[] types = new Class<?>[signature.length-1];
        for (int i=0; i<types.length; i++) types[i] = signature[i] instanceof String
            ? Reflect.findClass((String)signature[i], type.getClassLoader()) : (Class<?>)signature[i];
        try { install(type.getDeclaredMethod(name, types), (HookCallback)signature[types.length]); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(type.getName()+"."+name, e); }
    }
    public static void hookAllConstructors(Class<?> type, HookCallback callback) {
        for (var constructor : type.getDeclaredConstructors()) install(constructor, callback);
    }
    static void deoptimizeCallers(Class<?> type, String... names) {
        for (String name : names) {
            boolean found = false;
            for (Method method : type.getDeclaredMethods()) if (method.getName().equals(name)) {
                found = true;
                if (!api.deoptimize(method)) throw new IllegalStateException("deoptimize returned false: "+method);
            }
            if (!found) throw new IllegalStateException("Missing caller: "+type.getName()+"."+name);
        }
    }
    private static void install(Executable member, HookCallback callback) {
        // Registration is synchronous: failed hooks must not count as installed.
        api.hook(member).setId("thunder:"+member.toGenericString())
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Invocation p = new Invocation(chain.getThisObject(), chain.getArgs().toArray());
                try { callback.before(p); }
                catch (Throwable error) { ShadeHook.frameworkFailure(member.getName()+"/before", error); }
                if (!p.replaced) {
                    try { p.result = chain.proceed(p.args); }
                    catch (Throwable error) { p.error = error; }
                }
                try { callback.after(p); }
                catch (Throwable error) { ShadeHook.frameworkFailure(member.getName()+"/after", error); }
                if (p.error != null) throw p.error;
                return p.result;
            });
    }
    private static final class Invocation extends HookCallback.Param {
        Object result; Throwable error; boolean replaced;
        Invocation(Object object, Object[] args) { super(object, args, new HashMap<>()); }
        @Override public Object getResult() { return result; }
        @Override public boolean hasThrowable() { return error != null; }
        @Override public void setResult(Object value) { result=value; error=null; replaced=true; }
    }
    public static void log(String message) { api.log(Log.ERROR, "Thunder", message); }
}
