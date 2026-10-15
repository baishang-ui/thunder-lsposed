package io.github.baishangui.thunder;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class StatusBarTransitionTest {
    private static StatusBarTransition.Candidate app(int mode) {
        return new StatusBarTransition.Candidate(1, 1, mode, 0, true, false);
    }
    private static final StatusBarTransition.Candidate HOME =
        new StatusBarTransition.Candidate(2, 1, 4, 0, true, false);
    @Test public void selectsOpeningAndClosingAppBesideHome() {
        for (int mode : new int[]{1, 2, 3, 4}) {
            assertEquals(1, StatusBarTransition.select(mode, List.of(HOME, app(mode))));
        }
    }
    @Test public void rejectsAmbiguousAndUnsupportedScenes() {
        for (List<StatusBarTransition.Candidate> scene : List.of(
                List.of(app(1)), List.of(HOME), List.of(HOME, app(1), app(2)),
                List.of(HOME, new StatusBarTransition.Candidate(1, 6, 1, 0, true, false)),
                List.of(HOME, new StatusBarTransition.Candidate(1, 1, 1, 1, true, false)),
                List.of(HOME, new StatusBarTransition.Candidate(1, 1, 1, 0, false, false)),
                List.of(HOME, new StatusBarTransition.Candidate(1, 1, 1, 0, true, true)),
                List.of(HOME, new StatusBarTransition.Candidate(3, 1, 1, 0, true, false)))) {
            assertEquals(-1, StatusBarTransition.select(1, scene));
        }
        assertEquals(-1, StatusBarTransition.select(6, List.of(HOME, app(1))));
        assertEquals(-1, StatusBarTransition.select(1, List.of(HOME, app(6))));
    }
    @Test public void selectsCapturedColorOsRecentsSceneOnlyWithOutgoingAppAndIncomingHome() {
        var homeFront = new StatusBarTransition.Candidate(2, 1, 3, 0, true, false);
        assertEquals(1, StatusBarTransition.select(1021, List.of(homeFront, app(4))));
        assertEquals(-1, StatusBarTransition.select(1021, List.of(HOME, app(1))));
        assertEquals(-1, StatusBarTransition.select(1022, List.of(homeFront, app(4))));
    }
    private record Move(Object child, Object parent) { }
    private static final class Tx {
        final List<Move> moves = new ArrayList<>();
        Runnable onCommit;
    }
    private static final class Scene implements StatusBarTransition.Surfaces {
        final Object bar = new Object(), parent = new Object(), target = new Object(), owner = new Object(), info = new Object();
        final Tx start = new Tx(), finish = new Tx(), cleanup = new Tx();
        final List<Move> immediate = new ArrayList<>();
        final StatusBarTransition controller = new StatusBarTransition(this);
        Object invalid;
        boolean failAttach, failRestore;
        public boolean valid(Object surface) { return surface != null && surface != invalid; }
        public void reparent(Object tx, Object child, Object parent) {
            if (failAttach && parent == target) throw new IllegalStateException("attach failed");
            ((Tx) tx).moves.add(new Move(child, parent));
        }
        public void restore(Object child, Object parent) {
            if (failRestore) throw new IllegalStateException("restore failed");
            immediate.add(new Move(child, parent));
        }
        public void committed(Object tx, Runnable callback) { ((Tx) tx).onCommit = callback; }
        boolean prepare() { return controller.prepare(owner, info, bar, parent, target, start, finish); }
    }
    @Test public void preparesNativeRestoreBeforeAttachingAndAddsFailureCleanup() {
        Scene s = new Scene();
        assertTrue(s.prepare());
        assertEquals(List.of(new Move(s.bar, s.target)), s.start.moves);
        assertEquals(List.of(new Move(s.bar, s.parent)), s.finish.moves);
        assertTrue(s.immediate.isEmpty()); // The caller's native start transaction owns the commit.
        s.controller.cleanup(s.info, s.cleanup);
        assertEquals(List.of(new Move(s.bar, s.parent)), s.cleanup.moves);
        assertTrue(s.controller.owns(s.bar));
    }
    @Test public void onlyOwningTransitionMayFinish() {
        Scene s = new Scene(); assertTrue(s.prepare());
        s.controller.finish(new Object());
        assertTrue(s.controller.busy()); assertTrue(s.immediate.isEmpty());
        s.controller.finish(s.owner);
        assertFalse(s.controller.busy());
        assertEquals(List.of(new Move(s.bar, s.parent)), s.immediate);
    }
    @Test public void cancelRestoresButRetainsBarrierUntilNativeFinish() {
        Scene s = new Scene(); assertTrue(s.prepare());
        s.controller.cancel();
        assertFalse(s.controller.owns(s.bar)); assertTrue(s.controller.busy());
        assertEquals(new Move(s.bar, s.parent), s.immediate.get(0));
        // A late start commit after disabling must restore again.
        s.start.onCommit.run();
        assertEquals(2, s.immediate.size());
        assertFalse(s.controller.prepare(new Object(), new Object(), s.bar, s.parent, s.target, new Tx(), new Tx()));
        s.controller.finish(s.owner);
        assertTrue(s.controller.prepare(new Object(), new Object(), s.bar, s.parent, s.target, new Tx(), new Tx()));
    }
    @Test public void overlappingTransitionRestoresOldOwnerAndDoesNotAttachNewOne() {
        Scene s = new Scene(); assertTrue(s.prepare()); Tx nextStart = new Tx();
        assertFalse(s.controller.prepare(new Object(), new Object(), s.bar, s.parent, s.target, nextStart, new Tx()));
        assertTrue(nextStart.moves.isEmpty()); assertFalse(s.controller.owns(s.bar));
        assertEquals(new Move(s.bar, s.parent), s.immediate.get(0));
    }
    @Test public void staleCommitCannotDetachNewAnimationOnSameStatusBar() {
        Scene s = new Scene(); assertTrue(s.prepare());
        s.controller.finish(s.owner); s.immediate.clear();
        assertTrue(s.controller.prepare(new Object(), new Object(), s.bar, s.parent, s.target, new Tx(), new Tx()));
        s.start.onCommit.run();
        assertTrue(s.immediate.isEmpty()); assertTrue(s.controller.owns(s.bar));
    }
    @Test public void invalidParentOrTargetNeverEnqueuesAttach() {
        for (int i = 0; i < 3; i++) {
            Scene s = new Scene(); s.invalid = i == 0 ? s.parent : i == 1 ? s.target : s.bar;
            assertFalse(s.prepare()); assertTrue(s.start.moves.isEmpty()); assertFalse(s.controller.busy());
        }
    }
    @Test public void partialFailureRollsBackStartAndPreservesRecoveryBarrier() {
        Scene s = new Scene(); s.failAttach = true;
        assertThrows(IllegalStateException.class, s::prepare);
        assertEquals(new Move(s.bar, s.parent), s.start.moves.get(s.start.moves.size() - 1));
        assertTrue(s.controller.busy()); assertFalse(s.controller.owns(s.bar));
        s.start.onCommit.run();
        assertEquals(new Move(s.bar, s.parent), s.immediate.get(s.immediate.size() - 1));
    }
    @Test public void restoreFailureRetainsStateForRetry() {
        Scene s = new Scene(); assertTrue(s.prepare()); s.failRestore = true;
        assertThrows(IllegalStateException.class, () -> s.controller.finish(s.owner));
        assertTrue(s.controller.busy());
        s.failRestore = false; s.controller.finish(s.owner);
        assertFalse(s.controller.busy());
    }
    @Test public void unrelatedCleanupDoesNotWriteAnotherOwnersTransaction() {
        Scene s = new Scene(); assertTrue(s.prepare());
        s.controller.cleanup(new Object(), s.cleanup);
        assertTrue(s.cleanup.moves.isEmpty());
    }
}
