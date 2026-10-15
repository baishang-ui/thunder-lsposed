#!/system/bin/sh
# Read-only system_server and launcher overlay diagnostics (r4).
# Run on the phone: su -c 'sh /sdcard/Download/collect_statusbar_bootstrap.sh'

if [ "$#" != 0 ]; then
    echo "用法：su -c 'sh /sdcard/Download/collect_statusbar_bootstrap.sh'"
    exit 2
fi
if [ "$(id -u)" != 0 ]; then
    echo "需要 Root：su -c 'sh /sdcard/Download/collect_statusbar_bootstrap.sh'"
    exit 1
fi
umask 077
SB_PARENT=${THUNDER_COLLECT_PARENT:-/sdcard/Download}
case "$SB_PARENT" in /*) ;; *) echo '输出目录必须是绝对路径'; exit 2 ;; esac
SB_NAME="thunder-statusbar-bootstrap-$(date +%Y%m%d-%H%M%S)-$$"
SB_DIR="$SB_PARENT/$SB_NAME"
mkdir -p "$SB_PARENT" || exit 1
mkdir "$SB_DIR" || exit 1
mkdir "$SB_DIR/logs" "$SB_DIR/state" "$SB_DIR/apk" || exit 1
trap 'echo "采集中断，已有文件保留在：$SB_DIR"; exit 130' INT TERM

SB_TIMEOUT=none
if command -v timeout >/dev/null 2>&1; then
    SB_TIMEOUT=command
elif command -v toybox >/dev/null 2>&1 && toybox timeout 1 true >/dev/null 2>&1; then
    SB_TIMEOUT=toybox
fi
query() {
    {
        case "$SB_TIMEOUT" in
            command) timeout 20 "$@" </dev/null ;;
            toybox) toybox timeout 20 "$@" </dev/null ;;
            *) echo '[skipped: timeout unavailable]'; return ;;
        esac
        printf '\n[command_exit=%s]\n' "$?"
    } 2>&1 | cat
}

# Keep context for class-loading errors and multiline Java stack traces. Match
# both modern callback names and the framework's older log wording.
cat > "$SB_DIR/filter.awk" <<'AWK'
{
    if ($0 ~ /^\[command_exit=|^\[skipped:/) { print; next }
    hit = ($0 ~ /[Tt]hunder|baishangui|window-bootstrap|statusbar-follow|statusbar-overlay|launcher-overlay|StatusBarFollow|HookEntry|onSystemServerLoaded|onSystemServerStarting|SystemServerStartHook|LSPosed.*(safe mode|SafeMode|system_server|Loading module|Failed to load|Failed.*system)|[Aa][Vv][Cc]:.*(lspd|lsposed|thunder)|logcat:|Permission denied|timeout:/)
    if (hit) {
        start = NR - 5
        if (start < 1) start = 1
        for (i = start; i < NR; i++) if (i > printed) { print lines[i % 6]; printed = i }
        until_line = NR + 24
    }
    if (hit || NR <= until_line) { print; printed = NR }
    lines[NR % 6] = $0
}
AWK

echo "正在采集启动链路；不需要重启或切换应用。"
echo "输出目录：$SB_DIR"
{
    echo 'collector=statusbar-bootstrap/1'
    date '+%Y-%m-%d %H:%M:%S %z'
    printf 'timeout=%s\n' "$SB_TIMEOUT"
    id
    id -Z 2>/dev/null
    for SB_PROP in ro.product.model ro.build.display.id ro.build.version.sdk ro.build.fingerprint sys.boot_completed; do
        printf '%s=%s\n' "$SB_PROP" "$(getprop "$SB_PROP" 2>/dev/null)"
    done
    cat /proc/sys/kernel/random/boot_id /proc/uptime 2>/dev/null
    echo 'system_server PID:'
    pidof system_server 2>/dev/null
    echo 'SystemUI PID:'
    pidof com.android.systemui 2>/dev/null
} > "$SB_DIR/state/device.txt" 2>&1
query dumpsys package io.github.baishangui.thunder > "$SB_DIR/state/thunder-package.txt"
query pm path io.github.baishangui.thunder > "$SB_DIR/state/thunder-apk-paths.txt"

# Retain the installed module itself to check its entry, scope and implementation marker.
SB_APK_INDEX=0
while IFS= read -r SB_PATH; do
    case "$SB_PATH" in
        package:/data/app/*.apk)
            SB_SOURCE=${SB_PATH#package:}
            SB_APK_INDEX=$((SB_APK_INDEX + 1))
            cp "$SB_SOURCE" "$SB_DIR/apk/$SB_APK_INDEX-${SB_SOURCE##*/}" 2>> "$SB_DIR/errors.txt"
            ;;
    esac
done < "$SB_DIR/state/thunder-apk-paths.txt"

# These are thunder's settings/diagnostic preferences only, not other app data.
for SB_USER in /data/user_de/[0-9]* /data/user/[0-9]*; do
    for SB_PREF in settings runtime_system runtime_launcher runtime runtime_cloud; do
        SB_FILE="$SB_USER/io.github.baishangui.thunder/shared_prefs/$SB_PREF.xml"
        if [ -f "$SB_FILE" ]; then
            SB_LABEL=$(printf '%s' "$SB_USER" | tr '/' '_')
            cp "$SB_FILE" "$SB_DIR/state/$SB_LABEL-$SB_PREF.xml" 2>> "$SB_DIR/errors.txt"
        fi
    done
done

# An old APK mapping may explain a mismatch even if the installed APK is new.
for SB_PID in $(pidof system_server 2>/dev/null); do
    {
        cat "/proc/$SB_PID/status" 2>/dev/null
        echo 'thunder / LSPosed / services mappings:'
        awk '/baishangui|thunder|lspd|lsposed|services[.](jar|odex|vdex)/' "/proc/$SB_PID/maps" 2>/dev/null
    } > "$SB_DIR/state/system-server-$SB_PID.txt"
done

echo '正在筛选 LSPosed 当前及上次启动日志……'
: > "$SB_DIR/logs/sources.txt"
SB_LOG_INDEX=0
for SB_LOG_ROOT in /data/adb/lspd/log /data/adb/lspd/log.old \
    /data/adb/lsposed/log /data/adb/lsposed/log.old; do
    [ -d "$SB_LOG_ROOT" ] || continue
    for SB_LOG in "$SB_LOG_ROOT"/*.log "$SB_LOG_ROOT"/*.txt; do
        [ -f "$SB_LOG" ] || continue
        SB_LOG_INDEX=$((SB_LOG_INDEX + 1))
        printf '%s\t%s\n' "$SB_LOG_INDEX" "$SB_LOG" >> "$SB_DIR/logs/sources.txt"
        query awk -f "$SB_DIR/filter.awk" "$SB_LOG" > "$SB_DIR/logs/$SB_LOG_INDEX-${SB_LOG##*/}.filtered.txt"
    done
done
query logcat -d -b all -v threadtime | awk -f "$SB_DIR/filter.awk" > "$SB_DIR/logs/logcat.filtered.txt"

{
    printf 'lsposed_source_count=%s\napk_count=%s\n' "$SB_LOG_INDEX" "$SB_APK_INDEX"
    echo '匹配到的启动阶段（空白不能单独证明未注入，日志可能已轮转）：'
    for SB_FILTERED in "$SB_DIR/logs/"*.filtered.txt; do
        [ ! -f "$SB_FILTERED" ] || awk '/window-bootstrap|window-report|launcher-overlay|overlay-scene|overlay-first-frame|overlay-publish|系统过渡报告暂不可用|系统过渡接口安装失败|窗口服务初始化失败/' "$SB_FILTERED"
    done
    echo '如 lsposed_source_count=0 或无有效日志，请从 LSPosed 管理器导出日志。'
} > "$SB_DIR/collection-status.txt"

echo '正在打包……'
if tar -czf "$SB_PARENT/$SB_NAME.tar.gz" -C "$SB_PARENT" "$SB_NAME" 2>> "$SB_DIR/errors.txt"; then
    echo "采集包：$SB_PARENT/$SB_NAME.tar.gz"
    echo '请将此 tar.gz 发回；脚本没有修改作用域、系统设置或清空日志。'
else
    echo "压缩失败，采集目录已保留：$SB_DIR"
    exit 1
fi
