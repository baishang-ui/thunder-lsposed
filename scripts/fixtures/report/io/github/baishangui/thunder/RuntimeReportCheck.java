package io.github.baishangui.thunder;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import java.lang.reflect.Field;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public final class RuntimeReportCheck {
    static class Receiver extends ContentResolver {
        int calls;
        boolean unavailable, noAck;
        Bundle last;
        Runnable duringSend;
        @Override public Bundle call(Uri uri, String method, String arg, Bundle data) {
            calls++; last = data;
            if (duringSend != null) { Runnable event = duringSend; duringSend = null; event.run(); }
            if (unavailable) throw new IllegalArgumentException("Unknown authority io.github.baishangui.thunder.settings");
            return noAck ? null : Bundle.EMPTY;
        }
    }
    static void set(String name, Object value) throws Exception {
        Field field = ShadeHook.class.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("PASS: " + message);
    }
    public static void main(String[] args) throws Exception {
        // Install real lifecycle logic, then attach a synthetic target Context.
        ShadeHook.install("com.android.systemui", "com.android.systemui", RuntimeReportCheck.class.getClassLoader());
        Field workerField = ShadeHook.class.getDeclaredField("worker"); workerField.setAccessible(true);
        Handler worker = (Handler) workerField.get(null);
        worker.advance(500);
        Receiver receiver = new Receiver();
        set("context", new Context() { @Override public ContentResolver getContentResolver() { return receiver; } });
        for (int i = 0; i < 140; i++) ShadeHook.hookRegistered("boot-hook-" + i);
        check(worker.pending() == 1, "140 startup events coalesce into one pending report");
        worker.advance(250);
        ShadeHook.hookRegistered("newest-hook");
        worker.advance(250);
        check(receiver.calls == 1 && receiver.last.getString("hooks", "").contains("newest-hook"),
            "updates retain the first deadline and send the newest snapshot");
        check(worker.pending() == 0, "acknowledged reports stop scheduling work");

        receiver.unavailable = true;
        ShadeHook.hookRegistered("outage-start"); worker.advance(500);
        check(receiver.calls == 2 && worker.pending() == 1 && worker.nextDelay() >= 2500,
            "unknown provider retains one report and schedules a delayed retry");
        for (int i = 0; i < 100; i++) ShadeHook.hookRegistered("outage-hook-" + i);
        check(worker.pending() == 1 && worker.nextDelay() >= 2500,
            "hook events cannot bypass the provider failure backoff");
        ByteArrayOutputStream repeatedErrors = new ByteArrayOutputStream();
        PrintStream output = System.out;
        try {
            System.setOut(new PrintStream(repeatedErrors));
            for (int i = 0; i < 8; i++) worker.advance(worker.nextDelay());
        } finally { System.setOut(output); }
        check(!repeatedErrors.toString().contains("status unavailable"),
            "repeated failures in the same outage do not flood LSPosed logs");
        check(worker.pending() == 1 && worker.nextDelay() == 60000,
            "a long outage backs off to at most one attempt per minute");
        receiver.unavailable = false;
        Bundle updated = new Bundle(); updated.putLong("revision", 42);
        set("config", new HookConfig(updated));
        worker.advance(worker.nextDelay());
        check(receiver.last.getLong("revision", -1) == 42 && worker.pending() == 0,
            "provider recovery delivers the latest configuration without a new hook event");

        receiver.noAck = true;
        ShadeHook.hookRegistered("null-response"); worker.advance(500);
        check(worker.pending() == 1 && worker.nextDelay() == 2500,
            "null acknowledgement is retried and a recovered connection resets backoff");
        receiver.noAck = false; worker.advance(2500);
        check(worker.pending() == 0, "a later acknowledgement clears the retry");

        receiver.duringSend = () -> ShadeHook.hookRegistered("arrived-during-send");
        ShadeHook.hookRegistered("before-send"); worker.advance(500);
        check(worker.pending() == 1, "an event arriving during IPC is retained");
        worker.advance(500);
        check(receiver.last.getString("hooks", "").contains("arrived-during-send") && worker.pending() == 0,
            "the subsequent report includes the event that arrived during IPC");
    }
}
