"""Exercise process replacement using isolated fake Android commands. Never signal a host PID."""
from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
script = (ROOT / 'app/src/main/assets/restart-scope.sh').read_text()
# LuckyTool-style SystemUI exit, restricted to one previously validated PID.
assert '/system/bin/kill -9 "$thunder_host_old"' in script
assert 'force-stop --user 0 com.android.systemui' not in script
assert not re.search(r'\b(?:killall|pkill|pgrep|reboot|zygote)\b|\bam\s+crash\b', script)
for source in [*(ROOT / 'app/src/main/java').rglob('*.java'),
               ROOT / 'app/src/main/kotlin/io/github/baishangui/thunder/SettingsWriter.kt',
               ROOT / 'app/src/main/kotlin/io/github/baishangui/thunder/RemoteSettings.kt']:
    assert 'ProcessBuilder(' not in source.read_text() and 'ScopeRestart.restart(' not in source.read_text(), source


def run_case(mode):
    independent = mode.startswith('plugin-') and mode != 'plugin-appeared' or mode == 'independent'
    with tempfile.TemporaryDirectory(prefix='thunder-process-restart-') as folder:
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
        command('timeout', 'shift\nexec "$@"')
        command('pidof', '''case "$1" in
com.android.systemui) cat "$TEST_HOST";;
com.oplus.systemui.plugins) cat "$TEST_PLUGIN";;
*) exit 1;;
esac''')
        command('cmd', '''echo "cmd $*" >> "$TEST_CALLS"
[ "$*" = "package query-services --components --user 0 -n com.oplus.systemui.plugins/com.oplus.systemui.plugins.seedling.plugin.SeedlingPluginService" ] || exit 1
[ "$TEST_MODE" != plugin-unavailable ] || exit 1
[ "$TEST_MODE" != plugin-changed-during-lookup ] || echo 54 > "$TEST_PLUGIN"
[ "$TEST_MODE" != plugin-host-changed-during-lookup ] || echo 44 > "$TEST_HOST"
echo com.oplus.systemui.plugins/com.oplus.systemui.plugins.seedling.plugin.SeedlingPluginService
''')
        command('kill', '''echo "kill $*" >> "$TEST_CALLS"
[ "$*" = "-9 42" ] || exit 1
[ "$TEST_MODE" != signal-failed ] || exit 1
# Even a zero command exit must not be confused with the old process actually exiting.
[ "$TEST_MODE" != old-alive ] || exit 0
[ "$TEST_MODE" != phantom-new ] || { echo 43 > "$TEST_HOST"; exit 0; }
rm "$TEST_PROC/42/stat"
if [ "$TEST_MODE" = signal-exited-race ]; then echo 43 > "$TEST_HOST"; exit 1; fi
case "$TEST_MODE" in
 hosted|independent|plugin-*) echo 43 > "$TEST_HOST";;
 *) echo "" > "$TEST_HOST";;
esac
''')
        command('am', '''echo "am $*" >> "$TEST_CALLS"
case "$*" in
 "force-stop --user 0 com.oplus.systemui.plugins")
  [ "$TEST_MODE" != plugin-stop-failed ] || exit 1
  [ "$TEST_MODE" != plugin-old-alive ] || exit 0
  rm "$TEST_PROC/52/stat"
  echo "" > "$TEST_PLUGIN";;
 "startservice --user 0 -n com.android.systemui/com.android.systemui.SystemUIService")
  if [ "$TEST_MODE" = start-failed ]; then echo 'Error: service not found'; exit 0; fi
  [ "$TEST_MODE" != start-exit-failed ] || exit 1
  if [ "$TEST_MODE" = start-retry ] && [ ! -f "$TEST_RETRIED" ]; then touch "$TEST_RETRIED"; echo 'Error: temporary'; exit 1; fi
  [ "$TEST_MODE" = no-respawn ] || echo 43 > "$TEST_HOST";;
 "startservice --user 0 -n com.oplus.systemui.plugins/com.oplus.systemui.plugins.seedling.plugin.SeedlingPluginService")
  if [ "$TEST_MODE" = plugin-start-failed ]; then echo 'Error: service not found'; exit 0; fi
  if [ "$TEST_MODE" = plugin-start-retry ] && [ ! -f "$TEST_RETRIED" ]; then touch "$TEST_RETRIED"; echo 'Error: temporary'; exit 1; fi
  [ "$TEST_MODE" = plugin-no-respawn ] || echo 53 > "$TEST_PLUGIN"
  [ "$TEST_MODE" != plugin-late-start ] || touch "$TEST_LATE"
  if [ "$TEST_MODE" = plugin-host-recreated ]; then rm "$TEST_PROC/42/stat"; echo 44 > "$TEST_HOST"; fi
  if [ "$TEST_MODE" = plugin-host-exited ]; then rm "$TEST_PROC/42/stat"; echo "" > "$TEST_HOST"; fi
  [ "$TEST_MODE" != plugin-host-phantom ] || echo 44 > "$TEST_HOST";;
 *) exit 1;;
esac
''')
        # Replace the absolute Android kill binary before executing the actual asset on the host.
        test_script = script.replace('/proc/', str(proc) + '/').replace('/system/bin/kill', str(tmp / 'kill'))
        result = subprocess.run(['/bin/sh', '-c', test_script], capture_output=True, text=True, timeout=10,
            env={**os.environ, 'PATH': str(tmp) + ':/usr/bin:/bin', 'TEST_MODE': mode,
                 'TEST_HOST': str(hostpid), 'TEST_PLUGIN': str(pluginpid), 'TEST_CALLS': str(calls),
                 'TEST_PROC': str(proc), 'TEST_RETRIED': str(tmp / 'retried'), 'TEST_LATE': str(tmp / 'late'),
                 'THUNDER_RESTART_CONFIRMED_ELAPSED': '' if mode == 'unconfirmed' else '100000',
                 'THUNDER_RESTART_EXPECTED_PID': '41' if mode == 'changed' else '42',
                 'THUNDER_RESTART_PLUGIN_PID': '0' if mode == 'plugin-unconfirmed' else '51' if mode == 'plugin-changed' else '52' if independent else '0',
                 'THUNDER_RESTART_PLUGIN_ELAPSED': '110000' if mode == 'plugin-not-stable' else '100000',
                 'THUNDER_RESTART_DEADLINE': '0' if mode == 'expired' else '200'})
        actions = calls.read_text().splitlines() if calls.exists() else []
        mutations = [a for a in actions if a.startswith(('am ', 'kill '))]
        host_stop = 'kill -9 42'
        plugin_stop = 'am force-stop --user 0 com.oplus.systemui.plugins'
        host_start = 'am startservice --user 0 -n com.android.systemui/com.android.systemui.SystemUIService'
        plugin_start = 'am startservice --user 0 -n com.oplus.systemui.plugins/com.oplus.systemui.plugins.seedling.plugin.SeedlingPluginService'
        assert actions.count(host_stop) <= 1 and actions.count(plugin_stop) <= 1, actions
        assert actions.count(host_start) <= 2 and actions.count(plugin_start) <= 2, actions
        successes = {'hosted', 'independent', 'service-recovery', 'start-retry', 'signal-exited-race',
                     'plugin-start-retry', 'plugin-late-start', 'plugin-host-recreated', 'plugin-host-exited'}
        if mode in successes:
            assert result.returncode == 0, result.stdout + result.stderr
            expected_host = '44' if mode == 'plugin-host-recreated' else '43'
            assert 'THUNDER_RESTART_HOST_OLD=42' in result.stdout and 'THUNDER_RESTART_HOST_NEW=' + expected_host in result.stdout
            assert 'THUNDER_RESTART_HOST_EXITED=1' in result.stdout
            assert not (proc / '42/stat').exists()
            if independent:
                assert mutations[0] == plugin_stop
                assert 'THUNDER_RESTART_PLUGIN_EXITED=1' in result.stdout and 'THUNDER_RESTART_PLUGIN_NEW=53' in result.stdout
            else: assert 'THUNDER_RESTART_PLUGIN_MODE=host' in result.stdout
            if mode in {'hosted', 'independent'}: assert host_start not in mutations, actions
            if mode == 'start-retry': assert mutations.count(host_start) == 2
            if mode == 'plugin-start-retry': assert mutations.count(plugin_start) == 2
            if mode in {'plugin-host-recreated', 'plugin-host-exited'}: assert host_stop not in mutations, actions
            else: assert mutations.count(host_stop) == 1, actions
        else:
            error = {'denied': 'ROOT_DENIED', 'expired': 'EXPIRED', 'missing': 'NOT_RUNNING',
                     'bad-pid': 'BAD_PID', 'changed': 'PROCESS_CHANGED',
                     'reused-pid': 'PROCESS_CHANGED', 'unconfirmed': 'UNCONFIRMED_PROCESS', 'not-stable': 'NOT_STABLE',
                     'signal-failed': 'STOP_FAILED', 'old-alive': 'OLD_PROCESS_ALIVE', 'phantom-new': 'OLD_PROCESS_ALIVE',
                     'start-failed': 'START_FAILED', 'start-exit-failed': 'START_FAILED', 'no-respawn': 'NO_NEW_PROCESS',
                     'plugin-unconfirmed': 'PLUGIN_UNCONFIRMED', 'plugin-changed': 'PROCESS_CHANGED',
                     'plugin-changed-during-lookup': 'PROCESS_CHANGED', 'plugin-host-changed-during-lookup': 'PROCESS_CHANGED',
                     'plugin-reused-pid': 'PROCESS_CHANGED', 'plugin-not-stable': 'NOT_STABLE',
                     'plugin-unavailable': 'SERVICE_UNAVAILABLE', 'plugin-stop-failed': 'STOP_FAILED',
                     'plugin-old-alive': 'OLD_PROCESS_ALIVE', 'plugin-start-failed': 'START_FAILED',
                     'plugin-no-respawn': 'NO_NEW_PROCESS', 'plugin-host-phantom': 'PROCESS_CHANGED'}[mode]
            assert result.returncode != 0 and 'THUNDER_RESTART_ERROR=' + error in result.stdout, result.stdout
            if mode.startswith('plugin-'): assert host_stop not in mutations and host_start not in mutations, actions
            elif mode in {'signal-failed', 'old-alive', 'phantom-new'}: assert mutations == [host_stop], actions
            elif mode not in {'start-failed', 'start-exit-failed', 'no-respawn'}: assert not mutations, actions
            if mode in {'old-alive', 'phantom-new', 'plugin-old-alive'}: assert '_NEW=' not in result.stdout
        print('PASS:', mode)


MODES = ['hosted', 'independent', 'service-recovery', 'start-retry', 'signal-exited-race', 'plugin-start-retry', 'plugin-late-start',
         'plugin-host-recreated', 'plugin-host-exited', 'denied', 'expired', 'missing', 'bad-pid', 'changed',
         'unconfirmed', 'reused-pid', 'not-stable', 'signal-failed', 'old-alive', 'phantom-new',
         'start-failed', 'start-exit-failed', 'no-respawn', 'plugin-unconfirmed', 'plugin-changed',
         'plugin-changed-during-lookup', 'plugin-host-changed-during-lookup', 'plugin-reused-pid',
         'plugin-not-stable', 'plugin-unavailable', 'plugin-stop-failed', 'plugin-old-alive',
         'plugin-start-failed', 'plugin-no-respawn', 'plugin-host-phantom']
for mode in MODES:
    run_case(mode)
