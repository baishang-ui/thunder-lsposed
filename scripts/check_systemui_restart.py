"""Exercise the actual scope restart script with fake Android commands, never host processes."""
from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
asset = ROOT / 'app/src/main/assets/restart-scope.sh'
script = asset.read_text()
assert not re.search(r'\b(?:kill|killall|pkill|reboot|zygote)\b|\bam\s+crash\b', script)
for source in [*(ROOT / 'app/src/main/java').rglob('*.java'),
               ROOT / 'app/src/main/kotlin/io/github/baishangui/thunder/SettingsWriter.kt',
               ROOT / 'app/src/main/kotlin/io/github/baishangui/thunder/RemoteSettings.kt']:
    text = source.read_text()
    assert 'ProcessBuilder(' not in text and 'ScopeRestart.restart(' not in text, source


def run_case(mode):
    independent = mode.startswith('plugin-') and mode != 'plugin-appeared' or mode == 'independent'
    with tempfile.TemporaryDirectory(prefix='thunder-scope-') as folder:
        tmp = Path(folder)
        calls, hostpid, pluginpid = tmp / 'calls', tmp / 'host', tmp / 'plugin'
        proc = tmp / 'proc'
        for pid in ('42', '52'):
            (proc / pid).mkdir(parents=True)
            reused = mode == 'reused-pid' or mode == 'plugin-reused-pid' and pid == '52'
            (proc / pid / 'stat').write_text(pid + ' (system ui) S ' + '0 ' * 18 + ('12000' if reused else '1000'))
        (proc / 'uptime').write_text('159.99 0\n' if mode == 'not-stable' else '165.01 0\n')
        hostpid.write_text('' if mode == 'missing' else '1' if mode == 'bad-pid' else '42')
        pluginpid.write_text('52' if independent else '')
        def command(name, body):
            p = tmp / name
            p.write_text('#!/bin/sh\n' + body)
            p.chmod(0o755)
        command('id', 'echo ' + ('2000' if mode == 'denied' else '0'))
        command('date', 'if [ -f "$TEST_LATE" ]; then echo 201; else echo 100; fi')
        command('getconf', 'echo 100')
        command('sleep', 'true')
        command('pidof', '''case "$1" in
com.android.systemui) cat "$TEST_HOST";;
com.oplus.systemui.plugins) cat "$TEST_PLUGIN";;
*) exit 1;;
esac''')
        command('cmd', '''echo "cmd $*" >> "$TEST_CALLS"
case "$*" in
 "package query-services --components --user 0 -n com.android.systemui/com.android.systemui.SystemUIService")
  [ "$TEST_MODE" != unavailable ] || exit 1
  [ "$TEST_MODE" != changed-during-lookup ] || echo 44 > "$TEST_HOST"
  [ "$TEST_MODE" != plugin-appeared ] || echo 52 > "$TEST_PLUGIN"
  echo com.android.systemui/.SystemUIService;;
 "package query-services --components --user 0 -n com.oplus.systemui.plugins/com.oplus.systemui.plugins.seedling.plugin.SeedlingPluginService")
  [ "$TEST_MODE" != plugin-unavailable ] || exit 1
  [ "$TEST_MODE" != plugin-changed-during-lookup ] || echo 54 > "$TEST_PLUGIN"
  echo com.oplus.systemui.plugins/com.oplus.systemui.plugins.seedling.plugin.SeedlingPluginService;;
 *) exit 1;;
esac
''')
        command('am', '''echo "am $*" >> "$TEST_CALLS"
case "$*" in
 "force-stop --user 0 com.android.systemui")
  case "$TEST_MODE" in stop-failed|plugin-host-stop-failed) exit 1;; esac
  echo "" > "$TEST_HOST";;
 "force-stop --user 0 com.oplus.systemui.plugins")
  [ "$TEST_MODE" != plugin-stop-failed ] || exit 1
  echo "" > "$TEST_PLUGIN";;
 "startservice --user 0 -n com.android.systemui/com.android.systemui.SystemUIService")
  case "$TEST_MODE" in start-failed|plugin-host-start-failed) echo 'Error: service not found'; exit 0;; esac
  [ "$TEST_MODE" != start-exit-failed ] || exit 1
  [ "$TEST_MODE" = no-respawn ] || echo 43 > "$TEST_HOST";;
 "startservice --user 0 -n com.oplus.systemui.plugins/com.oplus.systemui.plugins.seedling.plugin.SeedlingPluginService")
  if [ "$TEST_MODE" = plugin-start-failed ]; then echo 'Error: service not found'; exit 0; fi
  [ "$TEST_MODE" = plugin-no-respawn ] || echo 53 > "$TEST_PLUGIN"
  [ "$TEST_MODE" != plugin-expired-after-start ] || touch "$TEST_LATE"
  [ "$TEST_MODE" != plugin-host-changed ] || echo 44 > "$TEST_HOST";;
 *) exit 1;;
esac
''')
        result = subprocess.run(['/bin/sh', '-c', script.replace('/proc/', str(proc) + '/')], capture_output=True, text=True, timeout=5,
            env={**os.environ, 'PATH': str(tmp) + ':/usr/bin:/bin', 'TEST_MODE': mode,
                 'TEST_HOST': str(hostpid), 'TEST_PLUGIN': str(pluginpid), 'TEST_CALLS': str(calls), 'TEST_LATE': str(tmp / 'late'),
                 'THUNDER_RESTART_CONFIRMED_ELAPSED': '' if mode == 'unconfirmed' else '100000',
                 'THUNDER_RESTART_EXPECTED_PID': '41' if mode == 'changed' else '42',
                 'THUNDER_RESTART_PLUGIN_PID': '0' if mode == 'plugin-unconfirmed' else '51' if mode == 'plugin-changed' else '52' if independent else '0',
                 'THUNDER_RESTART_PLUGIN_ELAPSED': '110000' if mode == 'plugin-not-stable' else '100000',
                 'THUNDER_RESTART_DEADLINE': '0' if mode == 'expired' else '200'})
        actions = calls.read_text().splitlines() if calls.exists() else []
        mutations = [a for a in actions if a.startswith('am ')]
        host_stop = 'am force-stop --user 0 com.android.systemui'
        plugin_stop = 'am force-stop --user 0 com.oplus.systemui.plugins'
        host_start = 'am startservice --user 0 -n com.android.systemui/com.android.systemui.SystemUIService'
        plugin_start = 'am startservice --user 0 -n com.oplus.systemui.plugins/com.oplus.systemui.plugins.seedling.plugin.SeedlingPluginService'
        assert actions.count(host_stop) <= 1 and actions.count(plugin_stop) <= 1
        if mode in {'hosted', 'independent'}:
            assert result.returncode == 0, result.stdout + result.stderr
            assert 'THUNDER_RESTART_HOST_OLD=42' in result.stdout and 'THUNDER_RESTART_HOST_NEW=43' in result.stdout
            expected = [host_stop, host_start]
            if independent:
                expected = [plugin_stop, plugin_start] + expected
                assert 'THUNDER_RESTART_PLUGIN_OLD=52' in result.stdout and 'THUNDER_RESTART_PLUGIN_NEW=53' in result.stdout
            else:
                assert 'THUNDER_RESTART_PLUGIN_MODE=host' in result.stdout
            assert mutations == expected, actions
            assert all(a.startswith('cmd ') for a in actions[:(2 if independent else 1)]), actions
        else:
            error = {'denied': 'ROOT_DENIED', 'expired': 'EXPIRED', 'plugin-expired-after-start': 'EXPIRED', 'missing': 'NOT_RUNNING',
                     'bad-pid': 'BAD_PID', 'changed': 'PROCESS_CHANGED', 'plugin-changed': 'PROCESS_CHANGED',
                     'reused-pid': 'PROCESS_CHANGED', 'plugin-reused-pid': 'PROCESS_CHANGED',
                     'not-stable': 'NOT_STABLE', 'plugin-not-stable': 'NOT_STABLE',
                     'unconfirmed': 'UNCONFIRMED_PROCESS', 'plugin-unconfirmed': 'PLUGIN_UNCONFIRMED',
                     'changed-during-lookup': 'PROCESS_CHANGED', 'plugin-changed-during-lookup': 'PROCESS_CHANGED',
                     'plugin-appeared': 'PROCESS_CHANGED', 'plugin-host-changed': 'PROCESS_CHANGED',
                     'unavailable': 'SERVICE_UNAVAILABLE', 'plugin-unavailable': 'SERVICE_UNAVAILABLE',
                     'stop-failed': 'STOP_FAILED', 'plugin-stop-failed': 'STOP_FAILED', 'plugin-host-stop-failed': 'STOP_FAILED',
                     'start-failed': 'START_FAILED', 'plugin-start-failed': 'START_FAILED', 'plugin-host-start-failed': 'START_FAILED',
                     'start-exit-failed': 'START_FAILED', 'no-respawn': 'NO_NEW_PROCESS', 'plugin-no-respawn': 'NO_NEW_PROCESS'}[mode]
            assert result.returncode != 0 and 'THUNDER_RESTART_ERROR=' + error in result.stdout, result.stdout
            if mode == 'plugin-not-stable': assert 'THUNDER_RESTART_TARGET=PLUGIN' in result.stdout
            if mode in {'plugin-host-stop-failed', 'plugin-host-start-failed'}:
                assert mutations[:2] == [plugin_stop, plugin_start]
                assert 'THUNDER_RESTART_PLUGIN_NEW=53' in result.stdout
                assert 'THUNDER_RESTART_TARGET=HOST' in result.stdout
            elif mode in {'plugin-stop-failed', 'plugin-start-failed', 'plugin-no-respawn', 'plugin-host-changed', 'plugin-expired-after-start'}:
                assert host_stop not in mutations and host_start not in mutations, actions
            elif mode not in {'stop-failed', 'start-failed', 'start-exit-failed', 'no-respawn'}:
                assert not mutations, actions
            if mode == 'stop-failed': assert host_start not in mutations
            if mode == 'plugin-stop-failed': assert plugin_start not in mutations
        print('PASS:', mode)


for mode in ['hosted', 'independent', 'denied', 'expired', 'missing', 'bad-pid', 'changed', 'changed-during-lookup',
             'unconfirmed', 'reused-pid', 'not-stable', 'unavailable', 'stop-failed', 'start-failed', 'start-exit-failed', 'no-respawn',
             'plugin-unconfirmed', 'plugin-changed', 'plugin-changed-during-lookup', 'plugin-appeared',
             'plugin-reused-pid', 'plugin-not-stable', 'plugin-unavailable', 'plugin-stop-failed', 'plugin-start-failed',
             'plugin-no-respawn', 'plugin-host-changed', 'plugin-expired-after-start', 'plugin-host-stop-failed', 'plugin-host-start-failed']:
    run_case(mode)
