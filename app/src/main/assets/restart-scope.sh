#!/system/bin/sh
# Recreate processes: force-stop the independent plugin, SIGKILL the exact SystemUI PID.
# See docs/lsposed-safe-mode.md for LuckyTool references and the LSPosed stability requirement.
thunder_target=HOST
fail() { echo "THUNDER_RESTART_TARGET=$thunder_target"; echo "THUNDER_RESTART_ERROR=$1"; exit 1; }
check_deadline() {
    [ "$(date +%s)" -le "${THUNDER_RESTART_DEADLINE:-0}" ] || fail EXPIRED
}
read_identity() {
    thunder_stat=$(cat /proc/$1/stat 2>/dev/null) || return 1
    set -- ${thunder_stat##*) }
    [ "$#" -ge 20 ] || return 1
    thunder_state=$1
    shift 19
    case "$1" in ''|*[!0-9]*) return 1;; esac
    thunder_start_ticks=$1
}
old_exited() {
    [ -e /proc/$1/stat ] || return 0
    read_identity "$1" || return 1
    [ "$thunder_start_ticks" != "$2" ] || [ "$thunder_state" = Z ] || [ "$thunder_state" = X ]
}
wait_exit() {
    thunder_exit_attempt=0
    while [ "$thunder_exit_attempt" -lt 5 ]; do
        if old_exited "$1" "$2"; then
            echo "THUNDER_RESTART_${thunder_target}_EXITED=1"
            return 0
        fi
        sleep 1
        thunder_exit_attempt=$((thunder_exit_attempt + 1))
    done
    fail OLD_PROCESS_ALIVE
}
check_process() {
    thunder_pid=$(pidof "$1")
    [ -n "$thunder_pid" ] || fail NOT_RUNNING
    case "$thunder_pid" in 0|1|''|*[!0-9]*) fail BAD_PID;; esac
    [ "$thunder_pid" = "$2" ] || fail PROCESS_CHANGED
    thunder_report=$3
    case "$thunder_report" in ''|*[!0-9]*) fail UNCONFIRMED_PROCESS;; esac
    thunder_ticks=$(getconf CLK_TCK) || fail UNCONFIRMED_PROCESS
    case "$thunder_ticks" in 0|''|*[!0-9]*) fail UNCONFIRMED_PROCESS;; esac
    read_identity "$thunder_pid" || fail UNCONFIRMED_PROCESS
    [ "$(( thunder_start_ticks * 1000 / thunder_ticks ))" -le "$thunder_report" ] || fail PROCESS_CHANGED
    read -r thunder_uptime thunder_unused < /proc/uptime || fail UNCONFIRMED_PROCESS
    thunder_uptime=${thunder_uptime%%.*}
    case "$thunder_uptime" in ''|*[!0-9]*) fail UNCONFIRMED_PROCESS;; esac
    [ "$((thunder_uptime * 1000 - thunder_report))" -ge 60000 ] || fail NOT_STABLE
}
check_plugin() {
    thunder_target=PLUGIN
    if [ "$thunder_plugin_old" = 0 ]; then
        [ -z "$(pidof com.oplus.systemui.plugins)" ] || fail PROCESS_CHANGED
    else
        check_process com.oplus.systemui.plugins "$thunder_plugin_old" "${THUNDER_RESTART_PLUGIN_ELAPSED:-}"
    fi
}
check_plugin_service() {
    thunder_services=$(cmd package query-services --components --user 0 -n "$thunder_plugin_service" 2>&1) || fail SERVICE_UNAVAILABLE
    case "$thunder_services" in *Error*|*Exception*) fail SERVICE_UNAVAILABLE;; esac
    case "$thunder_services" in *"$thunder_plugin_service"*|*com.oplus.systemui.plugins/.seedling.plugin.SeedlingPluginService*) ;; *) fail SERVICE_UNAVAILABLE;; esac
}
start_service() {
    # A service call may hang; leave time to verify the PID and try a second launch.
    thunder_start=$(timeout 5 am startservice --user 0 -n "$1" 2>&1)
    thunder_start_exit=$?
    thunder_start_failed=0
    if [ "$thunder_start_exit" != 0 ]; then thunder_start_failed=1; fi
    case "$thunder_start" in *Error*|*Exception*|*"not found"*) thunder_start_failed=1;; esac
    if [ "$thunder_start_failed" = 1 ]; then echo "$thunder_start"; fi
}
wait_replacement() {
    # $4 delays explicit startup so Android can restore persistent SystemUI by itself.
    thunder_attempt=0
    thunder_start_failed=0
    while [ "$thunder_attempt" -lt 20 ]; do
        thunder_new=$(pidof "$1")
        case "$thunder_new" in 0|1|''|*[!0-9]*) ;; *)
            if [ "$thunder_new" != "$2" ] && old_exited "$2" "$3"; then
                echo "THUNDER_RESTART_${thunder_target}_NEW=$thunder_new"
                return 0
            fi;;
        esac
        if [ "$thunder_attempt" = "$4" ] || [ "$thunder_attempt" = "$(( $4 + 5 ))" ]; then
            start_service "$5"
        fi
        sleep 1
        thunder_attempt=$((thunder_attempt + 1))
    done
    if [ "$thunder_start_failed" = 1 ]; then fail START_FAILED; fi
    fail NO_NEW_PROCESS
}
[ "$(id -u)" = 0 ] || fail ROOT_DENIED
check_deadline
thunder_host_old=${THUNDER_RESTART_EXPECTED_PID:-}
check_process com.android.systemui "$thunder_host_old" "${THUNDER_RESTART_CONFIRMED_ELAPSED:-}"
thunder_host_start=$thunder_start_ticks
thunder_plugin_old=${THUNDER_RESTART_PLUGIN_PID:-0}
thunder_target=PLUGIN
if [ "$thunder_plugin_old" = 0 ] && [ -n "$(pidof com.oplus.systemui.plugins)" ]; then fail PLUGIN_UNCONFIRMED; fi
check_plugin
thunder_plugin_start=$thunder_start_ticks
thunder_host_service=com.android.systemui/com.android.systemui.SystemUIService
thunder_plugin_service=com.oplus.systemui.plugins/com.oplus.systemui.plugins.seedling.plugin.SeedlingPluginService
if [ "$thunder_plugin_old" != 0 ]; then check_plugin_service; fi
# Revalidate both identities after Root approval and component lookup, before the first exit.
check_deadline
thunder_target=HOST
check_process com.android.systemui "$thunder_host_old" "${THUNDER_RESTART_CONFIRMED_ELAPSED:-}"
check_plugin
if [ "$thunder_plugin_old" != 0 ]; then
    echo "THUNDER_RESTART_PLUGIN_OLD=$thunder_plugin_old"
    am force-stop --user 0 com.oplus.systemui.plugins || fail STOP_FAILED
    wait_exit "$thunder_plugin_old" "$thunder_plugin_start"
    wait_replacement com.oplus.systemui.plugins "$thunder_plugin_old" "$thunder_plugin_start" 0 "$thunder_plugin_service"
else
    echo THUNDER_RESTART_PLUGIN_MODE=host
fi
thunder_target=HOST
# Plugin restoration may already have caused Android to rebuild SystemUI. Adopt that
# replacement only if the original really exited; never signal the newly started process.
if [ "$thunder_plugin_old" != 0 ] && old_exited "$thunder_host_old" "$thunder_host_start"; then
    echo "THUNDER_RESTART_HOST_OLD=$thunder_host_old"
    echo THUNDER_RESTART_HOST_EXITED=1
    wait_replacement com.android.systemui "$thunder_host_old" "$thunder_host_start" 3 "$thunder_host_service"
    exit 0
fi
check_process com.android.systemui "$thunder_host_old" "${THUNDER_RESTART_CONFIRMED_ELAPSED:-}"
echo "THUNDER_RESTART_HOST_OLD=$thunder_host_old"
# Like LuckyTool's SystemUI branch, but never match arbitrary names using a pattern.
/system/bin/kill -9 "$thunder_host_old"
thunder_signal_exit=$?
if [ "$thunder_signal_exit" != 0 ] && ! old_exited "$thunder_host_old" "$thunder_host_start"; then fail STOP_FAILED; fi
wait_exit "$thunder_host_old" "$thunder_host_start"
# The persistent process normally returns without any service call. Recovery launches
# may retry twice; termination is never retried against the old or replacement PID.
wait_replacement com.android.systemui "$thunder_host_old" "$thunder_host_start" 3 "$thunder_host_service"
