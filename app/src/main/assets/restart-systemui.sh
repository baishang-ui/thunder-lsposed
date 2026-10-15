#!/system/bin/sh
# Explicit user action only. Never called by the automatic settings writer.
fail() { echo "THUNDER_RESTART_ERROR=$1"; exit 1; }
[ "$(id -u)" = 0 ] || fail ROOT_DENIED
# A delayed Root authorization must not trigger an already expired request.
[ "$(date +%s)" -le "${THUNDER_RESTART_DEADLINE:-0}" ] || fail EXPIRED
thunder_old=$(pidof com.android.systemui)
[ -n "$thunder_old" ] || fail NOT_RUNNING
for thunder_pid in $thunder_old; do
    case "$thunder_pid" in 0|1|''|*[!0-9]*) fail BAD_PID;; esac
done
echo "THUNDER_RESTART_OLD=$thunder_old"
kill -TERM $thunder_old || fail TERMINATE_FAILED
# Some ART builds consume TERM. Only the original, still-running PID may receive KILL.
thunder_attempt=0
while [ "$thunder_attempt" -lt 3 ]; do
    thunder_remaining=""
    for thunder_pid in $(pidof com.android.systemui); do
        for thunder_previous in $thunder_old; do
            [ "$thunder_pid" != "$thunder_previous" ] || thunder_remaining="$thunder_remaining $thunder_pid"
        done
    done
    [ -n "$thunder_remaining" ] || break
    sleep 1
    thunder_attempt=$((thunder_attempt + 1))
done
# Re-read the exact target name immediately before the one permitted fallback.
thunder_remaining=""
for thunder_pid in $(pidof com.android.systemui); do
    for thunder_previous in $thunder_old; do
        [ "$thunder_pid" != "$thunder_previous" ] || thunder_remaining="$thunder_remaining $thunder_pid"
    done
done
if [ -n "$thunder_remaining" ]; then
    echo THUNDER_RESTART_FALLBACK
    kill -KILL $thunder_remaining || fail TERMINATE_FAILED
fi
thunder_attempt=0
while [ "$thunder_attempt" -lt 20 ]; do
    thunder_new=$(pidof com.android.systemui)
    thunder_stale=0
    for thunder_pid in $thunder_new; do
        case "$thunder_pid" in 0|1|''|*[!0-9]*) thunder_stale=1;; esac
        for thunder_previous in $thunder_old; do
            [ "$thunder_pid" != "$thunder_previous" ] || thunder_stale=1
        done
    done
    if [ -n "$thunder_new" ] && [ "$thunder_stale" = 0 ]; then
        echo "THUNDER_RESTART_NEW=$thunder_new"
        exit 0
    fi
    sleep 1
    thunder_attempt=$((thunder_attempt + 1))
done
fail NO_NEW_PROCESS
