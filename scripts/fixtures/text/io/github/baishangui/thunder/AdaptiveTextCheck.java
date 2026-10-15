package io.github.baishangui.thunder;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import android.graphics.drawable.ColorDrawable;
import com.android.systemui.shade.NotificationPanelView;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;

public class AdaptiveTextCheck {
    private static int count;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("PASS: " + message); count++;
    }
    private static void config(Bundle data) throws Exception {
        var field = ShadeHook.class.getDeclaredField("config"); field.setAccessible(true);
        field.set(null, new HookConfig(data));
    }
    private static HookCallback.Param param(TextView text) {
        HookCallback.Param p = new HookCallback.Param(); p.thisObject = text;
        p.args = new Object[]{new android.graphics.Canvas()}; return p;
    }
    public static void main(String[] args) throws Throwable {
        ShadeHook.install("com.android.systemui", "com.android.systemui", AdaptiveTextCheck.class.getClassLoader());
        HookCallback draw = Api102Runtime.callbacks.get("android.widget.TextView#onDraw");
        check(draw != null, "adaptive text drawing hook installed");
        TextView text = new TextView(); text.parent = new NotificationPanelView();
        HookCallback.Param initial = param(text); draw.before(initial); draw.after(initial);
        Bundle data = new Bundle(); data.putBoolean("enabled", true);
        data.putBoolean("shade_tint", true); data.putInt("shade_color", 0xffffffff);
        data.putInt("qs_card_radius", 100); data.putBoolean("qs_card_tint", true);
        data.putInt("qs_card_color", 0xff000000);
        data.putInt("notification_card_radius",100); data.putBoolean("notification_card_tint",true);
        data.putInt("notification_card_color",0xff000000); config(data);
        AdaptiveTextHook.refresh();
        check(text.invalidations == 1, "enabling tint invalidates previously drawn native labels so hardware display lists refresh");
        int nativeColor = text.getCurrentTextColor(), nativePaint = text.getPaint().getColor(), nativeLink = text.getPaint().linkColor;
        HookCallback.Param p = param(text); draw.before(p);
        check(text.getCurrentTextColor() == 0xff000000, "light panel uses black text on its first frame");
        draw.after(p);
        check(text.getCurrentTextColor() == nativeColor && text.getPaint().getColor() == nativePaint
            && text.getPaint().linkColor == nativeLink, "draw restores native color, paint and links without changing stored styles");
        data.putInt("shade_color", 0xff000000); config(data);
        p = param(text); draw.before(p); draw.after(p);
        SystemClock.now = 80; p = param(text); draw.before(p);
        check(text.getCurrentTextColor() == 0xff808080 && text.animationInvalidations > 0, "changed background interpolates black to white using grayscale");
        p.failure = new IllegalStateException("native draw failed"); draw.after(p);
        check(text.getCurrentTextColor() == nativeColor && p.failure != null, "upstream drawing failure retains exception and restores native color");
        SystemClock.now = 160; p = param(text); draw.before(p);
        check(text.getCurrentTextColor() == 0xffffffff, "dark panel finishes at white"); draw.after(p);
        TextView outside = new TextView(); p = param(outside); draw.before(p);
        check(outside.getCurrentTextColor() == nativeColor, "unrelated status bar or application text stays native"); draw.after(p);
        TextView rowText = new TextView(); ExpandableNotificationRow row = new ExpandableNotificationRow();
        rowText.parent = row; row.parent = text.parent;
        data.putInt("shade_color", 0xffffffff); config(data);
        p = param(rowText); draw.before(p);
        check(rowText.getCurrentTextColor() == 0xffffffff, "dark notification material overrides light outer panel when choosing text"); draw.after(p);
        data.putInt("notification_card_color", 0); config(data);
        check(AdaptiveTextHook.background(rowText, ShadeHook.snapshot()) == 0xffffffff, "transparent card inherits panel brightness instead of treating zero RGB as black");
        data.putBoolean("notification_card_tint", false); config(data);
        check(AdaptiveTextHook.background(rowText, ShadeHook.snapshot()) == null, "native notification surface keeps native text even with custom outer panel");
        data.putBoolean("shade_tint",false);data.putBoolean("qs_card_tint",false);
        data.putBoolean("notification_card_tint",true);data.putInt("notification_card_color",0xff000000);config(data);
        TextView notificationOnly = new TextView();notificationOnly.parent=row;
        p=param(notificationOnly);draw.before(p);
        check(notificationOnly.getCurrentTextColor()==0xffffffff&&p.getObjectExtra("adaptive-native-text")!=null,
            "notification-only tint enables adaptive text while panel, QS and banner tints are off");draw.after(p);
        data.putBoolean("notification_card_tint",false);data.putBoolean("shade_tint",true);config(data);
        data.putBoolean("banner_tint", true); data.putInt("banner_color", 0xffffffff); config(data);
        int previousInvalidations = rowText.invalidations;
        NotificationCardHook.track(row.mBackgroundNormal, "banner");
        check(rowText.invalidations > previousInvalidations, "heads-up material changes invalidate existing notification text display lists");
        check(AdaptiveTextHook.background(rowText, ShadeHook.snapshot()) == 0xffffffff, "heads-up reuse selects independent banner material");
        data.putInt("banner_color",0);config(data);
        check(AdaptiveTextHook.background(rowText, ShadeHook.snapshot()) == 0xff202020,
            "floating transparent banner estimate excludes unrelated panel tint");
        data.putInt("banner_color",0xffffffff);config(data);
        TextView button = new TextView(); button.parent = text.parent; button.background = new ColorDrawable(0xff000000);
        check(AdaptiveTextHook.background(button, ShadeHook.snapshot()) == 0xff000000, "local solid surface is composed above panel tint");
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setColor(0xff000000); button.background = pill;
        check(AdaptiveTextHook.background(button, ShadeHook.snapshot()) == 0xff000000, "solid rounded action button uses its own fill above the panel");
        pill.setColors(new int[]{0xff000000, 0xffffffff});
        check(AdaptiveTextHook.background(button, ShadeHook.snapshot()) == null, "multicolor local gradient keeps native text instead of guessing a single surface");
        com.oplus.systemui.qs.base.res.drawable.GradientTileDrawable tile = new com.oplus.systemui.qs.base.res.drawable.GradientTileDrawable();
        CloudCheck.after(tile.getClass().getName() + "#<init>", tile, null);
        View tileView = new View(); tileView.background = tile; tileView.parent = text.parent;
        TextView tileText = new TextView(); tileText.parent = tileView;
        data.putBoolean("qs_card_tint", true); data.putInt("qs_card_color", 0xff000000); config(data);
        check(AdaptiveTextHook.background(tileText, ShadeHook.snapshot()) == 0xff000000, "inactive control card uses its independent custom surface for text");
        for (var state : new com.oplus.systemui.qs.base.res.model.TileViewFlag[]{
                com.oplus.systemui.qs.base.res.model.TileViewFlag.Active,
                com.oplus.systemui.qs.base.res.model.TileViewFlag.Unavailable,
                com.oplus.systemui.qs.base.res.model.TileViewFlag.Inoperable}) {
            tile.setState(new int[]{state.getAttr()});
            check(AdaptiveTextHook.background(tileText, ShadeHook.snapshot()) == null, state + " control card retains native semantic text color");
        }
        data.putBoolean("shade_tint", false); data.putBoolean("qs_card_tint", false); data.putBoolean("banner_tint", false); config(data);
        p = param(text); draw.before(p);
        check(text.getCurrentTextColor() == nativeColor, "restoring custom tint settings immediately returns text to system color"); draw.after(p);
        data.putBoolean("enabled", false); config(data); AdaptiveTextHook.refresh();
        p = param(text); draw.before(p);
        check(text.getCurrentTextColor() == nativeColor, "master off immediately uses native text without stale animation"); draw.after(p);
        System.out.println("Verified " + count + " adaptive text checks; device rendering not tested.");
    }
}
