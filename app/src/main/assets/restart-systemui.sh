#!/system/bin/sh
# Runs only through su after the user taps Restart. No module files or boot scripts.
if [ "$(id -u)" != 0 ]; then
    echo PUI_RESTART_ERROR=ROOT_DENIED
    exit 10
fi
pui_old=$(pidof com.android.systemui)
if [ -z "$pui_old" ]; then
    echo PUI_RESTART_ERROR=NOT_RUNNING
    exit 11
fi
for pui_pid in $pui_old; do
    case "$pui_pid" in 0|1|''|*[!0-9]*) echo PUI_RESTART_ERROR=BAD_PID; exit 12;; esac
done
echo "PUI_RESTART_OLD=$pui_old"
# ART can intercept/ignore TERM. KILL forces the framework to start a fresh SystemUI.
if ! kill -KILL $pui_old; then
    echo PUI_RESTART_ERROR=KILL_FAILED
    exit 13
fi
pui_attempt=0
while [ "$pui_attempt" -lt 15 ]; do
    pui_new=$(pidof com.android.systemui)
    pui_stale=0
    for pui_pid in $pui_new; do
        case "$pui_pid" in ''|*[!0-9]*) pui_stale=1;; esac
        for pui_previous in $pui_old; do
            [ "$pui_pid" != "$pui_previous" ] || pui_stale=1
        done
    done
    if [ -n "$pui_new" ] && [ "$pui_stale" = 0 ]; then
        echo "PUI_RESTART_NEW=$pui_new"
        exit 0
    fi
    sleep 1
    pui_attempt=$((pui_attempt + 1))
done
echo PUI_RESTART_ERROR=NO_NEW_PROCESS
exit 14
