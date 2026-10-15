package io.github.baishangui.thunder;

import java.util.List;

/** Transaction ownership, independent of hidden Android window-manager classes. */
final class StatusBarTransition {
    record Candidate(int activityType, int windowingMode, int mode, int displayId,
                     boolean fullDisplay, boolean rotating) { }
    interface Surfaces {
        boolean valid(Object surface);
        void reparent(Object transaction, Object child, Object parent);
        void restore(Object child, Object parent);
        void committed(Object transaction, Runnable callback);
    }
    private final Surfaces surfaces;
    private Session active;
    private static final class Session {
        final Object owner, info, bar, parent;
        boolean follow = true;
        Session(Object owner, Object info, Object bar, Object parent) {
            this.owner = owner; this.info = info; this.bar = bar; this.parent = parent;
        }
    }
    StatusBarTransition(Surfaces surfaces) { this.surfaces = surfaces; }
    static int select(int type, List<Candidate> candidates) {
        // ColorOS starts its gesture home/recents transition as FIRST_CUSTOM+21.
        boolean recents = type == 1021;
        if (!recents && (type < 1 || type > 4)) return -1;
        int selected = -1;
        boolean home = false;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            if (c.displayId != 0 || c.windowingMode != 1 || c.rotating || !c.fullDisplay) return -1;
            if (c.activityType == 2 && (!recents || c.mode == 1 || c.mode == 3)) home = true;
            else if (c.activityType == 1 && c.mode >= 1 && c.mode <= 4 && selected < 0 &&
                    (!recents || c.mode == 2 || c.mode == 4)) selected = i;
            else return -1;
        }
        return home ? selected : -1;
    }
    boolean prepare(Object owner, Object info, Object bar, Object parent, Object target,
                    Object start, Object finish) {
        if (active != null) { cancel(); return false; }
        if (owner == null || info == null || start == null || finish == null ||
                !surfaces.valid(bar) || !surfaces.valid(parent) || !surfaces.valid(target)) return false;
        Session session = new Session(owner, info, bar, parent);
        active = session;
        try {
            // Never enqueue an attachment until native completion can undo it.
            surfaces.reparent(finish, bar, parent);
            surfaces.committed(start, () -> {
                // A disabled/interrupted start may arrive after the immediate restore.
                // An old commit must not detach a newer owner of the same bar.
                if ((active == null || active == session || active.bar != bar) &&
                        (active != session || !session.follow)) restore(session);
            });
            surfaces.reparent(start, bar, target);
            return true;
        } catch (RuntimeException error) {
            session.follow = false;
            try { surfaces.reparent(start, bar, parent); } catch (RuntimeException rollback) { error.addSuppressed(rollback); }
            try { restore(session); } catch (RuntimeException rollback) { error.addSuppressed(rollback); }
            throw error;
        }
    }
    void cleanup(Object info, Object transaction) {
        if (active != null && active.info == info && surfaces.valid(active.bar) && surfaces.valid(active.parent))
            surfaces.reparent(transaction, active.bar, active.parent);
    }
    void cancel() {
        if (active == null) return;
        active.follow = false;
        restore(active);
        // Retain the barrier until its native finish/cleanup has been consumed.
    }
    void finish(Object owner) {
        if (active == null || active.owner != owner) return;
        active.follow = false;
        restore(active);
        active = null;
    }
    private void restore(Session session) {
        if (surfaces.valid(session.bar) && surfaces.valid(session.parent)) surfaces.restore(session.bar, session.parent);
    }
    boolean owns(Object bar) { return active != null && active.follow && active.bar == bar; }
    boolean busy() { return active != null; }
}
