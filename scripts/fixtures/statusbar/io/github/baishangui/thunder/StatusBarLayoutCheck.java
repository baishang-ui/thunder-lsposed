package io.github.baishangui.thunder;
import android.os.Bundle;
import android.view.View;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.phone.PhoneStatusBarView;
import com.oplus.systemui.statusbar.phone.PhoneStatusBarViewExImpl;

public final class StatusBarLayoutCheck {
    private static int checks;
    static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);checks++;}
    static void config(Bundle b) throws Exception {
        var f=ShadeHook.class.getDeclaredField("config");f.setAccessible(true);f.set(null,new HookConfig(b));
    }
    static HookCallback callback(Class<?> type,String name){return Api102Runtime.callbacks.get(type.getName()+"#"+name);}
    static void layout(PhoneStatusBarViewExImpl owner) throws Throwable {
        var cb=callback(owner.getClass(),"updateContentsPadding");var p=StatusBarIconCheck.param(owner);
        cb.before(p);owner.updateContentsPadding();cb.after(p);
    }
    static void after(Clock clock,String name) throws Throwable {
        callback(Clock.class,name).after(StatusBarIconCheck.param(clock));
    }
    public static void main(String[] args)throws Throwable {
        StatusBarLayoutHook.install(StatusBarLayoutCheck.class.getClassLoader());
        StatusBarClockHook.install(StatusBarLayoutCheck.class.getClassLoader());
        var owner=new PhoneStatusBarViewExImpl();
        var b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("status_bar_margins",true);
        b.putInt("status_bar_left",8);b.putInt("status_bar_right",24);
        b.putBoolean("status_bar_center_enabled",true);b.putInt("status_bar_center",21);
        owner.cutoutLeftPadding=20;config(b);layout(owner);
        check(owner.start.getPaddingStart()==16 && owner.end.getPaddingEnd()==24,"left camera safe inset wins over smaller custom margin");
        check(owner.start.getPaddingEnd()==13 && owner.end.getPaddingStart()==11,"odd center gap split without losing a pixel");
        for(int i=0;i<20;i++)layout(owner);
        check(owner.end.getPaddingStart()==11,"repeated native refresh does not accumulate inward padding");
        owner.statusBarContents.layoutDirection=owner.start.layoutDirection=owner.end.layoutDirection=1;
        layout(owner);
        check(owner.start.getPaddingStart()==20 && owner.end.getPaddingEnd()==20,"RTL swaps physical left/right margins while retaining cutout safety");
        b.putBoolean("enabled",false);config(b);layout(owner);
        check(owner.start.getPaddingStart()==12 && owner.end.getPaddingEnd()==16 && owner.end.getPaddingStart()==0,"module off restores all native padding including the injected center gap");

        var root=new PhoneStatusBarView();var clock=new Clock();root.addView(clock);clock.res=new android.content.res.Resources(){public android.util.DisplayMetrics getDisplayMetrics(){var m=new android.util.DisplayMetrics();m.density=1;return m;}};
        clock.setTextSize(0,14);clock.setTextScaleX(1);clock.mShowSeconds=false;
        b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("status_bar_clock",true);
        b.putBoolean("status_bar_clock_seconds",true);b.putInt("status_bar_clock_size",20);
        b.putInt("status_bar_clock_date",2);b.putBoolean("status_bar_clock_weekday",true);
        b.putInt("status_bar_clock_hour_format",2);b.putBoolean("status_bar_compress_clock",true);config(b);
        after(clock,"onAttachedToWindow");
        StatusBarClockHook.refresh();
        check(clock.mShowSeconds && clock.secondUpdates>0 && clock.getTextSize()==20,"live clock settings use native second lifecycle and apply size");
        clock.mCalendar.setTimeZone(java.util.TimeZone.getTimeZone("GMT+08:00"));
        clock.mCalendar.set(2026,9,16,21,7,5);
        var p=StatusBarIconCheck.param(clock);p.setResult("native");callback(Clock.class,"getSmallTime").after(p);
        check(p.getResult().equals("2026/10/16 Fri 9:07:05 PM"),"format uses clock calendar, timezone, date, weekday, twelve-hour time and seconds");
        var foreign=new Clock();p=StatusBarIconCheck.param(foreign);p.setResult("native");callback(Clock.class,"getSmallTime").after(p);
        check(p.getResult().equals("native"),"lockscreen and shade clocks are excluded");
        clock.setText("2026/10/16 Fri 9:07:05 PM");
        p=StatusBarIconCheck.param(clock,View.MeasureSpec.makeMeasureSpec(90,View.MeasureSpec.AT_MOST),40);
        callback(Clock.class,"onMeasure").before(p);
        check(clock.getTextScaleX()<1 && clock.mSecondsFixedWidth<=90,"long seconds clock compresses and respects available measurement width");
        p=StatusBarIconCheck.param(clock,View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.AT_MOST),40);
        callback(Clock.class,"onMeasure").before(p);
        check(clock.getTextScaleX()==1,"wider space restores original horizontal scale");
        var stat=new com.oplus.systemui.statusbar.widget.StatClock();root.addView(stat);stat.res=clock.res;
        stat.setTextSize(0,14);stat.setText("2026/10/16 Fri 9:07:05 PM");after(stat,"onAttachedToWindow");
        var outer=StatusBarIconCheck.param(stat,View.MeasureSpec.makeMeasureSpec(90,View.MeasureSpec.AT_MOST),40);
        var statMeasure=callback(stat.getClass(),"onMeasure");statMeasure.before(outer);
        // Actual C17 StatClock overwrites both paint size and its parent's width constraint.
        stat.getPaint().setTextSize(14);
        var inner=StatusBarIconCheck.param(stat,View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),40);
        callback(Clock.class,"onMeasure").before(inner);
        check(stat.getPaint().textSize==20 && View.MeasureSpec.getSize((Integer)inner.args[0])<=90 && stat.getTextScaleX()<1,
            "StatClock font reset and forced width cannot override custom size or original parent constraint");
        statMeasure.after(outer);
        inner=StatusBarIconCheck.param(clock,View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.AT_MOST),40);
        callback(Clock.class,"onMeasure").before(inner);
        check(clock.getTextScaleX()==1,"StatClock measurement scope does not leak into another clock");
        stat.actualWidth=90;stat.cachedMinutesLayoutWidth=700;stat.mShowSeconds=true;stat.mSecondsFixedWidth=700;
        var area=StatusBarIconCheck.param(stat,true,0,0,90,30);
        callback(stat.getClass(),"onLayout").before(area);stat.onLayout(true,0,0,90,30);
        check(stat.publishedMinutes>0 && stat.publishedMinutes<=90 && stat.publishedFull==90,
            "SystemUI receives actual compressed minutes/full areas before native onLayout publishes them");
        b.putBoolean("status_bar_clock_seconds",false);config(b);stat.mShowSeconds=false;stat.mSecondsFixedWidth=-1;
        stat.actualWidth=180;stat.setText("21:07");stat.setTextScaleX(1);stat.getPaint().setTextSize(20);stat.cachedMinutesLayoutWidth=700;
        callback(stat.getClass(),"onLayout").before(area);stat.onLayout(true,0,0,180,30);
        check(stat.publishedMinutes>=64 && stat.publishedMinutes<=65 && stat.publishedFull==stat.publishedMinutes,"minutes-only clock repairs stale width even without native seconds refresh");
        check(StatusBarClockHook.compression(120,60)==.5f && StatusBarClockHook.compression(0,0)==1f,"compression handles half width and empty text");
        b.putBoolean("enabled",false);config(b);StatusBarClockHook.refresh();
        check(!clock.mShowSeconds && clock.getTextSize()==14 && clock.getTextScaleX()==1,"module off restores native seconds, text size and scale");
        android.os.Binder.uid=2000;
        var saved=SettingsProvider.preferences(new android.content.Context());
        var edit=saved.edit();
        for(String key:new String[]{"status_bar_margins","status_bar_center_enabled","status_bar_compress_clock",
                "status_bar_clock","status_bar_clock_seconds","status_bar_clock_weekday","status_bar_clock_bold"})edit.putBoolean(key,true);
        edit.putInt("status_bar_left",7).putInt("status_bar_right",31).putInt("status_bar_center",27)
            .putInt("status_bar_clock_hour_format",2).putInt("status_bar_clock_date",1).putInt("status_bar_clock_size",21).apply();
        var fallback=new HookConfig(new SettingsProvider().call("settings",null,null));
        check(fallback.statusBarMargins && fallback.statusBarCenterEnabled && fallback.statusBarCompressClock
            && fallback.statusBarClock && fallback.statusBarClockSeconds && fallback.statusBarClockWeekday && fallback.statusBarClockBold
            && fallback.statusBarLeft==7 && fallback.statusBarRight==31 && fallback.statusBarCenter==27
            && fallback.statusBarClockHourFormat==2 && fallback.statusBarClockDate==1 && fallback.statusBarClockSize==21,
            "provider fallback transports every layout and clock option");
        System.out.println("Verified "+checks+" status layout and clock checks; device rendering remains unverified.");
    }
}
