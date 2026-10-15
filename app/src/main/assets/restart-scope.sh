#!/system/bin/sh
# Fixed module scopes only. Every live process needs its own report and stability window.
thunder_target=HOST
fail() { echo "THUNDER_RESTART_TARGET=$thunder_target"; echo "THUNDER_RESTART_ERROR=$1"; exit 1; }
check_deadline() {
    [ "$(date +%s)" -le "${THUNDER_RESTART_DEADLINE:-0}" ] || fail EXPIRED
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
    thunder_stat=$(cat /proc/$thunder_pid/stat) || fail PROCESS_CHANGED
    set -- ${thunder_stat##*) }
    [ "$#" -ge 20 ] || fail UNCONFIRMED_PROCESS
    shift 19
    case "$1" in ''|*[!0-9]*) fail UNCONFIRMED_PROCESS;; esac
    [ "$(( $1 * 1000 / thunder_ticks ))" -le "$thunder_report" ] || fail PROCESS_CHANGED
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
check_service() {
    thunder_services=$(cmd package query-services --components --user 0 -n "$1" 2>&1) || fail SERVICE_UNAVAILABLE
    case "$thunder_services" in *Error*|*Exception*) fail SERVICE_UNAVAILABLE;; esac
    case "$thunder_services" in *"$1"*|*"$2"*) ;; *) fail SERVICE_UNAVAILABLE;; esac
}
restart_package() {
    echo "THUNDER_RESTART_${thunder_target}_OLD=$3"
    am force-stop --user 0 "$1" || fail STOP_FAILED
    thunder_start=$(am startservice --user 0 -n "$2" 2>&1)
    thunder_start_exit=$?
    if [ "$thunder_start_exit" != 0 ]; then echo "$thunder_start"; fail START_FAILED; fi
    case "$thunder_start" in *Error*|*Exception*|*"not found"*) echo "$thunder_start"; fail START_FAILED;; esac
    thunder_attempt=0
    while [ "$thunder_attempt" -lt 20 ]; do
        thunder_new=$(pidof "$1")
        case "$thunder_new" in 0|1|''|*[!0-9]*) ;; *)
            if [ "$thunder_new" != "$3" ]; then
                echo "THUNDER_RESTART_${thunder_target}_NEW=$thunder_new"
                return 0
            fi;;
        esac
        sleep 1
        thunder_attempt=$((thunder_attempt + 1))
    done
    fail NO_NEW_PROCESS
}
[ "$(id -u)" = 0 ] || fail ROOT_DENIED
check_deadline
thunder_host_old=${THUNDER_RESTART_EXPECTED_PID:-}
check_process com.android.systemui "$thunder_host_old" "${THUNDER_RESTART_CONFIRMED_ELAPSED:-}"
thunder_plugin_old=${THUNDER_RESTART_PLUGIN_PID:-0}
thunder_target=PLUGIN
if [ "$thunder_plugin_old" = 0 ] && [ -n "$(pidof com.oplus.systemui.plugins)" ]; then
    fail PLUGIN_UNCONFIRMED
fi
check_plugin
thunder_host_service=com.android.systemui/com.android.systemui.SystemUIService
thunder_plugin_service=com.oplus.systemui.plugins/com.oplus.systemui.plugins.seedling.plugin.SeedlingPluginService
# Resolve every required component before the first mutation.
thunder_target=HOST
check_service "$thunder_host_service" com.android.systemui/.SystemUIService
if [ "$thunder_plugin_old" != 0 ]; then
    thunder_target=PLUGIN
    check_service "$thunder_plugin_service" com.oplus.systemui.plugins/.seedling.plugin.SeedlingPluginService
fi
check_deadline
thunder_target=HOST
check_process com.android.systemui "$thunder_host_old" "${THUNDER_RESTART_CONFIRMED_ELAPSED:-}"
check_plugin
if [ "$thunder_plugin_old" != 0 ]; then
    # Restore the independent plugin first; a failed plugin restart must not stop the host.
    restart_package com.oplus.systemui.plugins "$thunder_plugin_service" "$thunder_plugin_old"
else
    echo THUNDER_RESTART_PLUGIN_MODE=host
fi
thunder_target=HOST
check_deadline
# Plugin restoration can cause Android to replace SystemUI. Never stop that replacement.
check_process com.android.systemui "$thunder_host_old" "${THUNDER_RESTART_CONFIRMED_ELAPSED:-}"
restart_package com.android.systemui "$thunder_host_service" "$thunder_host_old"
