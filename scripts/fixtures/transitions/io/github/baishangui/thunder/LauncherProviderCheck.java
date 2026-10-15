package io.github.baishangui.thunder;
import android.os.*;

public class LauncherProviderCheck {
    static void check(boolean value,String message) { StatusBarFollowCheck.check(value,message); }
    static void denied(Runnable call,String message) {
        try { call.run(); throw new AssertionError(message); }
        catch (SecurityException expected) { check(true,message); }
    }
    public static void main(String[] args) {
        SettingsProvider provider = new SettingsProvider();
        Binder endpoint = new Binder(); Bundle registration = new Bundle(); registration.putBinder("endpoint",endpoint);
        Binder.uid=3000;
        denied(() -> provider.call("overlay-register",null,registration),"unrelated UID cannot register a system overlay endpoint");
        denied(() -> provider.call("overlay-endpoint",null,null),"unrelated UID cannot retrieve surface access endpoint");
        Binder.uid=1000; provider.call("overlay-register",null,registration);
        Binder.uid=10234;
        check(provider.call("overlay-endpoint",null,null).getBinder("endpoint")==endpoint,"launcher can retrieve the live system endpoint");
        denied(() -> provider.call("overlay-register",null,registration),"launcher cannot replace the system endpoint");
        denied(() -> provider.call("settings",null,null),"launcher overlay access does not grant settings access");
        Bundle report = new Bundle(); report.putString("process","com.android.launcher"); report.putString("hits","frames=10");
        provider.call("report",null,report);
        check(android.content.Context.stores.get("runtime_launcher").getAll().get("hits").equals("frames=10"),"launcher frame diagnostics are stored separately");
        report.putString("process","system_server");
        denied(() -> provider.call("report",null,report),"launcher cannot impersonate system_server diagnostics");
        endpoint.die();
        check(provider.call("overlay-endpoint",null,null).getBinder("endpoint")==null,"dead endpoint is never handed to a restarting launcher");
    }
}
