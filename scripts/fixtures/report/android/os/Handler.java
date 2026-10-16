package android.os;

import java.util.ArrayList;
import java.util.List;

/** Virtual worker clock: retries are exercised without sleeping. */
public class Handler {
    private record Task(Runnable action, long time) {}
    private final List<Task> tasks = new ArrayList<>();
    public static int posts;
    private long now;
    public Handler(Object looper) {}
    public void post(Runnable action) { posts++; postDelayed(action, 0); }
    public void postDelayed(Runnable action, long delay) { tasks.add(new Task(action, now + delay)); }
    public void removeCallbacks(Runnable action) { tasks.removeIf(task -> task.action == action); }
    public int pending() { return tasks.size(); }
    public long nextDelay() { return tasks.stream().mapToLong(task -> task.time - now).min().orElse(-1); }
    public void advance(long delta) {
        long end = now + delta;
        while (true) {
            Task next = tasks.stream().filter(task -> task.time <= end)
                .min(java.util.Comparator.comparingLong(Task::time)).orElse(null);
            if (next == null) break;
            tasks.remove(next); now = next.time; next.action.run();
        }
        now = end;
    }
}
