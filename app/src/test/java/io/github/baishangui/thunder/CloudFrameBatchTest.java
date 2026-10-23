package io.github.baishangui.thunder;

import org.junit.Test;
import static org.junit.Assert.*;

public class CloudFrameBatchTest {
    @Test public void allCardWritesFlushOnceAtEndOfAnimationTick() {
        CloudFrameBatch batch=new CloudFrameBatch();
        assertFalse(batch.defer()); // A native close outside a render batch remains immediate.
        batch.begin();
        for(int card=0;card<8;card++)for(int write=0;write<3;write++)assertTrue(batch.defer());
        assertTrue(batch.end());
        assertFalse(batch.defer());
        batch.begin();assertFalse(batch.end());
    }
    @Test public void nestedNativeCallbacksCannotFlushAnIncompleteStack() {
        CloudFrameBatch batch=new CloudFrameBatch();
        batch.begin();batch.begin();assertTrue(batch.defer());
        assertFalse(batch.end());assertTrue(batch.defer());assertTrue(batch.end());
        assertFalse(batch.defer());
    }
    @Test public void finalizationAfterFailureDoesNotLeaveInputDeferred() {
        CloudFrameBatch batch=new CloudFrameBatch();
        batch.begin();
        try {assertTrue(batch.defer());throw new IllegalArgumentException("native frame failed");}
        catch(IllegalArgumentException expected) { }
        finally {assertTrue(batch.end());}
        assertFalse(batch.defer());
        batch.begin();assertFalse(batch.end());
    }
}
