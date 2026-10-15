#!/system/bin/sh
# Runs only through su after the user taps Restart. No module files or boot scripts.
if [ "$(id -u)" != 0 ]; then
    echo THUNDER_RESTART_ERROR=ROOT_DENIED
    exit 10
fi
thunder_old=$(pidof com.android.systemui)
if [ -z "$thunder_old" ]; then
    echo THUNDER_RESTART_ERROR=NOT_RUNNING
    exit 11
fi
for thunder_pid in $thunder_old; do
    case "$thunder_pid" in 0|1|''|*[!0-9]*) echo THUNDER_RESTART_ERROR=BAD_PID; exit 12;; esac
done
echo "THUNDER_RESTART_OLD=$thunder_old"
# ART can intercept/ignore TERM. KILL forces the framework to start a fresh SystemUI.
if ! kill -KILL $thunder_old; then
    echo THUNDER_RESTART_ERROR=KILL_FAILED
    exit 13
fi
thunder_attempt=0
while [ "$thunder_attempt" -lt 15 ]; do
    thunder_new=$(pidof com.android.systemui)
    thunder_stale=0
    for thunder_pid in $thunder_new; do
        case "$thunder_pid" in ''|*[!0-9]*) thunder_stale=1;; esac
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
echo THUNDER_RESTART_ERROR=NO_NEW_PROCESS
exit 14
