package io.github.baishangui.thunder;

import android.graphics.Typeface;
import android.text.Layout;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** Only clocks below PhoneStatusBarView; lockscreen and shade clocks retain native behavior. */
final class StatusBarClockHook {
    private static final Map<TextView, State> clocks = new WeakHashMap<>();
    private record Measurement(TextView clock, int widthSpec) { }
    private static final ThreadLocal<Measurement> measurement = new ThreadLocal<>();
    private static final class State {
        float size, scale;
        Typeface face;
        boolean nativeSeconds, custom, bold;
        SimpleDateFormat formatter;
        String pattern;
        Locale locale;
        State(TextView clock) {
            capture(clock);
            nativeSeconds = Reflect.getBooleanField(clock, "mShowSeconds");
        }
        void capture(TextView clock) { size = clock.getTextSize(); scale = clock.getTextScaleX(); face = clock.getTypeface(); }
    }

    static void install(ClassLoader loader) {
        Class<?> type = Reflect.findClass("com.android.systemui.statusbar.policy.Clock", loader);
        Api102Runtime.findAndHookMethod(type, "getSmallTime", new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                TextView clock = (TextView) p.thisObject;
                HookConfig c = ShadeHook.snapshot();
                if (p.hasThrowable() || !custom(c) || !isStatusClock(clock)) return;
                State state = state(clock);
                Calendar calendar = (Calendar) Reflect.getObjectField(clock, "mCalendar");
                if (calendar == null) return;
                Locale locale = clock.getResources().getConfiguration().getLocales().get(0);
                boolean hour24 = c.statusBarClockHourFormat == 1 || (c.statusBarClockHourFormat == 0 && DateFormat.is24HourFormat(clock.getContext()));
                String pattern = pattern(c, hour24);
                if (state.formatter == null || !pattern.equals(state.pattern) || !locale.equals(state.locale)) {
                    state.formatter = new SimpleDateFormat(pattern, locale); state.pattern = pattern; state.locale = locale;
                }
                state.formatter.setTimeZone(calendar.getTimeZone());
                p.setResult(state.formatter.format(calendar.getTime()));
            }
        });
        Api102Runtime.findAndHookMethod(type, "applyShowSecondsFromController", boolean.class, new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                TextView clock = (TextView) p.thisObject;
                if (!isStatusClock(clock)) return;
                state(clock).nativeSeconds = (Boolean) p.args[0];
                HookConfig c = ShadeHook.snapshot();
                if (custom(c)) p.args[0] = c.statusBarClockSeconds;
            }
        });
        Api102Runtime.findAndHookMethod(type, "updateShowSeconds", new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                TextView clock = (TextView) p.thisObject;
                if (!isStatusClock(clock)) return;
                State s = state(clock);
                HookConfig c = ShadeHook.snapshot();
                if (custom(c)) Reflect.setBooleanField(clock, "mShowSeconds", c.statusBarClockSeconds);
                else if (!s.custom) s.nativeSeconds = Reflect.getBooleanField(clock, "mShowSeconds");
            }
        });
        HookCallback appearance = new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                if (!p.hasThrowable() && isStatusClock((TextView) p.thisObject)) {
                    TextView clock = (TextView) p.thisObject;
                    apply(clock);
                    if (custom(ShadeHook.snapshot())) clock.setContentDescription(clock.getText());
                }
            }
        };
        Api102Runtime.findAndHookMethod(type, "updateClock", appearance);
        Api102Runtime.findAndHookMethod(type, "onAttachedToWindow", appearance);
        HookCallback dimensions = new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                State s = clocks.get((TextView) p.thisObject);
                if (s != null) restore((TextView) p.thisObject, s);
            }
            @Override protected void afterHookedMethod(Param p) {
                TextView clock = (TextView) p.thisObject;
                if (p.hasThrowable() || !isStatusClock(clock)) return;
                state(clock).capture(clock); apply(clock);
            }
        };
        Api102Runtime.findAndHookMethod(type, "reloadDimens", dimensions);
        Api102Runtime.findAndHookMethod(type, "computeSecondsFixedLayoutWidth", new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                TextView clock = (TextView) p.thisObject;
                if (isStatusClock(clock) && custom(ShadeHook.snapshot()))
                    p.setResult((int) Math.ceil(Layout.getDesiredWidth(clock.getText(), clock.getPaint())) + padding(clock));
            }
        });
        Api102Runtime.findAndHookMethod(type, "refreshWidth", new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                TextView clock = (TextView) p.thisObject;
                HookConfig c = ShadeHook.snapshot();
                if (!p.hasThrowable() && c.enabled && c.statusBarCompressClock && isStatusClock(clock)) {
                    ViewGroup.LayoutParams lp = clock.getLayoutParams();
                    if (lp != null && lp.width != ViewGroup.LayoutParams.WRAP_CONTENT) { lp.width = ViewGroup.LayoutParams.WRAP_CONTENT; clock.requestLayout(); }
                }
            }
        });
        Api102Runtime.findAndHookMethod(type, "onMeasure", int.class, int.class, new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                TextView clock = (TextView) p.thisObject;
                HookConfig c = ShadeHook.snapshot();
                Measurement outer = measurement.get();
                boolean stat = outer != null && outer.clock == clock;
                if (!isStatusClock(clock) || !c.enabled || (!c.statusBarCompressClock && !(stat && c.statusBarClock))) return;
                // StatClock resets its paint before super.onMeasure. Repair that paint in place;
                // calling setTextSize here would request another layout on every measurement.
                if (custom(c)) clock.getPaint().setTextSize(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, c.statusBarClockSize, clock.getResources().getDisplayMetrics()));
                State s = state(clock);
                clock.setTextScaleX(s.scale);
                int spec = stat ? outer.widthSpec : (Integer) p.args[0];
                int padding = padding(clock);
                float desired = Layout.getDesiredWidth(clock.getText(), clock.getPaint());
                int available = View.MeasureSpec.getMode(spec) == View.MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE : View.MeasureSpec.getSize(spec);
                float scale = c.statusBarCompressClock ? compression(desired, available - padding) : 1f;
                clock.setTextScaleX(s.scale * scale);
                int width = Math.max(1, Math.min(available, (int) Math.ceil(desired * scale) + padding));
                if (stat) p.args[0] = View.MeasureSpec.makeMeasureSpec(
                    View.MeasureSpec.getMode(spec) == View.MeasureSpec.EXACTLY ? available : width, View.MeasureSpec.EXACTLY);
                // C17's seconds branch ignores AT_MOST; cap its cached fixed width too.
                if (Reflect.getBooleanField(clock, "mShowSeconds"))
                    Reflect.setIntField(clock, "mSecondsFixedWidth", width);
                ShadeHook.resourceHit("status-bar-clock-compression");
            }
        });
        Class<?> statClock = Reflect.findClass("com.oplus.systemui.statusbar.widget.StatClock", loader);
        Api102Runtime.findAndHookMethod(statClock, "reloadStatClockDimens", dimensions);
        Api102Runtime.findAndHookMethod(statClock, "onLayout", boolean.class, int.class, int.class, int.class, int.class, new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                TextView clock = (TextView) p.thisObject;
                syncLayoutWidth(clock, clock.getWidth());
            }
        });
        Api102Runtime.findAndHookMethod(statClock, "onMeasure", int.class, int.class, new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                p.setObjectExtra("thunder-clock-measure", measurement.get());
                measurement.set(new Measurement((TextView) p.thisObject, (Integer) p.args[0]));
            }
            @Override protected void afterHookedMethod(Param p) {
                Measurement previous = (Measurement) p.getObjectExtra("thunder-clock-measure");
                if (previous == null) measurement.remove(); else measurement.set(previous);
                if (!p.hasThrowable()) {
                    TextView clock = (TextView) p.thisObject;
                    syncLayoutWidth(clock, clock.getMeasuredWidth());
                }
            }
        });
        // getSmallTime is private; invalidate compiled callers that may have inlined it.
        try { Api102Runtime.deoptimizeCallers(type, "updateClock", "onDarkChanged", "dispatchDemoCommand", "onMeasure", "refreshWidth"); }
        catch (RuntimeException error) { ShadeHook.frameworkFailure("status-bar-clock-deopt", error); }
        try { Api102Runtime.deoptimizeCallers(statClock, "onMeasure", "onLayout", "reloadDimens", "updateConfigurationChanged"); }
        catch (RuntimeException error) { ShadeHook.frameworkFailure("status-bar-statclock-deopt", error); }
    }

    static float compression(float desired, int available) {
        return desired <= 0 ? 1f : Math.min(1f, Math.max(0f, available) / desired);
    }
    static String pattern(HookConfig c, boolean hour24) {
        return (c.statusBarClockDate == 1 ? "M/d " : c.statusBarClockDate == 2 ? "yyyy/M/d " : "")
            + (c.statusBarClockWeekday ? "EEE " : "") + (hour24 ? "HH:mm" : "h:mm")
            + (c.statusBarClockSeconds ? ":ss" : "") + (hour24 ? "" : " a");
    }
    private static boolean custom(HookConfig c) { return c.enabled && c.statusBarClock; }
    private static int padding(TextView clock) { return ((Number) Reflect.callMethod(clock, "getSecondsWidthExtraPadding")).intValue(); }
    static boolean isStatusClock(TextView clock) {
        for (ViewParent parent = clock.getParent(); parent instanceof View view; parent = view.getParent())
            if (view.getClass().getName().equals("com.android.systemui.statusbar.phone.PhoneStatusBarView")) return true;
        return false;
    }
    private static State state(TextView clock) { return clocks.computeIfAbsent(clock, State::new); }
    private static void syncLayoutWidth(TextView clock, int width) {
        HookConfig c = ShadeHook.snapshot();
        if (!c.enabled || (!c.statusBarClock && !c.statusBarCompressClock) || !isStatusClock(clock)) return;
        // The native cache predates our final font/scale. SystemUI must receive
        // the actual clock areas, including the parent's compression constraint.
        String text = clock.getText().toString();
        char separator = text.indexOf(':') >= 0 ? ':' : '.';
        int first = text.indexOf(separator);
        int seconds = first < 0 ? -1 : text.indexOf(separator, first + 1);
        if (seconds >= 0 && Reflect.getBooleanField(clock, "mShowSeconds")) text = text.substring(0, seconds);
        int minutes = (int) Math.ceil(Layout.getDesiredWidth(text, clock.getPaint())) + padding(clock);
        Reflect.setIntField(clock, "cachedMinutesLayoutWidth", Math.min(Math.max(0, width), minutes));
        if (Reflect.getBooleanField(clock, "mShowSeconds")) Reflect.setIntField(clock, "mSecondsFixedWidth", width);
    }
    private static void restore(TextView clock, State s) {
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, s.size);
        clock.setTypeface(s.face); clock.setTextScaleX(s.scale);
    }
    private static void apply(TextView clock) {
        State s = state(clock);
        HookConfig c = ShadeHook.snapshot();
        if (custom(c)) {
            float pixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, c.statusBarClockSize, clock.getResources().getDisplayMetrics());
            if (!s.custom || clock.getTextSize() != pixels || s.bold != c.statusBarClockBold) {
                clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, pixels);
                clock.setTypeface(Typeface.create(s.face, c.statusBarClockBold ? Typeface.BOLD : Typeface.NORMAL));
                s.bold = c.statusBarClockBold;
                Reflect.callMethod(clock, "refreshWidth");
            }
            s.custom = true;
            ShadeHook.resourceHit("status-bar-clock");
        } else if (s.custom) { restore(clock, s); s.custom = false; }
        if (!c.enabled || !c.statusBarCompressClock) clock.setTextScaleX(s.scale);
    }
    static void refresh() {
        StatusBarClockTransitionHook.invalidate();
        for (TextView clock : new ArrayList<>(clocks.keySet())) {
            try {
                State s = clocks.get(clock);
                HookConfig c = ShadeHook.snapshot();
                boolean seconds = custom(c) ? c.statusBarClockSeconds : s.nativeSeconds;
                Reflect.setBooleanField(clock, "mShowSeconds", seconds);
                Reflect.callMethod(clock, "updateShowSeconds");
                apply(clock);
                Reflect.callMethod(clock, "updateClock");
                Reflect.callMethod(clock, "refreshWidth"); clock.requestLayout();
            } catch (Throwable error) { ShadeHook.frameworkFailure("status-bar-clock-refresh", error); }
        }
    }
}
