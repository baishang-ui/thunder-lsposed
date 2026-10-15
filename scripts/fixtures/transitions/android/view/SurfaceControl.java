package android.view;
import java.util.*;import java.util.concurrent.Executor;
public class SurfaceControl {
 public static class Builder {SurfaceControl surface=new SurfaceControl();public Builder setName(String s){return this;}public Builder setContainerLayer(){return this;}public Builder setParent(SurfaceControl p){surface.parent=p;return this;}public Builder setHidden(boolean b){surface.shown=!b;return this;}public SurfaceControl build(){return surface;}}
 public boolean removed,shown;public float[] matrix;public boolean released;public void release(){released=true;}public Object parent;public int layer=17;public boolean isValid(){return !released;}
 public interface TransactionCommittedListener{void onTransactionCommitted();}
 public static class Transaction implements AutoCloseable {
  public record Move(SurfaceControl child,SurfaceControl parent){}
  public record Layer(SurfaceControl child,int value){}
  public final List<Layer> layers=new ArrayList<>();
  public List<SurfaceControl> removals=new ArrayList<>(),shows=new ArrayList<>();public java.util.Map<SurfaceControl,float[]> matrices=new java.util.IdentityHashMap<>();
  public Transaction remove(SurfaceControl s){removals.add(s);s.release();return this;}
  public Transaction setMatrix(SurfaceControl s,android.graphics.Matrix m,float[] tmp){m.getValues(tmp);matrices.put(s,tmp.clone());return this;}
  public Transaction show(SurfaceControl s){shows.add(s);return this;}
  public Transaction setLayer(SurfaceControl child,int value){layers.add(new Layer(child,value));return this;}
  public final List<Move> moves=new ArrayList<>();final List<Runnable> listeners=new ArrayList<>();
  public Transaction reparent(SurfaceControl child,SurfaceControl parent){moves.add(new Move(child,parent));return this;}
  public Transaction addTransactionCommittedListener(Executor executor,TransactionCommittedListener listener){listeners.add(()->executor.execute(listener::onTransactionCommitted));return this;}
  public void apply(){for(var s:removals){s.removed=true;s.parent=null;}for(var s:shows)s.shown=true;matrices.forEach((s,m)->s.matrix=m.clone());for(var m:moves)m.child.parent=m.parent;for(var l:layers)l.child.layer=l.value;for(var r:listeners)r.run();}
  public void close(){}
 }
}
