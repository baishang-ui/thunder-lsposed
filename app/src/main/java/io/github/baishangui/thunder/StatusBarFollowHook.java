package io.github.baishangui.thunder;

import android.content.Context;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.view.SurfaceControl;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** ColorOS 17 window-manager adapter. All surface ownership is serialized by mGlobalLock. */
final class StatusBarFollowHook {
    private static final String IMPLEMENTATION = "statusbar-overlay/r4";
    private static String stage = "not-started";
    private static final String WM = "com.android.server.wm.";
    private static boolean installed, ready, enabled, faulted;
    private static Object globalLock, activeToken, activeDisplay;
    private static IBinder launcherClient;
    private static long generation, acquired, committed;
    private static Overlay overlay;
    private static int launcherUid = -1;
    private static final Runnable publishTask = StatusBarFollowHook::publish;
    private static final class Overlay {
        final SurfaceControl surface;
        final long generation;
        final int task, width, height;
        boolean acquired, committed;
        Overlay(SurfaceControl surface, long generation, int task, int width, int height) {
            this.surface = surface; this.generation = generation; this.task = task;
            this.width = width; this.height = height;
        }
    }
    private static final Binder endpoint = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code < StatusBarOverlayIpc.CONNECT || code > StatusBarOverlayIpc.REJECT)
                return super.onTransact(code, data, reply, flags);
            data.enforceInterface(StatusBarOverlayIpc.DESCRIPTOR);
            if (launcherUid < 0 || Binder.getCallingUid() != launcherUid)
                throw new SecurityException("Launcher UID required");
            IBinder client = data.readStrongBinder();
            int task = data.readInt(); long requestedGeneration = data.readLong();
            if (client == null || !client.isBinderAlive()) throw new SecurityException("Live launcher client required");
            Bundle result = new Bundle();
            synchronized (globalLock) {
                if (code == StatusBarOverlayIpc.CONNECT) {
                    if (launcherClient != client) {
                        client.linkToDeath(() -> wmHandler.post(() -> guarded(() -> {
                            if (launcherClient == client) {
                                launcherClient = null; controller.cancel();
                                reason = "桌面进程退出，已恢复"; requestReport();
                            }
                        })), 0);
                        if (launcherClient != null) controller.cancel();
                        launcherClient = client;
                    }
                    result.putBoolean("connected", ready && !faulted);
                } else if (launcherClient != client) throw new SecurityException("Unregistered launcher client");
                else if (overlay != null && activeToken != null && controller.owns(surface(activeToken)) && enabled && !faulted && overlay.task == task) {
                    if (code == StatusBarOverlayIpc.ACQUIRE) {
                        if (!overlay.acquired) { overlay.acquired = true; acquired++; }
                        result.putParcelable("surface", overlay.surface);
                        result.putLong("generation", overlay.generation);
                        result.putInt("width", overlay.width); result.putInt("height", overlay.height);
                    } else if (requestedGeneration == overlay.generation) {
                        if (code == StatusBarOverlayIpc.COMMITTED && !overlay.committed) {
                            overlay.committed = true; committed++; reason = "已收到桌面同帧提交确认";
                        } else if (code == StatusBarOverlayIpc.REJECT) {
                            controller.cancel(); reason = "桌面源坐标不匹配，已恢复";
                        }
                    }
                }
                requestReport();
            }
            reply.writeNoException(); reply.writeBundle(result); return true;
        }
    };
    private static Context context;
    private static Object service;
    private static Handler wmHandler, worker;
    private static LiveSettings liveSettings;
    private static Method fluidCloud;
    private static long revision = -1, followed, skipped;
    private static String reason = "等待配置", error = "";
    private static volatile Bundle pendingReport;
    // Accessed only by the report worker; failed IPC never disables animations.
    private static int reportFailures;
    private static boolean reportAcknowledged;
    private static final ThreadLocal<Object> reservedFade = new ThreadLocal<>();
    private static final Runnable reportTask = StatusBarFollowHook::report;
    private static final Runnable watchdog = StatusBarFollowHook::timeout;
    private static void timeout() { guarded(() -> {
        if (activeToken != null && controller.owns(surface(activeToken))) {
            controller.cancel();
            reason = "动画超过 6 秒，已恢复；等待原过渡结束";
            requestReport();
        }
    }); }
    private static final StatusBarTransition controller = new StatusBarTransition(new StatusBarTransition.Surfaces() {
        public boolean valid(Object surface) {
            Object handle = surface instanceof OriginalParent original ? original.surface : surface;
            return handle instanceof SurfaceControl s && s.isValid();
        }
        public void reparent(Object transaction, Object child, Object parent) {
            SurfaceControl.Transaction tx = (SurfaceControl.Transaction) transaction;
            if (parent instanceof OriginalParent original) {
                tx.reparent((SurfaceControl) child, original.surface).setLayer((SurfaceControl) child, original.layer);
                // Transaction.remove() releases the Java handle immediately, even when
                // this is only BUILDING a finish transaction. Detach at apply time;
                // release our handle only after the native transition terminates.
                if (original.overlay != null && original.overlay.isValid()) tx.reparent(original.overlay, null);
            } else {
                tx.reparent((SurfaceControl) child, (SurfaceControl) parent).setLayer((SurfaceControl) child, Integer.MAX_VALUE);
            }
        }
        public void restore(Object child, Object parent) {
            try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                reparent(transaction, child, parent);
                transaction.apply();
            }
        }
        public void committed(Object transaction, Runnable callback) {
            ((SurfaceControl.Transaction) transaction).addTransactionCommittedListener(
                command -> wmHandler.post(command), () -> guarded(callback));
        }
    });

    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        installed = true;
        stage = "installing";
        logStage();
        try {
            Class<?> wms = Reflect.findClass(WM + "WindowManagerService", loader);
            // Capture the actual service, never construct framework services ourselves.
            Api102Runtime.hookAllConstructors(wms, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (!p.hasThrowable()) capture(p.thisObject);
                }
            });
            Class<?> transition = Reflect.findClass(WM + "Transition", loader);
            Class<?> info = Reflect.findClass("android.window.TransitionInfo", loader);
            Class<?> display = Reflect.findClass(WM + "DisplayContent", loader);
            Class<?> token = Reflect.findClass(WM + "WindowToken", loader);
            Class<?> lite = Reflect.findClass(WM + "LiteOSAnimController", loader);
            Class<?> light = Reflect.findClass(WM + "LightOSFadeAnimController", loader);
            Class<?> fade = Reflect.findClass(WM + "FadeAnimationController", loader);
            Class<?> policy = Reflect.findClass(WM + "DisplayPolicy", loader);
            fluidCloud = Reflect.findClass(WM + "AdaptiveSmoothAnimUtils", loader)
                .getDeclaredMethod("hasFluidCloudInStatusBar", Context.class);
            fluidCloud.setAccessible(true);
            Api102Runtime.findAndHookMethod(transition, "buildFinishTransaction", SurfaceControl.Transaction.class,
                info, Array.newInstance(display, 0).getClass(), new HookCallback() {
                    @Override protected void afterHookedMethod(Param p) {
                        guarded(() -> {
                            if (p.hasThrowable()) { controller.cancel(); return; }
                            prepare(p.thisObject, p.args[1], p.args[0]);
                        });
                    }
                });
            Api102Runtime.findAndHookMethod(transition, "buildCleanupTransaction", SurfaceControl.Transaction.class,
                info, new HookCallback() {
                    @Override protected void afterHookedMethod(Param p) {
                        guarded(() -> controller.cleanup(p.args[1], p.args[0]));
                    }
                });
            HookCallback terminal = new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    guarded(() -> {
                        boolean wasBusy = controller.busy();
                        controller.finish(p.thisObject);
                        if (wasBusy && !controller.busy()) {
                            activeToken = null; activeDisplay = null;
                            if (overlay != null) { overlay.surface.release(); overlay = null; }
                            wmHandler.removeCallbacks(watchdog);
                            reason = enabled ? "过渡结束，状态栏已恢复" : "跟随已关闭，状态栏已恢复";
                            requestReport();
                        }
                    });
                }
            };
            Api102Runtime.findAndHookMethod(transition, "finishTransition", Reflect.findClass(WM + "ActionChain", loader), terminal);
            Api102Runtime.findAndHookMethod(transition, "abort", terminal);
            Api102Runtime.findAndHookMethod(transition, "cleanUpOnFailure", terminal);
            // ColorOS starts its independent fade before native finish transactions are built.
            // Reserve only a validated token for that synchronous call; never suppress globally.
            Api102Runtime.findAndHookMethod(lite, "startAsyncFadeAnimIfNeed", transition,
                SurfaceControl.Transaction.class, info, new HookCallback() {
                    @Override protected void beforeHookedMethod(Param p) {
                        p.setObjectExtra("previousFade", reservedFade.get());
                        guarded(() -> {
                            if (ready && enabled && !faulted && launcherClient != null && launcherClient.isBinderAlive() && !controller.busy()) {
                                Scene scene = scene(p.args[2]);
                                if (scene != null) reservedFade.set(scene.token);
                            }
                        });
                    }
                    @Override protected void afterHookedMethod(Param p) {
                        Object previous = p.getObjectExtra("previousFade");
                        if (previous == null) reservedFade.remove(); else reservedFade.set(previous);
                    }
                });
            Api102Runtime.findAndHookMethod(fade, "fadeWindowToken", boolean.class, token, int.class, new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    guarded(() -> {
                        if (ready && !faulted && enabled && (reservedFade.get() == p.args[1] ||
                                (activeToken == p.args[1] && controller.owns(surface(activeToken))))) p.setResult(null);
                    });
                }
            });
            Api102Runtime.findAndHookMethod(policy, "finishPostLayoutPolicyLw", new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    guarded(() -> {
                        if (activeDisplay != null && activeToken != null && controller.owns(surface(activeToken)) && !safeDisplay(activeDisplay)) {
                            controller.cancel(); wmHandler.removeCallbacks(watchdog); reason = "系统界面状态变化，已恢复"; requestReport();
                        }
                    });
                }
            });
            // Calls within services.jar/oplus-services.jar may already be compiled/inlined.
            Api102Runtime.deoptimizeCallers(transition, "onTransactionReady", "finishTransition", "cleanUpOnFailure");
            Api102Runtime.deoptimizeCallers(lite, "onTransactionReady", "startAsyncFadeAnimIfNeed");
            Api102Runtime.deoptimizeCallers(light, "fadeAllWindowToken");
            ready = true;
            stage = "hooks-ready";
            logStage();
        } catch (Throwable failure) {
            stage = "install-failed";
            faulted = true;
            error = "系统过渡接口安装失败：" + failure;
            log(error);
        }
    }

    private static void capture(Object instance) {
        if (service != null) return;
        try {
            Object lock = Reflect.getObjectField(instance, "mGlobalLock");
            synchronized (lock) {
                if (service != null) return;
                context = (Context) Reflect.getObjectField(instance, "mContext");
                wmHandler = (Handler) Reflect.getObjectField(instance, "mH");
                service = instance; globalLock = lock;
                try { launcherUid = context.getPackageManager().getPackageUid("com.android.launcher", 0); }
                catch (Exception ignored) { launcherUid = -1; }
                stage = "wms-captured";
                logStage();
                HandlerThread thread = new HandlerThread("Thunder-window-transitions"); thread.start();
                worker = new Handler(thread.getLooper());
                worker.post(publishTask);
                worker.post(() -> {
                    try {
                        liveSettings = new LiveSettings(Api102Runtime.remotePreferences(), worker,
                            StatusBarFollowHook::settingsLoaded, failure -> wmHandler.post(() -> guarded(() -> {
                                stage = "settings-failed"; logStage(); enabled = false; controller.cancel(); error = "远程配置不可用：" + failure; requestReport();
                            })));
                        liveSettings.start();
                    } catch (Throwable failure) {
                        wmHandler.post(() -> guarded(() -> { enabled = false; error = "配置初始化失败：" + failure; requestReport(); }));
                    }
                });
                requestReport();
            }
        } catch (Throwable failure) { stage = "wms-capture-failed"; logStage(); log("窗口服务初始化失败：" + failure); }
    }
    private static void settingsLoaded(Map<String, ?> values) {
        boolean next = !Boolean.FALSE.equals(values.get("enabled")) && Boolean.TRUE.equals(values.get("status_bar_follow_app"));
        long nextRevision = (Long) values.get("revision");
        wmHandler.post(() -> guarded(() -> {
            revision = nextRevision; enabled = next;
            stage = "settings-loaded";
            logStage();
            if (!faulted) error = "";
            if (!enabled) { controller.cancel(); wmHandler.removeCallbacks(watchdog); }
            reason = enabled ? "等待桌面与全屏应用过渡" : "跟随已关闭";
            requestReport();
        }));
    }
    private record OriginalParent(SurfaceControl surface, int layer, SurfaceControl overlay) { }
    private record Scene(Object token, OriginalParent parent, int task, Object display, Rect bounds) { }
    private static String sceneRejection = "";
    private static Scene reject(String detail) { sceneRejection = detail; return null; }
    private static Scene scene(Object info) {
        int type = number(info, "getType"), flags = number(info, "getFlags");
        boolean recents = type == 1021 && flags == 128;
        if (number(info, "getRootCount") != 1 || (flags != 0 && !recents)) return reject("过渡根或标记不支持 type=" + type + " flags=" + flags);
        if (type == 1021 && !recents) return reject("手势过渡标记不匹配");
        Object root = Reflect.callMethod(info, "getRoot", 0);
        Object offset = Reflect.callMethod(root, "getOffset");
        if (number(root, "getDisplayId") != 0 || Reflect.getIntField(offset, "x") != 0 || Reflect.getIntField(offset, "y") != 0) return reject("过渡根不是主屏原点");
        Object display = Reflect.callMethod(service, "getDefaultDisplayContentLocked");
        if (display == null || !safeDisplay(display)) return reject("系统界面保护：" + displayRejection);
        Rect bounds = (Rect) Reflect.callMethod(display, "getBounds");
        if (bounds.left != 0 || bounds.top != 0 || bounds.bottom <= bounds.right) return reject("显示区域不是原点竖屏");
        List<?> changes = (List<?>) Reflect.callMethod(info, "getChanges");
        List<StatusBarTransition.Candidate> candidates = new ArrayList<>();
        List<Object> taskChanges = new ArrayList<>();
        for (Object change : changes) {
            Object task = Reflect.callMethod(change, "getTaskInfo");
            if (task == null) {
                // Wallpaper and descendants of participating tasks do not choose the animation owner.
                if ((number(change, "getFlags") & 2) != 0 || Reflect.callMethod(change, "getParent") != null) continue;
                return reject("存在未归属任务的系统窗口变化 flags=" + number(change, "getFlags"));
            }
            Rect start = (Rect) Reflect.callMethod(change, "getStartAbsBounds");
            Rect end = (Rect) Reflect.callMethod(change, "getEndAbsBounds");
            candidates.add(new StatusBarTransition.Candidate(number(task, "getActivityType"), number(task, "getWindowingMode"),
                number(change, "getMode"), Reflect.getIntField(task, "displayId"), sameBounds(start, bounds) && sameBounds(end, bounds),
                number(change, "getStartRotation") != number(change, "getEndRotation")));
            taskChanges.add(change);
        }
        int selected = StatusBarTransition.select(number(info, "getType"), candidates);
        if (selected < 0) return reject("任务组合不支持 type=" + type + " candidates=" + candidates);
        Object policy = Reflect.callMethod(display, "getDisplayPolicy");
        Object window = Reflect.callMethod(policy, "getStatusBar");
        if (window == null || !yes(window, "isVisible")) return reject("状态栏窗口当前不可见");
        // InsetsSourceProvider keeps a normal control leash on WindowState even
        // while the bar is idle. We move its ancestor token, preserving that
        // child leash. Other WindowState animations still own their geometry.
        if (Reflect.callMethod(window, "getAnimationLeash") != null &&
                number(Reflect.getObjectField(window, "mSurfaceAnimator"), "getAnimationType") != 32) return reject("状态栏窗口有非 Insets 动画");
        Object token = Reflect.getObjectField(window, "mToken");
        if (token == null || Reflect.callMethod(token, "getAnimationLeash") != null ||
                Reflect.getObjectField(token, "mLastRelativeToLayer") != null ||
                yes(Reflect.getObjectField(token, "mSurfaceAnimator"), "hasLeash")) return reject("状态栏 Token 有动画或相对层级归属");
        Object parent = Reflect.getObjectField(token, "mParent");
        if (parent == null || !sameBounds((Rect) Reflect.callMethod(parent, "getBounds"), bounds)) return reject("状态栏父区域不是完整显示区域");
        return new Scene(token, new OriginalParent((SurfaceControl) surface(parent), Reflect.getIntField(token, "mLastLayer"), null),
            Reflect.getIntField(Reflect.callMethod(taskChanges.get(selected), "getTaskInfo"), "taskId"), display, bounds);
    }
    private static String displayRejection = "";
    private static boolean safeDisplay(Object display) {
        Object policy = Reflect.callMethod(display, "getDisplayPolicy");
        if (yes(policy, "isKeyguardShowing") || yes(policy, "isKeyguardOccluded") ||
                Reflect.callMethod(display, "getAsyncRotationController") != null || yes(display, "hasFixedRotationTransientLaunch")) { displayRejection = "锁屏或旋转"; return false; }
        Object shade = Reflect.getObjectField(policy, "mNotificationShade");
        if (shade != null && yes(shade, "isVisible")) { displayRejection = "通知面板窗口可见"; return false; }
        try { boolean active = (Boolean) fluidCloud.invoke(null, context); displayRejection = active ? "活动流体云" : ""; return !active; }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("fluid cloud guard", failure); }
    }
    private static void prepare(Object owner, Object info, Object finish) {
        if (!ready || faulted || !enabled) return;
        if (launcherClient == null || !launcherClient.isBinderAlive()) {
            skipped++; reason = "等待桌面逐帧 Hook 连接（需桌面作用域）"; requestReport(); return;
        }
        if (controller.busy()) {
            controller.cancel(); wmHandler.removeCallbacks(watchdog); skipped++; reason = "重叠过渡，已恢复并跳过"; requestReport(); return;
        }
        Scene scene = scene(info);
        if (scene == null) { skipped++; reason = "跳过：" + sceneRejection; log("overlay-scene " + reason); requestReport(); return; }
        activeToken = scene.token; activeDisplay = scene.display;
        Object builder = Reflect.newInstance(Reflect.findClass("android.view.SurfaceControl$Builder", SurfaceControl.class.getClassLoader()));
        Reflect.callMethod(builder, "setName", "Thunder-statusbar-overlay-" + (++generation));
        Reflect.callMethod(builder, "setContainerLayer");
        Reflect.callMethod(builder, "setParent", scene.parent.surface);
        Reflect.callMethod(builder, "setHidden", true);
        SurfaceControl container = (SurfaceControl) Reflect.callMethod(builder, "build");
        overlay = new Overlay(container, generation, scene.task, scene.bounds.right, scene.bounds.bottom);
        OriginalParent original = new OriginalParent(scene.parent.surface, scene.parent.layer, container);
        SurfaceControl.Transaction start = (SurfaceControl.Transaction) Reflect.getObjectField(owner, "mStartTransaction");
        start.setLayer(container, scene.parent.layer);
        if (controller.prepare(owner, info, surface(scene.token), original, container, start, finish)) {
            followed++; reason = "独立图层已准备，等待桌面首帧";
            Overlay prepared = overlay;
            wmHandler.postDelayed(() -> guarded(() -> {
                if (overlay == prepared && !prepared.committed && activeToken != null && controller.owns(surface(activeToken))) {
                    controller.cancel(); reason = "桌面首帧在 500ms 内未提交，已恢复";
                    log("overlay-first-frame-timeout task=" + prepared.task + " acquired=" + prepared.acquired);
                    requestReport();
                }
            }), 500);
            wmHandler.removeCallbacks(watchdog); wmHandler.postDelayed(watchdog, 6000);
        } else {
            try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) { tx.reparent(container, null).apply(); container.release(); }
            overlay = null; activeToken = null; activeDisplay = null; skipped++; reason = "跳过：表面不可用";
        }
        requestReport();
    }
    private static boolean sameBounds(Rect a, Rect b) {
        return a != null && b != null && a.left == b.left && a.top == b.top && a.right == b.right && a.bottom == b.bottom;
    }
    private static Object surface(Object container) { return Reflect.callMethod(container, "getSurfaceControl"); }
    private static int number(Object object, String method) { return (Integer) Reflect.callMethod(object, method); }
    private static boolean yes(Object object, String method) { return (Boolean) Reflect.callMethod(object, method); }
    private static void guarded(Runnable action) {
        Object lock = globalLock;
        if (lock == null) return;
        synchronized (lock) {
            try { action.run(); }
            catch (Throwable failure) {
                faulted = true; enabled = false; error = "已停止接管：" + failure;
                try { controller.cancel(); } catch (Throwable restore) { error += "; 恢复失败：" + restore; }
                log(error); requestReport();
            }
        }
    }
    private static void requestReport() {
        if (worker == null) return;
        Bundle data = new Bundle();
        data.putString("process", "system_server");
        data.putString("hooks", ready ? "Transition/start+finish+cleanup; terminal; ColorOS fade; DisplayPolicy" : "未完成安装");
        data.putString("hits", "跟随 " + followed + " 次；跳过 " + skipped + " 次");
        data.putString("error", error);
        data.putString("effective", "impl=" + IMPLEMENTATION + "; stage=" + stage + "; enabled=" + enabled + "; busy=" + controller.busy() + "; launcher=" + (launcherClient != null) + "; acquired=" + acquired + "; committed=" + committed + "; " + reason);
        data.putLong("revision", revision); data.putInt("pid", Process.myPid());
        data.putInt("hook_version", BuildConfig.VERSION_CODE);
        pendingReport = data;
        // Throttle rather than debounce: frequent transitions must not keep
        // moving the delivery deadline. The task reads the newest snapshot.
        if (!worker.hasCallbacks(reportTask)) worker.postDelayed(reportTask, 300);
    }
    private static void report() {
        Bundle snapshot = pendingReport;
        if (snapshot == null || context == null) return;
        try {
            if (!reportAcknowledged && reportFailures == 0)
                log("window-report sending " + snapshot.getString("effective", "") + "; " + snapshot.getString("hits", ""));
            Bundle acknowledgement = SystemProviderCall.call(context, "report", snapshot);
            // Require an explicit acknowledgement from the external provider.
            if (acknowledgement == null) throw new IllegalStateException("报告接收端没有确认（null）");
            if (!reportAcknowledged) log("window-report delivered " + IMPLEMENTATION);
            reportAcknowledged = true;
            reportFailures = 0;
        } catch (Throwable failure) {
            reportFailures++;
            if (reportFailures <= 3 || reportFailures == 8)
                log("window-report attempt=" + reportFailures + " 系统过渡报告暂不可用：" + failure + "; " +
                    snapshot.getString("effective", "") + "; " + snapshot.getString("hits", "") + "; error=" + snapshot.getString("error", ""));
            if (reportFailures < 8 && !worker.hasCallbacks(reportTask)) worker.postDelayed(reportTask, 5000);
        }
    }
    private static void publish() {
        try {
            Bundle data = new Bundle(); data.putBinder("endpoint", endpoint);
            Bundle result = SystemProviderCall.call(context, "overlay-register", data);
            if (result == null) throw new IllegalStateException("overlay registry returned null");
        } catch (Throwable failure) { log("overlay-publish " + failure); }
        // The module provider can be killed independently of system_server.
        worker.postDelayed(publishTask, 15000);
    }
    private static void logStage() {
        log("window-bootstrap " + IMPLEMENTATION + " stage=" + stage + " pid=" + Process.myPid() +
            " revision=" + revision + " enabled=" + enabled + " followed=" + followed + " skipped=" + skipped +
            " reason=" + reason + " error=" + error);
    }
    private static void log(String message) {
        try { Api102Runtime.log("Thunder: " + message); } catch (Throwable ignored) { /* Keep system_server alive. */ }
    }
}
