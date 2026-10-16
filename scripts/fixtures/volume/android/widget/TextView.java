package android.widget;
public class TextView extends android.view.View {
    public TextView(android.content.Context c){this();}public void setText(CharSequence s){text=s;}public void setTextSize(float s){textSize=s;}public void setTextColor(int c){mCurTextColor=c;}public int gravity;public int getGravity(){return gravity;}public void setGravity(int g){gravity=g;}public TextView() { background = null; }
    public float textScale=1;public float getTextScaleX(){return textScale;}public void setTextScaleX(float x){textScale=x;}public int fontChanges;public float textSize=14;public android.graphics.Typeface typeface=new android.graphics.Typeface();public float getTextSize(){return textSize;}public android.graphics.Typeface getTypeface(){return typeface;}public void setTextSize(int unit,float pixels){fontChanges++;textSize=pixels;paint.setTextSize(pixels);}public void setTypeface(android.graphics.Typeface face){typeface=face;}
    public CharSequence text="23:59:59";public CharSequence getText(){return text;}
    public int getCompoundPaddingLeft(){return 2;}public int getCompoundPaddingRight(){return 2;}
    public int getCompoundPaddingTop(){return 0;}public int getCompoundPaddingBottom(){return 0;}
    public boolean getIncludeFontPadding(){return true;}
    public int invalidations;
    @Override public void invalidate() { invalidations++; }
    public int mCurTextColor = 0xff445566;
    private final android.text.TextPaint paint = new android.text.TextPaint();
    public int getCurrentTextColor() { return mCurTextColor; }
    public android.text.TextPaint getPaint() { return paint; }
    protected void onDraw(android.graphics.Canvas canvas) { paint.setColor(mCurTextColor); }
}
