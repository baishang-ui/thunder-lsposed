package android.widget;
public class TextView extends android.view.View {
    public TextView() { background = null; }
    public int invalidations;
    @Override public void invalidate() { invalidations++; }
    public int mCurTextColor = 0xff445566;
    private final android.text.TextPaint paint = new android.text.TextPaint();
    public int getCurrentTextColor() { return mCurTextColor; }
    public android.text.TextPaint getPaint() { return paint; }
    protected void onDraw(android.graphics.Canvas canvas) { paint.setColor(mCurTextColor); }
}
