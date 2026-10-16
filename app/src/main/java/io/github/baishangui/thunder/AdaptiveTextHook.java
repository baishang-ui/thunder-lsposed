package io.github.baishangui.thunder;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Scoped draw-time text colors. Never replace the system's stored ColorStateLists or RemoteViews. */
final class AdaptiveTextHook {
    private static final String ROW = "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    private static final Map<TextView, AdaptiveTextColor.Transition> texts =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<TextView, Boolean> knownTexts = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Resources, Map<Integer, String>> resourceNames = new WeakHashMap<>();
    private static final int NOTIFICATION = 1, TILE = 2, PANEL = 4, EXCLUDED = 8;
    // Class identity is stable; parent chains, drawable state and material colors are not.
    private static final ClassValue<Integer> viewTypes = new ClassValue<>() {
        @Override protected Integer computeValue(Class<?> type) {
            int flags = 0;
            String name = type.getName();
            if (name.contains(".keyguard.") || name.contains(".bouncer.") || name.contains(".seedling."))
                flags |= EXCLUDED;
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                name = current.getName();
                if (ROW.equals(name)) flags |= NOTIFICATION;
                if ("com.android.systemui.plugins.qs.QSTileView".equals(name)) flags |= TILE;
                if ("com.android.systemui.shade.NotificationPanelView".equals(name)
                        || "com.android.systemui.qs.QSContainerImpl".equals(name)) flags |= PANEL;
            }
            return flags;
        }
    };
    private static volatile Integer wallpaper;
    private static boolean wallpaperStarted;

    static void install() {
        Api102Runtime.findAndHookMethod(TextView.class, "onDraw", Canvas.class, new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                TextView text = (TextView) p.thisObject;
                // Remember existing labels even before tint is enabled: parent invalidation alone
                // does not rebuild a child's hardware display list when a setting changes.
                if (!knownTexts.containsKey(text) && candidate(text)) knownTexts.put(text, true);
                HookConfig config = ShadeHook.snapshot();
                if (!config.enabled || !(config.shade.tint || config.qsCards.tint || config.notificationCard.tint || config.banner.tint)) return;
                Integer background = background(text, config);
                if (background == null) { texts.remove(text); return; }
                AdaptiveTextColor.Transition transition = texts.computeIfAbsent(text, key -> new AdaptiveTextColor.Transition());
                long now = SystemClock.uptimeMillis();
                int color = transition.color(AdaptiveTextColor.foreground(background), now);
                TextPaint paint = text.getPaint();
                p.setObjectExtra("adaptive-native-text", new int[]{text.getCurrentTextColor(), paint.getColor(), paint.linkColor});
                // TextView.onDraw reads mCurTextColor again; changing only Paint is overwritten there.
                // Direct temporary fields avoid setTextColor() invalidating another frame forever.
                Reflect.setIntField(text, "mCurTextColor", color);
                paint.setColor(color);
                paint.linkColor = color;
                if (transition.running(now)) text.postInvalidateOnAnimation();
                ShadeHook.resourceHit("adaptive-panel-text");
            }

            @Override protected void afterHookedMethod(Param p) {
                Object saved = p.getObjectExtra("adaptive-native-text");
                if (!(saved instanceof int[] colors)) return;
                TextView text = (TextView) p.thisObject;
                Reflect.setIntField(text, "mCurTextColor", colors[0]);
                text.getPaint().setColor(colors[1]);
                text.getPaint().linkColor = colors[2];
            }
        });
    }

    static void startWallpaper(Context context, Handler worker, Handler main) {
        if (wallpaperStarted) return;
        wallpaperStarted = true;
        worker.post(() -> {
            try {
                WallpaperManager manager = WallpaperManager.getInstance(context);
                manager.addOnColorsChangedListener((colors, which) -> {
                    if ((which & WallpaperManager.FLAG_SYSTEM) != 0) updateWallpaper(colors, main);
                }, worker);
                updateWallpaper(manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM), main);
            } catch (Throwable error) {
                ShadeHook.frameworkFailure("adaptive-text-wallpaper", error);
            }
        });
    }

    private static void updateWallpaper(WallpaperColors colors, Handler main) {
        wallpaper = colors == null ? null : colors.getPrimaryColor().toArgb();
        main.post(AdaptiveTextHook::refresh);
    }

    /** Wallpaper palette is an estimate: no screenshots, Binder calls or bitmap reads during drawing. */
    static Integer background(TextView text, HookConfig config) {
        ArrayList<Integer> localLayers = new ArrayList<>();
        View surface = null;
        HookConfig.Area material = null;
        boolean panel = false;
        for (View view = text; view != null; view = parent(view)) {
            if ((viewTypes.get(view.getClass()) & NOTIFICATION) != 0) {
                Object nativeBackground = Reflect.getObjectField(view, "mBackgroundNormal");
                if (!(nativeBackground instanceof View)) return null;
                material = NotificationCardHook.area((View) nativeBackground);
                surface = view;
                break;
            }
            Drawable drawable = view.getBackground();
            int card = QsCardHook.textSurface(drawable);
            // Some C17 tile views draw bgDrawable themselves instead of using View.background.
            if ((viewTypes.get(view.getClass()) & TILE) != 0) {
                try { card = QsCardHook.textSurface((Drawable) Reflect.getObjectField(view, "bgDrawable")); }
                catch (RuntimeException ignored) { return null; }
                if (card == 0) return null;
            }
            if (card != 0) {
                if (card == 2) return null; // Native active/unavailable/inoperable material.
                material = config.qsCards;
                surface = view;
                break;
            }
            if (isPanel(view)) { panel = true; break; }
            if (excluded(view)) return null;
            if (drawable instanceof ColorDrawable fill) localLayers.add(fill.getColor());
            else if (drawable instanceof GradientDrawable fill) {
                // A gradient/image cannot be represented by one reliable local background color.
                if (fill.getColors() != null) return null;
                if (fill.getColor() != null) localLayers.add(fill.getColor().getDefaultColor());
            }
        }
        if (surface == null && !panel) return null;
        // A native card is its own surface, even if the surrounding panel has a custom tint.
        if (material != null && !material.tint) return null;
        if (material == null && !config.shade.tint) return null;
        boolean dark = (text.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
        int base = wallpaper != null ? wallpaper : dark ? 0xff202020 : 0xffeeeeee;
        // Heads-up can float over an app while the shade is closed. Its estimate
        // must not inherit an unrelated custom panel tint. The actual app pixels
        // are unavailable here, so the wallpaper remains an approximation.
        if (material != config.banner)
            base = AdaptiveTextColor.composite(config.shade.tint ? config.shade.color : dark ? 0x92000000 : 0x33ffffff, base);
        if (material != null) base = AdaptiveTextColor.composite(material.color, base);
        for (int index = localLayers.size() - 1; index >= 0; index--)
            base = AdaptiveTextColor.composite(localLayers.get(index), base);
        return base;
    }

    private static View parent(View view) {
        ViewParent parent = view.getParent();
        return parent instanceof View ? (View) parent : null;
    }

    private static boolean candidate(TextView text) {
        for (View view = text; view != null; view = parent(view)) {
            if (excluded(view)) return false;
            if ((viewTypes.get(view.getClass()) & NOTIFICATION) != 0 || isPanel(view)
                    || (viewTypes.get(view.getClass()) & TILE) != 0
                    || QsCardHook.textSurface(view.getBackground()) != 0) return true;
        }
        return false;
    }

    private static boolean isPanel(View view) {
        if ((viewTypes.get(view.getClass()) & PANEL) != 0) return true;
        String name = idName(view);
        return "notification_panel".equals(name) || "qs_frame".equals(name);
    }

    private static boolean excluded(View view) {
        return (viewTypes.get(view.getClass()) & EXCLUDED) != 0;
    }

    private static String idName(View view) {
        int id = view.getId();
        if (id == View.NO_ID || id == 0) return "";
        Resources resources = view.getResources();
        synchronized (resourceNames) {
            Map<Integer, String> names = resourceNames.computeIfAbsent(resources, key -> new HashMap<>());
            String name = names.get(id);
            if (name != null) return name;
            try {
                name = resources.getResourceEntryName(id);
                names.put(id, name);
                return name;
            } catch (Resources.NotFoundException ignored) { return ""; }
        }
    }

    static void refresh() {
        ArrayList<TextView> current;
        synchronized (knownTexts) { current = new ArrayList<>(knownTexts.keySet()); }
        for (TextView text : current) text.invalidate();
        HookConfig config = ShadeHook.snapshot();
        if (!config.enabled || !(config.shade.tint || config.qsCards.tint || config.notificationCard.tint || config.banner.tint)) texts.clear();
    }
}
