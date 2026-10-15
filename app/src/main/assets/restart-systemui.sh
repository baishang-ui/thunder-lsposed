#!/system/bin/sh
# Explicit user request only, after a fresh Hook report and its 65-second stability window.
fail() { echo "THUNDER_RESTART_ERROR=$1"; exit 1; }
[ "$(id -u)" = 0 ] || fail ROOT_DENIED
[ "$(date +%s)" -le "${THUNDER_RESTART_DEADLINE:-0}" ] || fail EXPIRED
thunder_old=$(pidof com.android.systemui)
[ -n "$thunder_old" ] || fail NOT_RUNNING
case "$thunder_old" in 0|1|''|*[!0-9]*) fail BAD_PID;; esac
[ "$thunder_old" = "${THUNDER_RESTART_EXPECTED_PID:-}" ] || fail PROCESS_CHANGED
# Verify the report belongs to this process incarnation, including PID reuse.
thunder_report=${THUNDER_RESTART_CONFIRMED_ELAPSED:-}
case "$thunder_report" in ''|*[!0-9]*) fail UNCONFIRMED_PROCESS;; esac
thunder_ticks=$(getconf CLK_TCK) || fail UNCONFIRMED_PROCESS
case "$thunder_ticks" in 0|''|*[!0-9]*) fail UNCONFIRMED_PROCESS;; esac
thunder_stat=$(cat /proc/$thunder_old/stat) || fail PROCESS_CHANGED
set -- ${thunder_stat##*) }
[ "$#" -ge 20 ] || fail UNCONFIRMED_PROCESS
shift 19
case "$1" in ''|*[!0-9]*) fail UNCONFIRMED_PROCESS;; esac
[ "$(( $1 * 1000 / thunder_ticks ))" -le "$thunder_report" ] || fail PROCESS_CHANGED
read -r thunder_uptime thunder_unused < /proc/uptime || fail UNCONFIRMED_PROCESS
thunder_uptime=${thunder_uptime%%.*}
case "$thunder_uptime" in ''|*[!0-9]*) fail UNCONFIRMED_PROCESS;; esac
[ "$((thunder_uptime * 1000 - thunder_report))" -ge 60000 ] || fail NOT_STABLE
# Resolve the exact service before stopping anything. No signal or crash fallback is allowed.
thunder_component=com.android.systemui/com.android.systemui.SystemUIService
thunder_services=$(cmd package query-services --components --user 0 -n "$thunder_component" 2>&1) || fail SERVICE_UNAVAILABLE
case "$thunder_services" in *"$thunder_component"*|*com.android.systemui/.SystemUIService*) ;; *) fail SERVICE_UNAVAILABLE;; esac
# Root approval or service lookup must not let a delayed request act on a replacement process.
[ "$(date +%s)" -le "${THUNDER_RESTART_DEADLINE:-0}" ] || fail EXPIRED
[ "$(pidof com.android.systemui)" = "$thunder_old" ] || fail PROCESS_CHANGED
echo "THUNDER_RESTART_OLD=$thunder_old"
am force-stop --user 0 com.android.systemui || fail STOP_FAILED
# Persistent SystemUI may already have been restored by Android. Start its service either way.
thunder_start=$(am startservice --user 0 -n "$thunder_component" 2>&1)
thunder_start_exit=$?
if [ "$thunder_start_exit" != 0 ]; then
    echo "$thunder_start"
    fail START_FAILED
fi
case "$thunder_start" in *Error*|*Exception*|*"not found"*) echo "$thunder_start"; fail START_FAILED;; esac
thunder_attempt=0
while [ "$thunder_attempt" -lt 20 ]; do
    thunder_new=$(pidof com.android.systemui)
    case "$thunder_new" in 0|1|''|*[!0-9]*) ;; *)
        if [ "$thunder_new" != "$thunder_old" ]; then
            echo "THUNDER_RESTART_NEW=$thunder_new"
            exit 0
        fi;;
    esac
    sleep 1
    thunder_attempt=$((thunder_attempt + 1))
done
fail NO_NEW_PROCESS
