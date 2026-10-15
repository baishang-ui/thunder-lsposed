package io.github.baishangui.thunder;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.view.SurfaceControl;
import java.util.Locale;

/** Firmware PLQ110_17.0.0.104: append to the actual launcher frame transaction. */
final class LauncherStatusBarHook {
    private static final String IMPLEMENTATION = "statusbar-overlay/r4";
    private static final java.lang.ref.Cleaner cleaner = java.lang.ref.Cleaner.create();
    private static final Binder client = new Binder();
    private static final ThreadLocal<Object> frame = new ThreadLocal<>();
    private static volatile IBinder endpoint;
    private static Context context;
    private static Handler worker;
    private static boolean installed;
    private static volatile long frames, sessions, missing;
    private static volatile String error = "", last = "等待应用动画";
    private static final Runnable reportTask = LauncherStatusBarHook::report;
    private static final Runnable connectTask = LauncherStatusBarHook::connect;
    private static final Object NO_SESSION = new Object();
    private static final String SESSION = "thunder.statusbar.overlay.session";
    private static final class Session {
        final IBinder endpoint;
        final SurfaceControl overlay;
        final long generation;
        final int task, width, height;
        final float[] values = new float[9];
        boolean acknowledged, rejected;
        Session(IBinder endpoint, Bundle response, int task) {
            this.endpoint = endpoint; this.task = task;
            overlay = response.getParcelable("surface", SurfaceControl.class);
            generation = response.getLong("generation");
            width = response.getInt("width"); height = response.getInt("height");
        }
    }
    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        installed = true;
        try {
            Class<?> runner = Reflect.findClass("ma.p0", loader);
            Class<?> params = Reflect.findClass("xi.q$b", loader);
            Api102Runtime.findAndHookMethod(Application.class, "attach", Context.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) { start((Context) p.args[0]); }
            });
            Api102Runtime.findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", Application.class, new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) { start((Application) p.args[0]); }
            });
            Api102Runtime.findAndHookMethod(runner, "run", new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    p.setObjectExtra("thunder.frame.previous", frame.get()); frame.set(p.thisObject);
                }
                @Override protected void afterHookedMethod(Param p) {
                    Object previous = p.getObjectExtra("thunder.frame.previous");
                    if (previous == null) frame.remove(); else frame.set(previous);
                }
            });
            Api102Runtime.findAndHookMethod(params, "i", Matrix.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (p.hasThrowable() || frame.get() == null) return;
                    try { append(frame.get(), p.thisObject, (Matrix) p.args[0]); }
                    catch (Throwable failure) {
                        String message = "逐帧变换失败：" + failure;
                        if (!message.equals(error)) log(message);
                        error = message;
                        Object target = Reflect.getObjectField(frame.get(), "a");
                        Object cached = Reflect.getAdditionalInstanceField(target, SESSION);
                        if (cached instanceof Session session && !session.rejected) {
                            session.rejected = true; worker.post(() -> notifyServer(session, StatusBarOverlayIpc.REJECT));
                        }
                        scheduleReport();
                    }
                }
            });
            Api102Runtime.deoptimizeCallers(runner, "run");
            Class<?> activityThread = Reflect.findClass("android.app.ActivityThread", loader);
            Api102Runtime.deoptimizeCallers(activityThread, "handleBindApplication");
            var currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object current = currentApplication.invoke(null);
            if (current instanceof Application application) start(application);
            log("hooks-ready ma.p0.run -> xi.q$b.i(Matrix)");
        } catch (Throwable failure) { error = "桌面接口安装失败：" + failure; log(error); }
    }
    private static synchronized void start(Context application) {
        if (worker != null) return;
        context = application;
        HandlerThread thread = new HandlerThread("Thunder-launcher-overlay"); thread.start();
        worker = new Handler(thread.getLooper()); worker.post(connectTask);
    }
    private static void connect() {
        try {
            if (endpoint == null || !endpoint.isBinderAlive()) {
                Bundle registry = context.getContentResolver().call(Uri.parse("content://" + SettingsProvider.AUTHORITY),
                    "overlay-endpoint", null, null);
                IBinder server = registry == null ? null : registry.getBinder("endpoint");
                if (server == null) throw new IllegalStateException("等待系统框架发布独立图层接口");
                Bundle result = StatusBarOverlayIpc.call(server, StatusBarOverlayIpc.CONNECT, client, -1, 0);
                if (result == null || !result.getBoolean("connected")) throw new IllegalStateException("系统框架接口未就绪");
                server.linkToDeath(() -> { endpoint = null; worker.post(connectTask); }, 0);
                endpoint = server; error = ""; log("connected");
            }
        } catch (Throwable failure) { error = "桌面连接：" + failure; }
        scheduleReport();
        if (endpoint == null && !worker.hasCallbacks(connectTask)) worker.postDelayed(connectTask, 5000);
    }
    private static synchronized void append(Object runner, Object params, Matrix matrix) throws Exception {
        IBinder server = endpoint;
        if (server == null) return;
        Object target = Reflect.getObjectField(runner, "a");
        // ma.p0 also writes auxiliary surfaces. Follow only its application's d leash.
        if (Reflect.getObjectField(params, "a") != Reflect.getObjectField(target, "d")) return;
        Object cached = Reflect.getAdditionalInstanceField(target, SESSION);
        if (cached == NO_SESSION) return;
        Session session = (Session) cached;
        if (session == null) {
            int task = Reflect.getIntField(target, "a");
            Bundle response = StatusBarOverlayIpc.call(server, StatusBarOverlayIpc.ACQUIRE, client, task, 0);
            if (response == null || !response.containsKey("surface")) {
                Reflect.setAdditionalInstanceField(target, SESSION, NO_SESSION);
                missing++; last = "任务 " + task + " 没有可跟随的系统会话"; scheduleReport(); return;
            }
            session = new Session(server, response, task);
            Reflect.setAdditionalInstanceField(target, SESSION, session);
            // Release the received native handle when the remote target is released.
            // The server owns removal from the scene graph, including after process death.
            cleaner.register(target, session.overlay::release);
            sessions++;
        }
        if (session.rejected || session.overlay == null || !session.overlay.isValid()) return;
        Object helper = Reflect.getObjectField(runner, "b");
        Rect source = (Rect) Reflect.callMethod(helper, "f");
        matrix.getValues(session.values);
        if (!StatusBarFrameGeometry.fullscreen(source.left, source.top, source.right, source.bottom,
                session.width, session.height, session.values)) {
            session.rejected = true;
            Session rejected = session;
            worker.post(() -> notifyServer(rejected, StatusBarOverlayIpc.REJECT));
            last = "源坐标或矩阵不支持 source=" + source + " expected=0,0," + session.width + "," + session.height + "，已请求恢复"; scheduleReport(); return;
        }
        Object wrapper = Reflect.getObjectField(params, "b");
        SurfaceControl.Transaction transaction = (SurfaceControl.Transaction) Reflect.getObjectField(wrapper, "a");
        // Same transaction, same matrix. No application crop, alpha or corner radius.
        Reflect.callMethod(transaction, "setMatrix", session.overlay, matrix, session.values);
        Reflect.callMethod(transaction, "show", session.overlay);
        frames++;
        last = String.format(Locale.ROOT, "task=%d session=%d scale=%.4f,%.4f xy=%.2f,%.2f",
            session.task, session.generation, session.values[0], session.values[4], session.values[2], session.values[5]);
        if (!session.acknowledged) {
            session.acknowledged = true;
            Session first = session;
            transaction.addTransactionCommittedListener(command -> worker.post(command),
                () -> notifyServer(first, StatusBarOverlayIpc.COMMITTED));
        }
        scheduleReport();
    }
    private static void notifyServer(Session session, int code) {
        try { StatusBarOverlayIpc.call(session.endpoint, code, client, session.task, session.generation); }
        catch (Throwable failure) { error = "提交确认失败：" + failure; }
    }
    private static void scheduleReport() {
        if (worker != null && !worker.hasCallbacks(reportTask)) worker.postDelayed(reportTask, 700);
    }
    private static void report() {
        Bundle data = new Bundle();
        data.putString("process", "com.android.launcher");
        data.putString("hooks", "ma.p0.run; xi.q$b.i(Matrix)");
        data.putString("hits", "动画 " + sessions + " 次；同帧写入 " + frames + " 帧；无会话 " + missing + " 次");
        data.putString("effective", "impl=" + IMPLEMENTATION + "; connected=" + (endpoint != null) + "; " + last);
        data.putString("error", error); data.putInt("pid", Process.myPid());
        data.putInt("hook_version", BuildConfig.VERSION_CODE);
        try { context.getContentResolver().call(Uri.parse("content://" + SettingsProvider.AUTHORITY), "report", null, data); }
        catch (Throwable failure) { log("report " + failure); }
    }
    private static void log(String message) {
        try { Api102Runtime.log("Thunder: launcher-overlay " + IMPLEMENTATION + " " + message); }
        catch (Throwable ignored) { }
    }
    private LauncherStatusBarHook() { }
}
