package io.github.baishangui.thunder;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Direct color selection using Settings' own color wheel and native swatches. */
final class SystemSettingsPalette {
    private static final String WHEEL = "com.oplus.settings.feature.display.atmospherelight.color.AtmosphereLightColorPickRound";
    private SystemSettingsPalette() { }
    static final class Editor {
        final int[] selected;
        final Dialog dialog;
        Editor(int[] selected, Dialog dialog) { this.selected = selected; this.dialog = dialog; }
        int color() { return selected[0]; }
    }
    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    private static TextView label(Context context, String text) {
        TextView label = (TextView)Reflect.newInstance(Reflect.findClass("com.coui.appcompat.textview.COUITextView", context.getClassLoader()), context);
        label.setText(text); label.setGravity(Gravity.CENTER);
        return label;
    }
    static Editor show(Context context, ClassLoader loader, String title, int initial, IntConsumer apply, Runnable closed) {
        int[] selected = {initial};
        Object builder = Reflect.newInstance(Reflect.findClass("com.coui.appcompat.dialog.COUIAlertDialogBuilder", loader), context);
        Reflect.callMethod(builder, "setBlurBackgroundDrawable", true);
        Context themed = (Context)Reflect.callMethod(builder, "getContext");
        LinearLayout content = new LinearLayout(themed);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(themed, 24), 0, dp(themed, 24), dp(themed, 8));
        FrameLayout wheelArea = new FrameLayout(themed);
        View wheel = (View)Reflect.newInstance(Reflect.findClass(WHEEL, loader), themed);
        wheel.setContentDescription("色环，点选或拖动选择颜色");
        int radius = dp(themed, Math.min(104, Math.max(56, (context.getResources().getConfiguration().screenWidthDp - 144) / 2)));
        int thickness = dp(themed, 24), halo = dp(themed, 16);
        Reflect.callMethod(wheel, "setCanvasParameter", radius, thickness, halo, null, null,
            String.format(Locale.ROOT, "#%06X", initial & 0xffffff));
        wheelArea.addView(wheel, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        ImageView preview = (ImageView)Reflect.newInstance(Reflect.findClass("com.coui.appcompat.imageview.COUIRoundImageView", loader), themed);
        int circle;
        try {
            java.lang.reflect.Field type = preview.getClass().getDeclaredField("CIRCLE");
            type.setAccessible(true); circle = type.getInt(null);
        }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        Reflect.callMethod(preview, "setType", circle);
        preview.setContentDescription("当前选择的颜色");
        wheelArea.addView(preview, new FrameLayout.LayoutParams(dp(themed, 72), dp(themed, 72), Gravity.CENTER));
        content.addView(wheelArea, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (radius + halo + dp(themed, 8)) * 2));
        TextView code = label(themed, ""); content.addView(code);
        TextView hint = label(themed, "点选色环或下方颜色");
        hint.setPadding(0, dp(themed, 4), 0, dp(themed, 8)); content.addView(hint);
        ArrayList<View> tones = new ArrayList<>();
        ArrayList<View> toneTargets = new ArrayList<>();
        ArrayList<TextView> toneLabels = new ArrayList<>();
        int[] toneColors = {initial | 0xff000000, 0xffffffff, 0xffdddddd, 0xff999999, 0xff444444, 0xff000000};
        String[] toneNames = {"原色", "白色", "浅灰", "灰色", "深灰", "黑色"};
        TextView opacity = label(themed, "");
        opacity.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        opacity.setPadding(0, dp(themed, 12), 0, 0);
        View opacitySlider = (View)Reflect.newInstance(Reflect.findClass("com.coui.appcompat.seekbar.COUISeekBar", loader), themed);
        opacitySlider.setContentDescription("不透明度");
        Reflect.callMethod(opacitySlider, "setMax", 255);
        Reflect.callMethod(opacitySlider, "setProgress", initial >>> 24);
        Runnable update = () -> {
            int color = selected[0];
            preview.setImageDrawable(new ColorDrawable(color));
            code.setText(String.format(Locale.ROOT, "#%08X", color));
            opacity.setText("不透明度  " + Math.round((color >>> 24) * 100f / 255) + "%");
            Reflect.callMethod(opacitySlider, "setProgressContentDescription", opacity.getText().toString());
            for (int i = 0; i < tones.size(); i++) {
                View swatch = tones.get(i);
                Reflect.callMethod(swatch, "setColor", toneColors[i]);
                boolean checked = (color & 0xffffff) == (toneColors[i] & 0xffffff);
                toneTargets.get(i).setSelected(checked);
                toneLabels.get(i).setText((checked ? "✓ " : "") + toneNames[i]);
                toneTargets.get(i).setContentDescription((i == 0 ? "原颜色 " : "颜色 ") +
                    String.format(Locale.ROOT, "#%06X", toneColors[i] & 0xffffff) + (checked ? "，已选中" : ""));
            }
        };
        boolean[] ready = {false};
        Class<?> contract = Reflect.findClass(WHEEL + "$OnColorSelectedListener", loader);
        Object listener = Proxy.newProxyInstance(loader, new Class<?>[]{contract}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "ThunderColorSelection";
                case "onColorSelected":
                    if (ready[0]) {
                        selected[0] = (selected[0] & 0xff000000) | (((Number)args[0]).intValue() & 0xffffff);
                        update.run();
                    }
                    break;
                default: break;
            }
            return null;
        });
        Reflect.callMethod(wheel, "setOnColorSelectedListener", listener);
        ready[0] = true; // Native listener registration reports its closest preset; preserve arbitrary existing colors.
        LinearLayout strip = new LinearLayout(themed); strip.setGravity(Gravity.CENTER);
        for (int index = 0; index < toneColors.length; index++) {
            final int chosen = index;
            LinearLayout cell = new LinearLayout(themed);
            cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER);
            View swatch = (View)Reflect.newInstance(Reflect.findClass("com.oplus.settings.feature.othersettings.cameraeffect.CameraColorPickerItemView", loader), themed);
            cell.addView(swatch, new LinearLayout.LayoutParams(dp(themed, 28), dp(themed, 28)));
            TextView name = label(themed, toneNames[index]);
            name.setPadding(0, dp(themed, 6), 0, 0);
            cell.addView(name);
            cell.setOnClickListener(view -> {
                selected[0] = (selected[0] & 0xff000000) | (toneColors[chosen] & 0xffffff); update.run();
            });
            cell.setClickable(true); cell.setFocusable(true);
            swatch.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            strip.addView(cell, new LinearLayout.LayoutParams(0, dp(themed, 64), 1));
            tones.add(swatch); toneTargets.add(cell); toneLabels.add(name);
        }
        content.addView(strip);
        content.addView(opacity);
        content.addView(opacitySlider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(themed, 48)));
        Class<?> seekListener = Reflect.findClass("com.coui.appcompat.seekbar.COUISeekBar$OnSeekBarChangeListener", loader);
        Reflect.callMethod(opacitySlider, "setOnSeekBarChangeListener", Proxy.newProxyInstance(loader, new Class<?>[]{seekListener}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "ThunderOpacitySelection";
                case "onProgressChanged":
                    if (Boolean.TRUE.equals(args[2])) {
                        selected[0] = SystemSettingsInputs.withChannel(selected[0], 24, ((Number)args[1]).intValue());
                        update.run();
                    }
                    break;
                default: break;
            }
            return null;
        }));
        update.run();
        ScrollView scroll = new ScrollView(themed); scroll.addView(content);
        Reflect.callMethod(builder, "setTitle", title);
        Reflect.callMethod(builder, "setView", scroll);
        Reflect.callMethod(builder, "setNegativeButton", "取消", (DialogInterface.OnClickListener)(d, w) -> { });
        Reflect.callMethod(builder, "setPositiveButton", "保存", (DialogInterface.OnClickListener)(d, w) -> apply.accept(selected[0]), true);
        Reflect.callMethod(builder, "setOnDismissListener", (DialogInterface.OnDismissListener)dialog -> {
            closed.run();
        });
        return new Editor(selected, (Dialog)Reflect.callMethod(builder, "show"));
    }
}
