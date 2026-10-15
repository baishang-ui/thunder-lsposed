#!/system/bin/sh
# ColorOS 17 window/status-bar transition diagnostics, version 1.
# Run: su -c 'sh /sdcard/Download/collect_window_transitions.sh --sample'
# Reads firmware and service state; only writes to the output directory/archive.

usage() {
    echo "用法：su -c 'sh /sdcard/Download/collect_window_transitions.sh [--sample]'"
    echo "--sample：复制完成后，额外采样 6 次窗口动画状态；期间可手动打开应用、返回桌面、切换任务。"
    echo "默认输出到 /sdcard/Download；可用 THUNDER_COLLECT_PARENT 指定其他输出目录。"
}

SAMPLE=0
case "$#:$1" in
    0:) ;;
    1:--sample) SAMPLE=1 ;;
    1:--help|1:-h) usage; exit 0 ;;
    *) usage; exit 2 ;;
esac
if [ "$(id -u)" != 0 ]; then
    echo "需要 Root 才能读取完整固件。请按以下命令运行："
    usage
    exit 1
fi

umask 077
DIAG_PARENT=${THUNDER_COLLECT_PARENT:-/sdcard/Download}
case "$DIAG_PARENT" in
    /*) ;;
    *) echo "输出目录必须是绝对路径。"; exit 2 ;;
esac
DIAG_NAME="thunder-window-transitions-$(date +%Y%m%d-%H%M%S)-$$"
DIAG_DIR="$DIAG_PARENT/$DIAG_NAME"
mkdir -p "$DIAG_PARENT" || exit 1
mkdir "$DIAG_DIR" || exit 1
mkdir -p "$DIAG_DIR/files" "$DIAG_DIR/state" "$DIAG_DIR/packages" || exit 1
: > "$DIAG_DIR/errors.txt"
printf 'role\tsource\tbytes\tdex\n' > "$DIAG_DIR/code-index.tsv"
trap 'echo "采集已中断，已保存的文件仍在：$DIAG_DIR"; exit 130' INT TERM

# Binder rejects some external-storage file descriptors. Pipe command output
# before saving it, and bound each query even when a service is unresponsive.
TIMEOUT_MODE=none
if command -v timeout >/dev/null 2>&1; then
    TIMEOUT_MODE=command
elif command -v toybox >/dev/null 2>&1 && toybox timeout 1 true >/dev/null 2>&1; then
    TIMEOUT_MODE=toybox
fi
query() {
    {
        case "$TIMEOUT_MODE" in
            command) timeout 20 "$@" </dev/null ;;
            toybox) toybox timeout 20 "$@" </dev/null ;;
            *) echo '[query skipped: timeout command unavailable]'; return 0 ;;
        esac
        query_result=$?
        printf '\n[command_exit=%s]\n' "$query_result"
    } 2>&1 | cat
}

# Full source paths prevent collisions between partitions, APK splits and JARs.
# Each function uses its own variable prefix because Android sh has no portable
# function-local variables. Repeated files are copied only once.
copy_file() {
    CF_SOURCE=$1
    CF_ROLE=$2
    case "$CF_SOURCE" in /*) ;; *) return 1 ;; esac
    [ -f "$CF_SOURCE" ] || return 1
    CF_DEST="$DIAG_DIR/files$CF_SOURCE"
    [ ! -f "$CF_DEST" ] || return 0
    if ! mkdir -p "${CF_DEST%/*}" || ! cp "$CF_SOURCE" "$CF_DEST" 2>> "$DIAG_DIR/errors.txt"; then
        printf 'COPY_FAILED %s\n' "$CF_SOURCE" >> "$DIAG_DIR/errors.txt"
        return 1
    fi
    CF_DEX=unknown
    case "$CF_SOURCE" in
        *.apk|*.jar)
            if command -v unzip >/dev/null 2>&1; then
                if unzip -l "$CF_DEST" > "$DIAG_DIR/zip-list.tmp" 2>> "$DIAG_DIR/errors.txt"; then
                    CF_DEX=no
                    if grep -qE ' classes[0-9]*\.dex[[:space:]]*$' "$DIAG_DIR/zip-list.tmp"; then
                        CF_DEX=yes
                    fi
                fi
            fi
            ;;
    esac
    CF_BYTES=$(wc -c < "$CF_DEST" | tr -d '[:space:]')
    printf '%s\t%s\t%s\t%s\n' "$CF_ROLE" "$CF_SOURCE" "$CF_BYTES" "$CF_DEX" >> "$DIAG_DIR/code-index.tsv"
}

copy_sidecars() {
    CS_SOURCE=$1
    CS_BASE=${CS_SOURCE##*/}
    CS_BASE=${CS_BASE%.*}
    CS_DIR=${CS_SOURCE%/*}
    for CS_FILE in "$CS_DIR"/oat/*/"$CS_BASE".odex "$CS_DIR"/oat/*/"$CS_BASE".vdex \
        "$CS_DIR"/oat/*/"$CS_BASE".oat "$CS_DIR"/*/"$CS_BASE".odex \
        "$CS_DIR"/*/"$CS_BASE".vdex "$CS_DIR"/*/"$CS_BASE".oat "$CS_DIR/$CS_BASE.dm"; do
        [ ! -f "$CS_FILE" ] || copy_file "$CS_FILE" compiled
    done
}

copy_package() {
    CP_PACKAGE=$1
    CP_ROLE=$2
    echo "正在导出 $CP_PACKAGE"
    query pm path "$CP_PACKAGE" > "$DIAG_DIR/packages/$CP_PACKAGE-paths.txt"
    query dumpsys package "$CP_PACKAGE" > "$DIAG_DIR/packages/$CP_PACKAGE-info.txt"
    while IFS= read -r CP_LINE; do
        case "$CP_LINE" in
            package:/*.apk)
                CP_SOURCE=${CP_LINE#package:}
                copy_file "$CP_SOURCE" "$CP_ROLE"
                copy_sidecars "$CP_SOURCE"
                ;;
        esac
    done < "$DIAG_DIR/packages/$CP_PACKAGE-paths.txt"
}

echo "输出目录：$DIAG_DIR"
echo "正在读取版本和系统桌面信息……"
{
    printf 'collector_version=1\ntimeout_mode=%s\n' "$TIMEOUT_MODE"
    for PROP in ro.product.manufacturer ro.product.model ro.build.version.release \
        ro.build.version.sdk ro.build.display.id ro.build.fingerprint \
        ro.build.version.oplusrom ro.build.version.ota ro.product.cpu.abilist; do
        printf '%s=%s\n' "$PROP" "$(getprop "$PROP" 2>/dev/null)"
    done
    id -Z 2>/dev/null
    df -h "$DIAG_PARENT"
} > "$DIAG_DIR/state/build.txt" 2>&1
query cmd package resolve-activity --brief --user current \
    -a android.intent.action.MAIN -c android.intent.category.HOME > "$DIAG_DIR/state/home-resolver.txt"
query cmd package query-activities --brief --user current \
    -a android.intent.action.MAIN -c android.intent.category.HOME > "$DIAG_DIR/state/home-candidates.txt"
{
    echo com.android.launcher
    echo com.oplus.launcher
    echo com.oppo.launcher
    awk '/^[A-Za-z0-9_.]+\/[A-Za-z0-9_.$]+$/ {sub(/\/.*/, ""); print}' \
        "$DIAG_DIR/state/home-resolver.txt" "$DIAG_DIR/state/home-candidates.txt"
} | sort -u > "$DIAG_DIR/packages/launcher-packages.txt"
while IFS= read -r PACKAGE; do copy_package "$PACKAGE" launcher; done < "$DIAG_DIR/packages/launcher-packages.txt"
copy_package com.android.systemui systemui
copy_package com.oplus.systemui.plugins plugin

# Recover firmware APKs when package-manager/Binder access is blocked. Data/app
# is restricted to known launcher/SystemUI paths; no app data is read.
echo "正在直接查找桌面与 SystemUI APK……"
{
    for SCAN_ROOT in /system/app /system/priv-app /system_ext/app /system_ext/priv-app \
        /product/app /product/priv-app /vendor/app /vendor/priv-app /odm/app /odm/priv-app \
        /my_product/app /my_product/priv-app /my_stock/app /my_stock/priv-app \
        /my_region/app /my_region/priv-app /data/app; do
        [ ! -d "$SCAN_ROOT" ] || find "$SCAN_ROOT" -type f -name '*.apk' 2>> "$DIAG_DIR/errors.txt"
    done
} | awk 'tolower($0) ~ /systemui|system_ui|system-ui|launcher/ && tolower($0) !~ /\/overlay\//' \
    | sort -u > "$DIAG_DIR/packages/direct-apk-paths.txt"
while IFS= read -r APK; do
    APK_LOWER=$(printf '%s' "$APK" | tr '[:upper:]' '[:lower:]')
    case "$APK_LOWER" in
        *launcher*) APK_ROLE=launcher ;;
        *plugin*) APK_ROLE=plugin ;;
        *) APK_ROLE=systemui ;;
    esac
    copy_file "$APK" "$APK_ROLE"
    copy_sidecars "$APK"
done < "$DIAG_DIR/packages/direct-apk-paths.txt"

# Capture classpaths only, never the full process environment.
{
    for CLASSPATH_KEY in BOOTCLASSPATH DEX2OATBOOTCLASSPATH SYSTEMSERVERCLASSPATH STANDALONE_SYSTEMSERVER_JARS; do
        printf '%s=%s\n' "$CLASSPATH_KEY" "$(printenv "$CLASSPATH_KEY" 2>/dev/null)"
    done
    for SYSTEM_PID in $(pidof system_server 2>/dev/null); do
        if [ -r "/proc/$SYSTEM_PID/environ" ]; then
            tr '\000' '\n' < "/proc/$SYSTEM_PID/environ" | \
                grep -E '^(BOOTCLASSPATH|DEX2OATBOOTCLASSPATH|SYSTEMSERVERCLASSPATH|STANDALONE_SYSTEMSERVER_JARS)='
        fi
    done
} > "$DIAG_DIR/state/classpaths.txt"
awk '{sub(/^[^=]*=/, ""); gsub(/:/, "\n"); print}' "$DIAG_DIR/state/classpaths.txt" | \
    sort -u > "$DIAG_DIR/state/classpath-files.txt"

echo "正在导出 framework、services 与各分区框架 JAR（含 Oplus 扩展）……"
{
    for FRAMEWORK_ROOT in /system/framework /system_ext/framework /product/framework \
        /vendor/framework /odm/framework /my_product/framework /my_stock/framework /my_region/framework; do
        [ ! -d "$FRAMEWORK_ROOT" ] || find "$FRAMEWORK_ROOT" -type f -name '*.jar' 2>> "$DIAG_DIR/errors.txt"
    done
    cat "$DIAG_DIR/state/classpath-files.txt"
} | sort -u > "$DIAG_DIR/state/framework-files.txt"
while IFS= read -r FRAMEWORK_FILE; do
    case "$FRAMEWORK_FILE" in
        /*.jar) copy_file "$FRAMEWORK_FILE" framework; copy_sidecars "$FRAMEWORK_FILE" ;;
    esac
done < "$DIAG_DIR/state/framework-files.txt"

# Boot JARs may have their DEX stripped into a boot image. Retain code-bearing
# OAT/VDEX companions, including vendor boot extensions, but omit ART heap images.
for FRAMEWORK_ROOT in /system/framework /system_ext/framework /product/framework \
    /vendor/framework /odm/framework /my_product/framework /my_stock/framework /my_region/framework; do
    for BOOT_CODE in "$FRAMEWORK_ROOT"/*/boot*.oat "$FRAMEWORK_ROOT"/*/boot*.vdex \
        "$FRAMEWORK_ROOT"/oat/*/boot*.oat "$FRAMEWORK_ROOT"/oat/*/boot*.vdex; do
        [ ! -f "$BOOT_CODE" ] || copy_file "$BOOT_CODE" compiled
    done
done

echo "正在读取窗口、图层与过渡设置……"
query dumpsys window windows > "$DIAG_DIR/state/window-windows.txt"
query dumpsys window displays > "$DIAG_DIR/state/window-displays.txt"
query dumpsys window containers > "$DIAG_DIR/state/window-containers.txt"
query dumpsys window policy > "$DIAG_DIR/state/window-policy.txt"
query dumpsys window animator > "$DIAG_DIR/state/window-animator.txt"
query dumpsys window -h > "$DIAG_DIR/state/window-help.txt"
query dumpsys SurfaceFlinger --list > "$DIAG_DIR/state/surface-layers.txt"
query wm size > "$DIAG_DIR/state/display-size.txt"
query wm density > "$DIAG_DIR/state/display-density.txt"
{
    for SETTING in window_animation_scale transition_animation_scale animator_duration_scale; do
        echo "$SETTING"
        query settings get global "$SETTING"
    done
} > "$DIAG_DIR/state/animation-settings.txt"
query cmd overlay list --user current > "$DIAG_DIR/state/overlays.txt"

if [ "$SAMPLE" = 1 ]; then
    mkdir -p "$DIAG_DIR/samples"
    echo "现在开始采样：请手动打开应用、返回桌面、进入最近任务并切换应用。"
    echo "共采样 6 次，正常约 20 秒；若系统服务响应缓慢，耗时会更长。"
    SAMPLE_INDEX=1
    while [ "$SAMPLE_INDEX" -le 6 ]; do
        {
            date '+%Y-%m-%d %H:%M:%S %z'
            query dumpsys window animator
        } > "$DIAG_DIR/samples/$SAMPLE_INDEX-animator.txt"
        query dumpsys SurfaceFlinger --list > "$DIAG_DIR/samples/$SAMPLE_INDEX-layers.txt"
        SAMPLE_INDEX=$((SAMPLE_INDEX + 1))
        [ "$SAMPLE_INDEX" -gt 6 ] || sleep 3
    done
fi

{
    echo 'collector_version=1'
    for REQUIRED_ROLE in launcher systemui framework; do
        if awk -F '\t' -v role="$REQUIRED_ROLE" '$1 == role {found=1} END {exit !found}' "$DIAG_DIR/code-index.tsv"; then
            printf '%s_copied=yes\n' "$REQUIRED_ROLE"
        else
            printf '%s_copied=NO\n' "$REQUIRED_ROLE"
        fi
    done
    for REQUIRED_JAR in services.jar framework.jar; do
        if awk -F '\t' -v jar="/$REQUIRED_JAR" 'index($2, jar) == length($2)-length(jar)+1 {found=1} END {exit !found}' "$DIAG_DIR/code-index.tsv"; then
            printf '%s_copied=yes\n' "$REQUIRED_JAR"
        else
            printf '%s_copied=NO\n' "$REQUIRED_JAR"
        fi
    done
    printf 'passive_samples=%s\n' "$SAMPLE"
    echo 'dex=no means optimized companions may be needed; copied does not guarantee decompilation.'
    echo 'See errors.txt and state/* for copy errors, query timeouts (124), and unsupported commands.'
} > "$DIAG_DIR/collection-status.txt"
cat > "$DIAG_DIR/README.txt" <<'EOF'
Purpose: assess whether the status bar can follow app launch, home and recents transitions on this firmware.
files/ preserves absolute source paths. code-index.tsv lists successful copies, sizes and embedded DEX status.
Includes launcher/SystemUI APKs, framework JARs, available compiled code, package metadata and window/layer state.
Runtime state can contain app package names, window titles and paths. It does not collect app private data,
notification dumps, screenshots, screen recordings, full logcat, or the full process environment.
The collector does not change settings/overlays, launch apps, restart processes, or enable tracing.
Optional samples are coarse passive snapshots, not a frame-by-frame animation trace.
Original collected files are retained if compression fails or collection is interrupted.
EOF

if command -v sha256sum >/dev/null 2>&1; then
    echo "正在计算固件文件校验值……"
    (cd "$DIAG_DIR" && find files -type f -exec sha256sum '{}' \;) > "$DIAG_DIR/SHA256SUMS.txt" 2>> "$DIAG_DIR/errors.txt"
fi
echo "正在打包；框架文件较多，请等待……"
if tar -czf "$DIAG_PARENT/$DIAG_NAME.tar.gz" -C "$DIAG_PARENT" "$DIAG_NAME" 2>> "$DIAG_DIR/errors.txt"; then
    cat "$DIAG_DIR/collection-status.txt"
    echo "采集包：$DIAG_PARENT/$DIAG_NAME.tar.gz"
    du -h "$DIAG_PARENT/$DIAG_NAME.tar.gz"
    echo "请把这个 .tar.gz 文件发回。若上方显示 NO，也请保留并发回，包内含失败诊断。"
else
    echo "压缩失败，原始采集目录已保留：$DIAG_DIR"
    echo "同名 .tar.gz 可能不完整，请用文件管理器压缩上述目录后发回。"
    exit 1
fi
