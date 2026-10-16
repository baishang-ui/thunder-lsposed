package com.oplus.systemui.statusbar.phone;
import android.view.*;
public class PhoneStatusBarViewExImpl {
    public ViewGroup statusBarContents = new ViewGroup();
    public View start = new View(), end = new View();
    public int cutoutLeftPadding, cutoutRightPadding;
    public PhoneStatusBarViewExImpl() {
        statusBarContents.res=new android.content.res.Resources(){
            public android.util.DisplayMetrics getDisplayMetrics(){var m=new android.util.DisplayMetrics();m.density=1;return m;}
        };
        statusBarContents.res.ids.put("status_bar_start_side_container",11);
        statusBarContents.res.ids.put("status_bar_end_side_container",12);
        statusBarContents.res.ids.put("status_bar_left_clock_starting_padding",13);
        statusBarContents.id=10; start.id=11; end.id=12;
        statusBarContents.addView(start); statusBarContents.addView(end);
        updateContentsPadding();
    }
    public void updateContentsPadding() {
        // C17 deliberately preserves the end container's inward padding.
        start.setPaddingRelative(12, 1, 3, 2);
        end.setPaddingRelative(end.getPaddingStart(), 1, 16, 2);
    }
}
