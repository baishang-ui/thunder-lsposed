package android.graphics;
public class RenderNode {
 public static int count;public static RenderNode last;public boolean recording,discarded,failEnd;public float alpha;public RenderEffect effect;public final Canvas canvas=new Canvas(true);
 public RenderNode(String name){count++;last=this;}public void setClipToBounds(boolean value){}public void setPosition(int l,int t,int r,int b){}
 public Canvas beginRecording(int w,int h){if(recording)throw new IllegalStateException("nested record");recording=true;return canvas;}
 public void endRecording(){if(failEnd){failEnd=false;throw new IllegalStateException("end failed");}if(!recording)throw new IllegalStateException("not recording");recording=false;}
 public void setRenderEffect(RenderEffect e){effect=e;}public void setAlpha(float value){alpha=value;}public void discardDisplayList(){discarded=true;}
}
