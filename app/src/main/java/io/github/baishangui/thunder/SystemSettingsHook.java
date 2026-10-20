package io.github.baishangui.thunder;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.widget.Toast;
import android.util.Log;
import android.app.Activity;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Hosts real vendor preference screens inside Settings. No module activity or view imitation. */
final class SystemSettingsHook {
    static final String PACKAGE = "com.android.settings";
    static final String FRAGMENT = "com.coui.appcompat.preference.COUIPreferenceFragment";
    static final String MARKER = "thunder:settings_page";
    private static final int IMPORT_JSON = 0x7468;
    private static final String ENTRY = "thunder:settings_entry";
    private static final String STATE = "thunder:settings_state";
    private static final Uri PROVIDER = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".settings");
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> new Thread(r, "Thunder-settings-bridge"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Bundle lastSnapshot; // Main-thread only; data, never a host context.

    static void install(ClassLoader loader) {
        try {
            // Check the complete vendor surface before exposing an entry that cannot open.
            for (String name : new String[]{FRAGMENT, "com.coui.appcompat.preference.COUIPreference",
                    "com.coui.appcompat.preference.COUISwitchPreference",
                    "com.coui.appcompat.preference.COUIEditTextPreference",
                    "com.coui.appcompat.dialog.COUIAlertDialogBuilder",
                    "com.oplus.settings.widget.preference.RestrictedMenuPreference",
                    "com.oplus.settings.widget.preference.OplusSeekBarPreference",
                    "com.oplus.settings.widget.preference.SettingsPreferenceCategory",
                    "com.oplus.settings.widget.preference.SettingsSimpleJumpPreference",
                    "com.oplus.settings.widget.preference.SettingSwitchPreference"}) Reflect.findClass(name, loader);
            Api102Runtime.findAndHookMethod(FRAGMENT, loader, "onCreatePreferences", Bundle.class, String.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (!isModulePage(p.thisObject)) return;
                    State state = new State(p.thisObject, loader);
                    // The fragment's native dialog restoration immediately calls findPreference.
                    // Create stable keys during this callback, before child fragments are restored.
                    Reflect.setAdditionalInstanceField(p.thisObject, STATE, new WeakReference<>(state));
                    state.load();
                    Reflect.callMethod(p.thisObject, "setHasOptionsMenu", true);
                }
            });
            Api102Runtime.findAndHookMethod(FRAGMENT, loader, "onViewCreated", View.class, Bundle.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (!isModulePage(p.thisObject)) return;
                    configureList(p.thisObject);
                    insetContent(p.thisObject);
                    State state = sliderState(p.thisObject);
                    if (state != null) state.restoreColor((Bundle)p.args[1]);
                }
            });
            Api102Runtime.findAndHookMethod("com.android.settings.homepage.TopLevelSettings", loader,
                "onCreatePreferences", Bundle.class, String.class, new HookCallback() {
                    @Override protected void afterHookedMethod(Param p) { addEntry(p.thisObject, loader); }
                });
            Api102Runtime.findAndHookMethod("com.android.settings.homepage.TopLevelSettings", loader, "onStart", new HookCallback() {
                @Override protected void afterHookedMethod(Param p) { addEntry(p.thisObject, loader); }
            });
            Api102Runtime.findAndHookMethod("androidx.fragment.app.Fragment", loader, "onCreateOptionsMenu", Menu.class, MenuInflater.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (!isModulePage(p.thisObject)) return;
                    Object ref = Reflect.getAdditionalInstanceField(p.thisObject, STATE);
                    Object state = ref instanceof WeakReference<?> ? ((WeakReference<?>)ref).get() : null;
                    if (state instanceof State) ((State)state).addRestart((Menu)p.args[0]);
                }
            });
            Api102Runtime.findAndHookMethod("androidx.fragment.app.Fragment", loader, "onResume", new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (!p.thisObject.getClass().getName().equals(FRAGMENT) || !isModulePage(p.thisObject)) return;
                    Object ref = Reflect.getAdditionalInstanceField(p.thisObject, STATE);
                    Object state = ref instanceof WeakReference<?> ? ((WeakReference<?>)ref).get() : null;
                    if (state instanceof State) ((State)state).refresh();
                }
            });
            Api102Runtime.findAndHookMethod("androidx.fragment.app.Fragment", loader, "onActivityResult", int.class, int.class, Intent.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if ((int)p.args[0] != IMPORT_JSON || (int)p.args[1] != Activity.RESULT_OK || !isModulePage(p.thisObject)) return;
                    State state = sliderState(p.thisObject);
                    Intent result = (Intent)p.args[2];
                    if (state != null && result != null && result.getData() != null) state.importDocument(result.getData());
                }
            });
            Api102Runtime.findAndHookMethod("androidx.fragment.app.Fragment", loader, "onSaveInstanceState", Bundle.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (!isModulePage(p.thisObject)) return;
                    State state = sliderState(p.thisObject);
                    if (state != null) state.saveColor((Bundle)p.args[0]);
                }
            });
            Api102Runtime.findAndHookMethod("androidx.fragment.app.Fragment", loader, "onDestroyView", new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if (!isModulePage(p.thisObject)) return;
                    State state = sliderState(p.thisObject);
                    if (state != null && state.colorEditor != null) state.colorEditor.dialog.dismiss();
                }
            });
            // Keep the native input dialog, IME animation and Fragment restoration.
            Class<?> builder = Reflect.findClass("androidx.appcompat.app.AlertDialog$Builder", loader);
            Api102Runtime.findAndHookMethod("androidx.preference.PreferenceDialogFragmentCompat", loader,
                "onPrepareDialogBuilder", builder, new HookCallback() {
                    @Override protected void afterHookedMethod(Param p) {
                        Object pref = Reflect.callMethod(p.thisObject, "getPreference");
                        State state = sliderState(pref);
                        if (state == null || !state.controls.containsKey(pref)) return;
                        Reflect.callMethod(p.args[0], "setPositiveButton", "保存",
                            (android.content.DialogInterface.OnClickListener)p.thisObject, true);
                    }
                });
            Api102Runtime.findAndHookMethod("com.coui.appcompat.preference.COUIEditTextPreferenceDialogFragment", loader,
                "onStart", new HookCallback() {
                    @Override protected void afterHookedMethod(Param p) {
                        Object pref = Reflect.callMethod(p.thisObject, "getPreference");
                        State state = sliderState(pref);
                        if (state != null) state.validateNumberDialog(p.thisObject, pref);
                    }
                });
            Class<?> seekBar = Reflect.findClass("com.coui.appcompat.seekbar.COUISeekBar", loader);
            String seekPreference = "com.oplus.settings.widget.preference.OplusSeekBarPreference";
            Api102Runtime.findAndHookMethod(seekPreference, loader, "onStartTrackingTouch", seekBar, new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    State state = sliderState(p.thisObject);
                    if (state != null) state.dragging.add(p.thisObject);
                }
            });
            Api102Runtime.findAndHookMethod(seekPreference, loader, "onProgressChanged", seekBar, int.class, boolean.class, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    State state = sliderState(p.thisObject);
                    if (state != null && Boolean.TRUE.equals(p.args[2])) state.previewSlider(p.thisObject, (int)p.args[1]);
                }
            });
            Api102Runtime.findAndHookMethod(seekPreference, loader, "onStopTrackingTouch", seekBar, new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    State state = sliderState(p.thisObject);
                    if (state != null) {
                        state.dragging.remove(p.thisObject); state.previews.remove(p.thisObject);
                        state.apply();
                    }
                }
            });
            Log.i("ThunderSettings", "System Settings native COUI integration installed");
        } catch (Throwable error) {
            Log.e("ThunderSettings", "Settings integration unavailable on this firmware", error);
            Api102Runtime.log("System Settings integration unavailable: " + error);
        }
    }

    private static State sliderState(Object pref) {
        Object ref = Reflect.getAdditionalInstanceField(pref, STATE);
        Object state = ref instanceof WeakReference<?> ? ((WeakReference<?>)ref).get() : null;
        return state instanceof State ? (State)state : null;
    }
    private static boolean isModulePage(Object fragment) {
        Bundle args = (Bundle)Reflect.callMethod(fragment, "getArguments");
        return args != null && args.containsKey(MARKER);
    }
    private static Context context(Object fragment) { return (Context)Reflect.callMethod(fragment, "requireContext"); }
    private static Object preference(ClassLoader loader, Context context, String type, String key, String title) {
        Object pref = Reflect.newInstance(Reflect.findClass(type.contains(".") ? type : "com.coui.appcompat.preference." + type, loader), context);
        Reflect.callMethod(pref, "setKey", key);
        Reflect.callMethod(pref, "setTitle", title);
        // Never persist module keys into Settings' private preference file.
        Reflect.callMethod(pref, "setPersistent", false);
        return pref;
    }
    private static int resource(Context context, String name, String type) {
        return context.getResources().getIdentifier(name, type, PACKAGE);
    }
    private static Object category(ClassLoader loader, Context context, Object screen, String key, String title) {
        Object group = preference(loader, context, "com.oplus.settings.widget.preference.SettingsPreferenceCategory", key, title);
        Reflect.callMethod(screen, "addPreference", group);
        return group;
    }
    private static Object jump(ClassLoader loader, Context context, String key, String title, boolean homepage) {
        Object pref = preference(loader, context, "com.oplus.settings.widget.preference.SettingsSimpleJumpPreference", key, title);
        int layout = resource(context, homepage ? "jump_preference_simple_layout_no_summary_homepage" :
            "jump_preference_simple_layout_no_icon_and_summary", "layout");
        if (layout != 0) Reflect.callMethod(pref, "setLayoutResource", layout);
        return pref;
    }
    // SubSettings overlays its toolbar for a plain COUI fragment. Use the real toolbar's
    // measured bounds for content spacing; native components still own all drawing.
    private static void insetContent(Object fragment) {
        View list = (View)Reflect.callMethod(fragment, "getListView");
        Activity activity = (Activity)Reflect.callMethod(fragment, "getActivity");
        if (list == null || activity == null) return;
        View toolbar = activity.findViewById(resource(activity, "toolbar", "id"));
        if (toolbar == null) return;
        int bottom = list.getPaddingBottom();
        boolean[] positioned = {false};
        list.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            int[] barPosition = new int[2], listPosition = new int[2];
            toolbar.getLocationInWindow(barPosition); list.getLocationInWindow(listPosition);
            int top = Math.max(0, barPosition[1] + toolbar.getHeight() - listPosition[1]);
            if (view.getPaddingTop() != top)
                view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), bottom);
            if (!positioned[0] && toolbar.getHeight() > 0) {
                positioned[0] = true;
                view.post(() -> Reflect.callMethod(list, "scrollToPosition", 0));
            }
        });
        list.requestLayout();
    }
    private static void configureList(Object fragment) {
        // Match SettingsBaseFragment: AndroidX's full-width divider must not draw
        // over COUI's inset card dividers. COUI keeps ownership of their rendering.
        Reflect.callMethod(fragment, "setDivider", (Object)null);
        Reflect.callMethod(fragment, "setDividerHeight", 0);
        Object list = Reflect.callMethod(fragment, "getListView");
        if (list == null) return;
        // Remove any ItemDecorations added by COUI's own fragment init that still draw
        // full-width separator lines and overflow the card's rounded-corner boundaries.
        int decorCount = (int)Reflect.callMethod(list, "getItemDecorationCount");
        while (decorCount-- > 0) Reflect.callMethod(list, "removeItemDecorationAt", 0);
        // Initial loading and conditional rows should not animate vertically.
        Reflect.callMethod(list, "setItemAnimator", (Object)null);
        ((android.view.ViewGroup)list).setLayoutAnimation(null);
    }
    private interface Listener { Object invoke(Object[] args); }
    private static Object listener(ClassLoader loader, String type, Listener callback) {
        Class<?> contract = Reflect.findClass("androidx.preference." + type, loader);
        return Proxy.newProxyInstance(loader, new Class<?>[]{contract}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "ThunderSettingsListener";
                default: return callback.invoke(args);
            }
        });
    }
    private static void click(Object pref, ClassLoader loader, Runnable action) {
        Reflect.callMethod(pref, "setOnPreferenceClickListener", listener(loader, "Preference$OnPreferenceClickListener", args -> {
            try { action.run(); } catch (Throwable error) { toast((Context)Reflect.callMethod(pref, "getContext"), error); }
            return true;
        }));
    }
    private static void addEntry(Object fragment, ClassLoader loader) {
        try {
            Object screen = Reflect.callMethod(fragment, "getPreferenceScreen");
            if (screen == null || Reflect.callMethod(fragment, "findPreference", ENTRY) != null) return;
            Context context = context(fragment);
            // Place the entry directly on the screen without a wrapper category: an empty-title
            // category produces no visible header and COUIPreferenceItemDecoration does not insert
            // spacing between it and the next group, so the entry would appear flush against the
            // native account card. A bare preference with order -1 sorts first and lets the
            // decoration handle inter-item spacing naturally.
            Object pref = jump(loader, context, ENTRY, "thunder", true);
            Reflect.callMethod(pref, "setOrder", -1);
            int icon = resource(context, "settings_personalise_ic", "drawable");
            if (icon != 0) Reflect.callMethod(pref, "setIcon", icon);
            click(pref, loader, () -> open(context, "home", "thunder"));
            Reflect.callMethod(screen, "addPreference", pref);
        } catch (Throwable error) { Log.e("ThunderSettings", "Cannot add Settings entry", error); }
    }
    private static void open(Context context, String page, String title) {
        Bundle args = new Bundle(); args.putString(MARKER, page);
        Intent intent = new Intent().setClassName(PACKAGE, "com.android.settings.SubSettings")
            .putExtra(":settings:show_fragment", FRAGMENT).putExtra(":settings:show_fragment_args", args)
            .putExtra(":settings:show_fragment_title", title).putExtra(":settings:source_metrics", 0);
        int enter = resource(context, "coui_open_slide_enter", "anim");
        int exit = resource(context, "coui_open_slide_exit", "anim");
        context.startActivity(intent, android.app.ActivityOptions.makeCustomAnimation(context, enter, exit).toBundle());
    }
    private static Bundle call(Context context, String method, Bundle data) {
        Bundle result = context.getContentResolver().call(PROVIDER, method, null, data);
        if (result == null) throw new IllegalStateException("模块配置服务未返回结果");
        return result;
    }
    private static void toast(Context context, Throwable error) {
        Log.e("ThunderSettings", "Settings operation failed", error);
        Toast.makeText(context, "操作失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
    }

    private static final class State {
        final WeakReference<Object> fragment;
        final ClassLoader loader;
        final Map<Object, Bundle> controls = new LinkedHashMap<>();
        final java.util.Set<Object> dragging = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        final Map<Object, Integer> previews = new java.util.IdentityHashMap<>();
        final Map<String, Integer> pendingNumbers = new LinkedHashMap<>();
        final Map<String, Long> numberEdits = new LinkedHashMap<>();
        long editSequence;
        Bundle snapshot;
        Object status;
        Object statusGroup;
        MenuItem restartButton;
        final ArrayList<Object> actions = new ArrayList<>();
        final ArrayList<Object> sections = new ArrayList<>();
        SystemSettingsPalette.Editor colorEditor;
        String colorKey;
        Bundle restoredColor;
        String page;
        String failure;
        boolean refreshing;
        State(Object fragment, ClassLoader loader) { this.fragment = new WeakReference<>(fragment); this.loader = loader; }
        boolean loaded;
        void load() {
            Object host = fragment.get(); if (host == null) return;
            // Stable keys exist before native child dialogs restore, without Binder/disk IO
            // on Settings' UI thread. Controls stay disabled until the first real snapshot.
            loaded = lastSnapshot != null;
            snapshot = loaded ? lastSnapshot : emptySnapshot();
            build(host);
            refresh();
        }
        private Bundle emptySnapshot() {
            ArrayList<Bundle> fields = SystemSettingsCatalog.INSTANCE.schema();
            Bundle data = new Bundle(), values = new Bundle(), pages = new Bundle();
            for (Bundle field : fields) {
                String key = field.getString("key");
                if (field.containsKey("min") || field.getBoolean("color") || field.getInt("bit") != 0)
                    values.putInt(key, field.getInt("min"));
                else values.putBoolean(key, false);
            }
            for (Map.Entry<String, String> page : SystemSettingsCatalog.INSTANCE.getPages().entrySet()) pages.putString(page.getKey(), page.getValue());
            data.putParcelableArrayList("fields", fields); data.putBundle("values", values); data.putBundle("pages", pages);
            data.putString("status", "正在读取模块配置，点按可重试"); data.putString("version", "");
            return data;
        }
        void refresh() { request("ui_snapshot", null, null); }
        void request(String method, Bundle data, Runnable completed) {
            Object host = fragment.get(); if (host == null) return;
            if ("ui_snapshot".equals(method) && refreshing) return;
            if ("ui_snapshot".equals(method)) refreshing = true;
            Context app = context(host).getApplicationContext();
            WORKER.execute(() -> {
                try {
                    Bundle result = call(app, method, data);
                    deliver(() -> {
                        if ("ui_snapshot".equals(method)) refreshing = false;
                        Object current = fragment.get();
                        if (current == null || !(Boolean)Reflect.callMethod(current, "isAdded")) return;
                        snapshot = result; lastSnapshot = result; loaded = true; failure = null;
                        if (controls.isEmpty() && status == null) build(current); else apply();
                        if (completed != null) completed.run();
                    });
                } catch (Throwable error) {
                    deliver(() -> {
                        if ("ui_snapshot".equals(method)) refreshing = false;
                        Object current = fragment.get();
                        if (current != null && (Boolean)Reflect.callMethod(current, "isAdded")) {
                            failure = "操作失败，点按重试读取：" + error.getMessage();
                            pendingNumbers.clear(); numberEdits.clear();
                            toast(context(current), error); apply();
                        }
                    });
                }
            });
        }
        private void deliver(Runnable action) {
            MAIN.post(() -> {
                try { action.run(); }
                catch (Throwable error) {
                    Log.e("ThunderSettings", "Cannot update native Settings page", error);
                    Object host = fragment.get();
                    if (host != null) try {
                        if ((Boolean)Reflect.callMethod(host, "isAdded")) toast(context(host), error);
                    } catch (Throwable ignored) { /* A detached host must never crash Settings. */ }
                }
            });
        }
        void build(Object host) {
            controls.clear(); actions.clear(); sections.clear(); status = null;
            Context context = context(host);
            page = ((Bundle)Reflect.callMethod(host, "getArguments")).getString(MARKER, "home");
            Object screen = Reflect.callMethod(Reflect.callMethod(host, "getPreferenceManager"), "createPreferenceScreen", context);
            Bundle pages = snapshot.getBundle("pages");
            // Show the module status row only on the home page. Deep category pages start
            // directly with their settings, not a repeated status header.
            if ("home".equals(page) || "tools".equals(page)) {
                statusGroup = category(loader, context, screen, "thunder:status_group", "模块信息");
                status = preference(loader, context, "COUIPreference", "thunder:status", "模块状态");
                click(status, loader, this::refresh);
                Reflect.callMethod(statusGroup, "addPreference", status);
            }
            Map<String, Object> groups = new LinkedHashMap<>();
            ArrayList<Bundle> fields = snapshot.getParcelableArrayList("fields", Bundle.class);
            if (fields != null) for (Bundle field : fields) {
                if (!page.equals(field.getString("page"))) continue;
                String key = field.getString("key");
                Object value = snapshot.getBundle("values").get(key);
                String type = field.containsKey("choices") ? "com.oplus.settings.widget.preference.RestrictedMenuPreference" :
                    (value instanceof Boolean || field.getInt("bit") != 0) ? "com.oplus.settings.widget.preference.SettingSwitchPreference" :
                    field.containsKey("min") ? "COUIEditTextPreference" : "COUIPreference";
                // A separate preference key per bit also makes native dialog restoration deterministic.
                Object pref = preference(loader, context, type, "thunder:" + key + ":" + field.getInt("bit"), field.getString("title"));
                String section = field.getString("section", "");
                Object group = groups.get(section);
                if (group == null) {
                    group = category(loader, context, screen, "thunder:section:" + groups.size(), section);
                    groups.put(section, group); sections.add(group);
                }
                Reflect.callMethod(pref, "setSummary", field.getString("description"));
                if (type.equals("COUIPreference")) {
                    if (field.getBoolean("color")) {
                        int arrow = resource(context, "coui_arrow_next", "drawable");
                        if (arrow != 0) Reflect.callMethod(pref, "setJump", arrow);
                        click(pref, loader, () -> openColor(field, snapshot.getBundle("values").getInt(key)));
                    } else Reflect.callMethod(pref, "setSelectable", false);
                }
                if (type.equals("COUIEditTextPreference")) {
                    Reflect.setAdditionalInstanceField(pref, STATE, new WeakReference<>(this));
                    Reflect.callMethod(pref, "setDialogTitle", field.getString("title"));
                    Reflect.callMethod(pref, "setDialogMessage", "范围：" + field.getInt("min") + "–" + field.getInt("max") + " " + field.getString("unit", ""));
                    Reflect.callMethod(pref, "setPositiveButtonText", "保存");
                    Reflect.callMethod(pref, "setNegativeButtonText", "取消");
                    Reflect.callMethod(pref, "setBlurBackground", true);
                    Reflect.callMethod(pref, "setOnBindEditTextListener", listener(loader, "EditTextPreference$OnBindEditTextListener", args -> {
                        android.widget.EditText input = (android.widget.EditText)args[0];
                        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                            (field.getInt("min") < 0 ? android.text.InputType.TYPE_NUMBER_FLAG_SIGNED : 0));
                        input.setSingleLine(true); input.selectAll();
                        return null;
                    }));
                }
                if (field.containsKey("choices")) {
                    String[] choices = field.getStringArray("choices");
                    String[] values = new String[choices.length];
                    int[] choiceValues = field.getIntArray("choice_values");
                    for (int i = 0; i < values.length; i++) values[i] = String.valueOf(choiceValues == null ? i : choiceValues[i]);
                    Reflect.callMethod(pref, "setEntries", (Object)choices);
                    Reflect.callMethod(pref, "setEntryValues", (Object)values);
                }
                Reflect.callMethod(pref, "setOnPreferenceChangeListener", listener(loader, "Preference$OnPreferenceChangeListener", args -> {
                    try {
                        if (!loaded) throw new IllegalStateException("配置尚未加载，请稍后重试");
                        Object changed = args[1];
                        if (!(changed instanceof Boolean)) {
                            changed = SystemSettingsInputs.parseNumber(changed.toString(), field.getInt("min"), field.getInt("max"));
                        }
                        Bundle patch = new Bundle(); patch.putString("key", key); patch.putInt("bit", field.getInt("bit"));
                        if (changed instanceof Boolean) patch.putBoolean("value", (boolean)changed); else patch.putInt("value", (int)changed);
                        Reflect.callMethod(pref, "setEnabled", false);
                        request("ui_update", patch, null);
                    } catch (Throwable error) { toast(context, error); }
                    return false; // Only display values acknowledged by the module's writer.
                }));
                controls.put(pref, field);
                Reflect.callMethod(group, "addPreference", pref);
                if (field.containsKey("min") && !field.containsKey("choices")) {
                    addSlider(context, group, field, "thunder:" + key + ":slider");
                }
            }
            if ("home".equals(page)) {
                Object controlNotify = category(loader, context, screen, "thunder:appearance", "控制与通知");
                Object statusNav = category(loader, context, screen, "thunder:status_nav", "状态栏与导航");
                Object cloudCat = category(loader, context, screen, "thunder:cloud_cat", "流体云");
                Object sound = category(loader, context, screen, "thunder:sound", "声音");
                Object maintenance = category(loader, context, screen, "thunder:maintenance", "维护");
                for (String key : new String[]{"panel", "cards", "notifications", "banner", "volume", "cloud", "status", "gesture"}) {
                    Object pref = jump(loader, context, "thunder:page:" + key, pages.getString(key), false);
                    click(pref, loader, () -> open(context, key, pages.getString(key)));
                    Object group;
                    if ("status".equals(key) || "gesture".equals(key)) group = statusNav;
                    else if ("cloud".equals(key)) group = cloudCat;
                    else if ("volume".equals(key)) group = sound;
                    else group = controlNotify;
                    Reflect.callMethod(group, "addPreference", pref);
                }
                Object tools = jump(loader, context, "thunder:tools", "配置与维护", false);
                click(tools, loader, () -> open(context, "tools", "配置与维护"));
                Reflect.callMethod(maintenance, "addPreference", tools);
            } else if ("tools".equals(page)) buildTools(context, screen);
            Reflect.callMethod(host, "setPreferenceScreen", screen);
            apply();
        }
        void openColor(Bundle field, int initial) {
            Object host = fragment.get();
            if (host == null || !loaded || colorEditor != null) return;
            colorKey = field.getString("key");
            String key = colorKey;
            colorEditor = SystemSettingsPalette.show(context(host), loader, field.getString("title"), initial, color -> {
                Bundle patch = new Bundle(); patch.putString("key", key); patch.putInt("value", color);
                request("ui_update", patch, null);
            }, () -> { colorEditor = null; colorKey = null; });
        }
        void saveColor(Bundle saved) {
            if (colorEditor == null) return;
            saved.putString("thunder:color_key", colorKey);
            saved.putInt("thunder:color_draft", colorEditor.color());
        }
        void restoreColor(Bundle saved) {
            if (saved != null && saved.containsKey("thunder:color_key")) {
                restoredColor = new Bundle();
                restoredColor.putString("key", saved.getString("thunder:color_key"));
                restoredColor.putInt("draft", saved.getInt("thunder:color_draft"));
            }
            if (!loaded || restoredColor == null) return;
            Bundle restore = restoredColor; restoredColor = null;
            for (Bundle field : controls.values()) {
                if (!field.getBoolean("color") || !restore.getString("key", "").equals(field.getString("key"))) continue;
                Bundle values = SystemSettingsCatalog.INSTANCE.presentationValues(snapshot.getBundle("values"));
                if (SystemSettingsCatalog.INSTANCE.visible(field, values) && values.getBoolean("enabled")) openColor(field, restore.getInt("draft"));
                break;
            }
        }
        void validateNumberDialog(Object dialogFragment, Object pref) {
            Bundle field = controls.get(pref);
            if (field == null || !field.containsKey("min")) return;
            android.widget.EditText input = (android.widget.EditText)Reflect.getObjectField(dialogFragment, "mEditText");
            Object dialog = Reflect.callMethod(dialogFragment, "getDialog");
            android.widget.TextView save = (android.widget.TextView)Reflect.callMethod(dialog, "getButton", android.content.DialogInterface.BUTTON_POSITIVE);
            Runnable validate = () -> {
                boolean valid;
                try { SystemSettingsInputs.parseNumber(input.getText().toString(), field.getInt("min"), field.getInt("max")); valid = true; }
                catch (IllegalArgumentException error) { valid = false; }
                save.setEnabled(valid);
                Reflect.callMethod(input, "setErrorState", !valid);
            };
            // onStart can run again on the same dialog; do not accumulate watchers.
            Object ref = Reflect.getAdditionalInstanceField(input, STATE);
            Object old = ref instanceof WeakReference<?> ? ((WeakReference<?>)ref).get() : null;
            if (old instanceof android.text.TextWatcher) input.removeTextChangedListener((android.text.TextWatcher)old);
            android.text.TextWatcher watcher = new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                public void onTextChanged(CharSequence s, int start, int before, int count) { }
                public void afterTextChanged(android.text.Editable text) { validate.run(); }
            };
            Reflect.setAdditionalInstanceField(input, STATE, new WeakReference<>(watcher));
            input.addTextChangedListener(watcher); validate.run();
        }
        void addSlider(Context context, Object group, Bundle field, String preferenceKey) {
            String key = field.getString("key");
            Object slider = preference(loader, context, "com.oplus.settings.widget.preference.OplusSeekBarPreference", preferenceKey, field.getString("title"));
            Reflect.callMethod(slider, "setMax", field.getInt("max") - field.getInt("min"));
            Reflect.callMethod(slider, "setContinuousUpdates", false);
            Reflect.callMethod(slider, "setSeekBarContentDescription", field.getString("title"));
            Bundle sliderField = new Bundle(field); sliderField.putBoolean("slider", true);
            controls.put(slider, sliderField);
            Reflect.setAdditionalInstanceField(slider, STATE, new WeakReference<>(this));
            Reflect.callMethod(slider, "setOnPreferenceChangeListener", listener(loader, "Preference$OnPreferenceChangeListener", args -> {
                try {
                    if (!loaded) return false;
                    int number = ((Number)args[1]).intValue() + field.getInt("min");
                    if (number < field.getInt("min") || number > field.getInt("max")) return false;
                    int value = sliderValue(field, number);
                    Bundle patch = new Bundle(); patch.putString("key", key); patch.putInt("value", value);
                    long edit = ++editSequence;
                    pendingNumbers.put(key, value); numberEdits.put(key, edit);
                    request("ui_update", patch, () -> {
                        if (Long.valueOf(edit).equals(numberEdits.get(key))) {
                            pendingNumbers.remove(key); numberEdits.remove(key); apply();
                        }
                    });
                    return true;
                } catch (Throwable error) { toast(context, error); }
                return false;
            }));
            Reflect.callMethod(group, "addPreference", slider);
        }
        int sliderValue(Bundle field, int number) {
            if (!field.containsKey("channel")) return number;
            String key = field.getString("key");
            int color = pendingNumbers.containsKey(key) ? pendingNumbers.get(key) : snapshot.getBundle("values").getInt(key);
            return SystemSettingsInputs.withChannel(color, field.getInt("channel"), number);
        }
        void previewSlider(Object slider, int progress) {
            Bundle field = controls.get(slider);
            if (field == null) return;
            int value = sliderValue(field, progress + field.getInt("min"));
            previews.put(slider, value);
            for (Map.Entry<Object, Bundle> entry : controls.entrySet()) {
                Bundle other = entry.getValue();
                if (other.getBoolean("slider") || !field.getString("key").equals(other.getString("key"))) continue;
                if (other.containsKey("channel"))
                    Reflect.callMethod(entry.getKey(), "setAssignment", String.valueOf(SystemSettingsInputs.channel(value, other.getInt("channel"))));
                else if (other.getBoolean("color")) setColorPreview(entry.getKey(), value);
                else Reflect.callMethod(entry.getKey(), "setAssignment", value +
                    (field.getString("unit", "").isEmpty() ? "" : " " + field.getString("unit")));
            }
        }
        void setColorPreview(Object pref, int color) {
            Reflect.callMethod(pref, "setAssignment", String.format(Locale.ROOT, "#%08X", color));
            // A native COUI color assignment carries the exact ARGB value without
            // an extra leading icon that would shift the card's text/divider inset.

        }
        void importDocument(Uri uri) {
            Object host = fragment.get(); if (host == null) return;
            Context app = context(host).getApplicationContext();
            WORKER.execute(() -> {
                try (java.io.InputStream stream = app.getContentResolver().openInputStream(uri)) {
                    String json = SystemSettingsInputs.readJson(stream);
                    deliver(() -> {
                        Object current = fragment.get();
                        if (current == null || !(Boolean)Reflect.callMethod(current, "isAdded")) return;
                        Bundle data = new Bundle(); data.putString("text", json);
                        request("ui_import", data, () -> Toast.makeText(context(current), "JSON 配置已导入", Toast.LENGTH_SHORT).show());
                    });
                } catch (Throwable error) {
                    deliver(() -> { Object current = fragment.get(); if (current != null && (Boolean)Reflect.callMethod(current, "isAdded")) toast(context(current), error); });
                }
            });
        }
        void buildTools(Context context, Object screen) {
            Object backup = category(loader, context, screen, "thunder:backup", "配置管理");
            Object export = jump(loader, context, "thunder:export", "复制配置", false);
            click(export, loader, () -> {
                android.content.ClipboardManager clipboard = context.getSystemService(android.content.ClipboardManager.class);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("thunder.settings", snapshot.getString("export")));
                Toast.makeText(context, "配置已复制", Toast.LENGTH_SHORT).show();
            });
            actions.add(export);
            Reflect.callMethod(export, "setEnabled", loaded);
            Reflect.callMethod(backup, "addPreference", export);
            Object input = jump(loader, context, "thunder:import", "从 JSON 文件导入", false);
            click(input, loader, () -> {
                Object host = fragment.get(); if (host == null) return;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Reflect.callMethod(host, "startActivityForResult", intent, IMPORT_JSON);
            });
            actions.add(input);
            Reflect.callMethod(input, "setEnabled", loaded);
            Reflect.callMethod(backup, "addPreference", input);
            Object reset = jump(loader, context, "thunder:reset", "重置配置", false);
            click(reset, loader, () -> confirm(context, "重置配置", "将所有模块参数恢复默认值。", () -> request("ui_reset", null, null)));
            actions.add(reset);
            Reflect.callMethod(reset, "setEnabled", loaded);
            Reflect.callMethod(backup, "addPreference", reset);
        }
        void addRestart(Menu menu) {
            final int id = 0x74687273;
            restartButton = menu.findItem(id);
            if (restartButton == null) restartButton = menu.add(Menu.NONE, id, Menu.NONE, "重启系统界面");
            Object host = fragment.get();
            if (host != null) {
                Context context = context(host);
                int icon = resource(context, "ic_sync", "drawable");
                if (icon == 0) icon = android.R.drawable.ic_popup_sync;
                restartButton.setIcon(icon);
                restartButton.setContentDescription("重启系统界面");
                restartButton.setTooltipText("重启系统界面");
            }
            restartButton.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            restartButton.setEnabled(loaded);
            restartButton.setOnMenuItemClickListener(item -> {
                request("ui_restart", null, () -> {
                    Object current = fragment.get();
                    if (current != null) Toast.makeText(context(current), "已提交重启系统界面请求", Toast.LENGTH_SHORT).show();
                });
                return true;
            });
        }
        void confirm(Context context, String title, String message, Runnable action) {
            Object builder = Reflect.newInstance(Reflect.findClass("com.coui.appcompat.dialog.COUIAlertDialogBuilder", loader), context);
            Reflect.callMethod(builder, "setBlurBackgroundDrawable", true);
            Reflect.callMethod(builder, "setTitle", title);
            Reflect.callMethod(builder, "setMessage", message);
            Reflect.callMethod(builder, "setNegativeButton", "取消", (android.content.DialogInterface.OnClickListener)(dialog, which) -> { });
            Reflect.callMethod(builder, "setPositiveButton", "重置", (android.content.DialogInterface.OnClickListener)(dialog, which) -> action.run(), true);
            Reflect.callMethod(builder, "show");
        }
        @SuppressWarnings("deprecation") void apply() {
            if (snapshot == null) return;
            Bundle values = SystemSettingsCatalog.INSTANCE.presentationValues(snapshot.getBundle("values"));
            if (status != null) {
                // Home page: show status only while loading or when there's a failure.
                // Tools page: always show (it is the maintenance page).
                Reflect.callMethod(statusGroup, "setVisible", "tools".equals(page) || !loaded || failure != null);
                Reflect.callMethod(status, "setAssignment", snapshot.getString("version"));
                Reflect.callMethod(status, "setTitle", loaded ? "模块状态" : "正在读取模块配置");
                Reflect.callMethod(status, "setSummary", failure != null ? failure : snapshot.getString("status") + "\n" + snapshot.getString("restart", ""));
            }
            if (restartButton != null) restartButton.setEnabled(loaded);
            for (Object action : actions) Reflect.callMethod(action, "setEnabled", loaded);
            for (Map.Entry<Object, Bundle> entry : controls.entrySet()) {
                Object pref = entry.getKey(); Bundle field = entry.getValue();
                String key = field.getString("key");
                Object value = pendingNumbers.containsKey(key) ? pendingNumbers.get(key) : values.get(key);
                for (Map.Entry<Object, Integer> preview : previews.entrySet())
                    if (dragging.contains(preview.getKey()) && key.equals(controls.get(preview.getKey()).getString("key"))) value = preview.getValue();
                int bit = field.getInt("bit");
                if (field.getBoolean("slider")) {
                    if (dragging.contains(pref)) continue; // Never rebind an active native gesture.
                    int scalar = field.containsKey("channel") ? SystemSettingsInputs.channel((int)value, field.getInt("channel")) : (int)value;
                    int progress = scalar - field.getInt("min");
                    if ((int)Reflect.callMethod(pref, "getProgress") != progress)
                        Reflect.callMethod(pref, "setProgress", progress);
                    Reflect.callMethod(pref, "setSeekBarStateDescription", String.valueOf(scalar) + " " + field.getString("unit", ""));
                }
                else if (field.containsKey("channel")) Reflect.callMethod(pref, "setAssignment", String.valueOf(SystemSettingsInputs.channel((int)value, field.getInt("channel"))));
                else if (field.getBoolean("color")) setColorPreview(pref, (int)value);
                else if (bit != 0) Reflect.callMethod(pref, "setChecked", (values.getInt(field.getString("key")) & bit) != 0);
                else if (value instanceof Boolean) Reflect.callMethod(pref, "setChecked", value);
                else if (field.containsKey("choices")) {
                    Reflect.callMethod(pref, "setValue", String.valueOf(value));
                    int index = (int)value;
                    int[] choiceValues = field.getIntArray("choice_values");
                    if (choiceValues != null) for (int i = 0; i < choiceValues.length; i++) if (choiceValues[i] == (int)value) { index = i; break; }
                    Reflect.callMethod(pref, "setAssignment", field.getStringArray("choices")[index]);
                } else {
                    if (field.containsKey("min")) Reflect.callMethod(pref, "setText", String.valueOf(value));
                    Reflect.callMethod(pref, "setAssignment", value +
                        (field.getString("unit", "").isEmpty() ? "" : " " + field.getString("unit")));
                }
                Reflect.callMethod(pref, "setVisible", SystemSettingsCatalog.INSTANCE.visible(field, values));
                Reflect.callMethod(pref, "setEnabled", loaded && ("enabled".equals(key) || values.getBoolean("enabled")));
            }
            for (Object section : sections) {
                boolean visible = false;
                int count = (int)Reflect.callMethod(section, "getPreferenceCount");
                for (int i = 0; i < count && !visible; i++)
                    visible = (boolean)Reflect.callMethod(Reflect.callMethod(section, "getPreference", i), "isVisible");
                Reflect.callMethod(section, "setVisible", visible);
            }
            restoreColor(null);
        }
    }
}
