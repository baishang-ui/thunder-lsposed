package io.github.baishangui.thunder;

/** Card input ownership follows transition/native lifecycle rather than clamped form progress. */
public final class CloudInputOwnershipCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        CloudInputOwnership state=new CloudInputOwnership();
        Object window=new Object(),other=new Object();
        check(state.capsuleRoot(window),"first capsule root acquires the native gesture stream");
        check(!state.capsuleRoot(window),"switching active capsule content in the same root must preserve DOWN/MOVE/UP");
        check(state.capsuleRoot(other),"replacing the native capsule root cancels the old gesture stream");
        check(state.bind(window),"first root is a root change");state.begin(true,true);
        check(state.captureOutside()&&state.keepLease(true),"morph must own input at progress zero");
        check(state.keepsCardPresentation(),"opening owns the card even before form progress advances");
        check(!state.bind(window),"same-root reversal must not release native lease");
        state.begin(true,true);check(state.keepLease(true),"reversal preserves transition ownership");
        state.settle(false);
        check(!state.captureOutside(),"settled capsule must not keep outside capture");
        check(state.keepLease(true),"native hide can happen after settle in the same call stack");
        check(!state.keepLease(false),"native hidden state must release ownership");
        check(!state.keepLease(true),"a later unrelated native show must not resurrect the closed session");
        state.begin(true,true);state.settle(true);
        check(state.captureOutside()&&state.keepLease(true),"expanded card owns its outside dismissal");
        check(state.keepsCardPresentation(),"background capsule events must preserve expanded presentation");
        // A handled OUTSIDE only queues a request. Until native begin(close),
        // rejected/deferred requests must leave the visible card interactive.
        check(state.acceptsInput()&&state.captureOutside(),"waiting for native close must preserve visible card input");
        check(state.keepLease(true),"pending close must not hand input to the transparent native window");
        state.settle(true);
        check(state.acceptsInput(),"late opening end keeps input when no native close has started");
        state.begin(true,false);
        check(!state.keepsCardPresentation(),"a real card close allows capsule ownership handoff");
        check(!state.acceptsInput()&&!state.captureOutside()&&state.keepLease(true),"close begins without Body input and retains the native lease");
        check(state.acceptsCapsuleInput(),"closing must accept a fresh capsule DOWN without waiting for visual/native end");
        check(!state.captureOutside(),"capsule reversal input cannot restore outside capture");
        state.settle(false);
        check(state.acceptsInput()&&!state.captureOutside(),"visual endpoint restores capsule input before native cleanup");
        check(!state.acceptsCapsuleInput(),"settled capsule returns to normal input routing");
        check(state.keepLease(true),"late native hide still requires its pointer lease");
        state.begin(true,true);
        check(state.acceptsInput()&&state.captureOutside(),"reopening reacquires Body input immediately");
        state.begin(true,false);state.begin(true,true);
        check(state.acceptsInput()&&state.captureOutside(),"closing reversal restores input without waiting for an endpoint");
        state.begin(true,false);state.begin(false,false);
        check(!state.acceptsInput()&&!state.captureOutside(),"a capsule scene replacing dismissal cannot restore Body input before its endpoint");
        check(!state.acceptsCapsuleInput(),"a non-card replacement cannot resurrect the dismissed card's reversal target");
        state.settle(false);
        check(state.acceptsInput()&&!state.captureOutside(),"replacement capsule restores only its own input at the visual endpoint");
        check(state.bind(other),"new native root must release prior ownership");
        check(!state.captureOutside()&&!state.keepLease(true),"new root waits for an explicit transition");
        state.begin(false,false);check(!state.captureOutside()&&!state.keepLease(true),"capsule-only entry cannot capture the display");
        state.begin(true,true);state.begin(false,false);
        check(!state.captureOutside()&&state.keepLease(true),"capsule scene replacing a card motion keeps only the late native handoff");
        state.settle(false);check(!state.keepLease(false),"replaced motion must release after native hide");
        state.begin(true,true);check(!state.keepLease(false),"native hidden while transitioning stops window lease");
        check(state.captureOutside(),"a waiting transition can capture after native mounting");
        System.out.println("CloudInputOwnershipCheck: lifecycle, reversal and late-hide checks passed");
    }
}
