package android.app;
public class WallpaperManager {
    public static final int FLAG_SYSTEM = 1;
    public interface OnColorsChangedListener { void onColorsChanged(WallpaperColors colors, int which); }
    public static WallpaperManager getInstance(android.content.Context context) { return new WallpaperManager(); }
    public void addOnColorsChangedListener(OnColorsChangedListener listener, android.os.Handler handler) { }
    public WallpaperColors getWallpaperColors(int which) { return new WallpaperColors(); }
}
