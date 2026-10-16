#!/system/bin/sh
# Restart only SystemUI, immediately after Root authorization. No Hook report prerequisite.
fail() { echo "THUNDER_RESTART_ERROR=$1"; exit 1; }
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
    while [ "$thunder_exit_attempt" -lt 25 ]; do
        if old_exited "$thunder_host_old" "$thunder_host_start"; then
            echo THUNDER_RESTART_HOST_EXITED=1
            return 0
        fi
        sleep 0.2
        thunder_exit_attempt=$((thunder_exit_attempt + 1))
    done
    fail OLD_PROCESS_ALIVE
}
start_service() {
    thunder_start=$(timeout 5 am startservice --user 0 -n com.android.systemui/com.android.systemui.SystemUIService 2>&1)
    thunder_start_exit=$?
    thunder_start_failed=0
    if [ "$thunder_start_exit" != 0 ]; then thunder_start_failed=1; fi
    case "$thunder_start" in *Error*|*Exception*|*"not found"*) thunder_start_failed=1;; esac
    if [ "$thunder_start_failed" = 1 ]; then echo "$thunder_start"; fi
}
wait_replacement() {
    thunder_attempt=0
    thunder_start_failed=0
    while [ "$thunder_attempt" -lt 100 ]; do
        thunder_new=$(pidof com.android.systemui)
        case "$thunder_new" in 0|1|''|*[!0-9]*) ;; *)
            if [ "$thunder_new" != "$thunder_host_old" ] && old_exited "$thunder_host_old" "$thunder_host_start"; then
                echo "THUNDER_RESTART_HOST_NEW=$thunder_new"
                return 0
            fi;;
        esac
        # Give Android three seconds to restore its persistent process; recovery never signals it again.
        if [ "$thunder_attempt" = 15 ] || [ "$thunder_attempt" = 40 ]; then start_service; fi
        sleep 0.2
        thunder_attempt=$((thunder_attempt + 1))
    done
    if [ "$thunder_start_failed" = 1 ]; then fail START_FAILED; fi
    fail NO_NEW_PROCESS
}
[ "$(id -u)" = 0 ] || fail ROOT_DENIED
check_deadline
# Resolve the live PID after authorization, without waiting for module injection or configuration reports.
thunder_host_old=$(pidof com.android.systemui)
[ -n "$thunder_host_old" ] || fail NOT_RUNNING
case "$thunder_host_old" in 0|1|*[!0-9]*) fail BAD_PID;; esac
read_identity "$thunder_host_old" || fail UNCONFIRMED_PROCESS
thunder_host_start=$thunder_start_ticks
thunder_name=$(tr '\000' '\n' < /proc/$thunder_host_old/cmdline) || fail UNCONFIRMED_PROCESS
[ "$thunder_name" = com.android.systemui ] || fail UNCONFIRMED_PROCESS
check_deadline
[ "$(pidof com.android.systemui)" = "$thunder_host_old" ] || fail PROCESS_CHANGED
read_identity "$thunder_host_old" || fail PROCESS_CHANGED
[ "$thunder_start_ticks" = "$thunder_host_start" ] || fail PROCESS_CHANGED
echo "THUNDER_RESTART_HOST_OLD=$thunder_host_old"
/system/bin/kill -9 "$thunder_host_old"
thunder_signal_exit=$?
if [ "$thunder_signal_exit" != 0 ] && ! old_exited "$thunder_host_old" "$thunder_host_start"; then fail STOP_FAILED; fi
wait_exit
wait_replacement
