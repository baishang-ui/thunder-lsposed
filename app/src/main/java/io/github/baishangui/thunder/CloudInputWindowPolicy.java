package io.github.baishangui.thunder;

import java.util.IdentityHashMap;
import java.util.Set;

/** Native window flags while an independent cloud surface owns pointer input. */
final class CloudInputWindowPolicy {
    // Keep this policy usable by the host JVM checks; values are Android window constants.
    private static final int NOT_TOUCHABLE=0x10,WATCH_OUTSIDE_TOUCH=0x40000;
    private static final int POINTER_FLAGS=NOT_TOUCHABLE|WATCH_OUTSIDE_TOUCH;
    private final Set<Object> owners=java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private Object revision;
    private int hostFlags;

    int acquire(Object owner,int flags,Object revision) {
        observe(flags,revision);owners.add(owner);
        // The Body handles outside dismissal. Leaving the native watcher active would
        // dismiss the card when a DOWN is delivered to that different Body window.
        return (flags|NOT_TOUCHABLE)&~WATCH_OUTSIDE_TOUCH;
    }
    int release(Object owner,int flags,Object revision) {
        if(!owners.contains(owner))return flags;
        observe(flags,revision);owners.remove(owner);
        return owned()?(flags|NOT_TOUCHABLE)&~WATCH_OUTSIDE_TOUCH:hostFlags;
    }
    boolean owned(){return !owners.isEmpty();}
    private void observe(int flags,Object revision) {
        // A new native factory result includes its own WATCH_OUTSIDE policy. A clone
        // of our overridden params still has exactly NOT_TOUCHABLE, so keep the saved
        // pointer bits instead of treating our override as a permanent host request.
        if(!owned()||this.revision!=revision&&(flags&POINTER_FLAGS)!=NOT_TOUCHABLE)hostFlags=flags;
        else hostFlags=(flags&~POINTER_FLAGS)|(hostFlags&POINTER_FLAGS);
        this.revision=revision;
    }
}
