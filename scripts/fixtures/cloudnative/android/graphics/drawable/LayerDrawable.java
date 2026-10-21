package android.graphics.drawable;
public class LayerDrawable extends Drawable {
 public static final int PADDING_MODE_NEST=0,PADDING_MODE_STACK=1;private final Drawable[] layers;private final int[][] insets;private final int[] widths,heights;private int paddingMode;
 public LayerDrawable(Drawable[] values){layers=values;insets=new int[values.length][4];widths=new int[values.length];heights=new int[values.length];java.util.Arrays.fill(widths,-1);java.util.Arrays.fill(heights,-1);}
 public int getNumberOfLayers(){return layers.length;}public Drawable getDrawable(int index){return layers[index];}
 public void setPaddingMode(int mode){paddingMode=mode;}public int getPaddingMode(){return paddingMode;}
 public void setLayerInset(int i,int l,int t,int r,int b){insets[i]=new int[]{l,t,r,b};}public int getLayerInsetLeft(int i){return insets[i][0];}public int getLayerInsetTop(int i){return insets[i][1];}public int getLayerInsetRight(int i){return insets[i][2];}public int getLayerInsetBottom(int i){return insets[i][3];}
 public void setLayerSize(int i,int w,int h){widths[i]=w;heights[i]=h;}public int getLayerWidth(int i){return widths[i];}public int getLayerHeight(int i){return heights[i];}
 @Override public void setBounds(int l,int t,int r,int b){super.setBounds(l,t,r,b);for(int i=0;i<layers.length;i++)layers[i].setBounds(l+insets[i][0],t+insets[i][1],r-insets[i][2],b-insets[i][3]);}
}
