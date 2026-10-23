package io.github.baishangui.thunder;

/** Scope for coalescing input geometry writes within a synchronous render tick. */
final class CloudFrameBatch {
    private int depth;
    private boolean dirty;
    void begin(){depth++;}
    boolean defer(){if(depth==0)return false;dirty=true;return true;}
    boolean end(){
        if(depth<=0)throw new IllegalStateException("Unbalanced cloud frame batch");
        if(--depth!=0)return false;
        boolean flush=dirty;dirty=false;return flush;
    }
}
