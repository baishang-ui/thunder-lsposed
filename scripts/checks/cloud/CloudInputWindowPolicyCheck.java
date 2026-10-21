package io.github.baishangui.thunder;

/** Exercises the production window ownership policy without a fake Android dispatcher. */
public final class CloudInputWindowPolicyCheck {
    private static final int NATIVE=0x1840120,NOT_TOUCHABLE=0x10,WATCH_OUTSIDE=0x40000;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        CloudInputWindowPolicy policy=new CloudInputWindowPolicy();
        Object primary=new Object(),secondary=new Object(),revision=new Object();
        int owned=policy.acquire(primary,NATIVE,revision);
        check((owned&NOT_TOUCHABLE)!=0,"hidden native window still consumes relocated card DOWN");
        check((owned&WATCH_OUTSIDE)==0,"hidden native window still dismisses visible card from ACTION_OUTSIDE");
        check((owned&~(NOT_TOUCHABLE|WATCH_OUTSIDE))==(NATIVE&~(NOT_TOUCHABLE|WATCH_OUTSIDE)),"focus/back flags changed");
        check(policy.acquire(primary,owned,revision)==owned,"same surface acquired twice");
        check(policy.acquire(secondary,owned,revision)==owned,"secondary card changed shared native flags");
        check(policy.release(primary,owned,revision)==owned,"one card restored a window still owned by another");
        check(policy.owned(),"shared window lost its last owner too early");
        check(policy.release(secondary,owned,revision)==NATIVE,"original native flags did not restore exactly");
        check(!policy.owned(),"last surface leaked window ownership");
        check(policy.release(secondary,NATIVE,revision)==NATIVE,"duplicate cleanup changed native flags");

        // Native window.i.f() returns new LayoutParams on focus/back updates. Its latest
        // host flags must replace our saved baseline, including an existing NOT_TOUCHABLE.
        Object first=new Object(),updated=new Object();
        owned=policy.acquire(primary,NATIVE,first);
        int hostUpdate=NATIVE|NOT_TOUCHABLE|0x20000;
        owned=policy.acquire(primary,hostUpdate,updated);
        check(policy.release(primary,owned,updated)==hostUpdate,"new host focus/back parameters were discarded");

        // A host edit of unrelated flags on the same params survives release too.
        owned=policy.acquire(primary,NATIVE,first);
        int edited=owned|0x20000;
        check(policy.release(primary,edited,first)==(NATIVE|0x20000),"unrelated host flag update was overwritten");

        // A framework clone of the already-overridden LayoutParams is not a fresh host
        // request to keep NOT_TOUCHABLE forever after the independent surface goes away.
        owned=policy.acquire(primary,NATIVE,first);
        owned=policy.acquire(primary,owned|0x20000,new Object());
        check(policy.release(primary,owned,new Object())==(NATIVE|0x20000),"cloned overridden params leaked pointer ownership");
        System.out.println("CloudInputWindowPolicyCheck: 14 native window ownership checks passed");
    }
}
