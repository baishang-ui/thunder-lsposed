"""Exercise the shipped restart asset with fake Android commands; never signal a host PID."""
from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
script = (ROOT / 'app/src/main/assets/restart-scope.sh').read_text()
assert '/system/bin/kill -9 "$thunder_host_old"' in script
assert not re.search(r'\b(?:killall|pkill|pgrep|reboot|zygote)\b|\bam\s+(?:crash|force-stop)\b', script)
for source in [*(ROOT / 'app/src/main/java').rglob('*.java'),
               ROOT / 'app/src/main/kotlin/io/github/baishangui/thunder/SettingsWriter.kt',
               ROOT / 'app/src/main/kotlin/io/github/baishangui/thunder/RemoteSettings.kt']:
    assert 'ProcessBuilder(' not in source.read_text() and 'ScopeRestart.restart(' not in source.read_text(), source


def run_case(mode):
    with tempfile.TemporaryDirectory(prefix='thunder-systemui-restart-') as folder:
        tmp = Path(folder)
        calls, hostpid, pluginpid = tmp / 'calls', tmp / 'host', tmp / 'plugin'
        proc = tmp / 'proc'
        for pid in ('42', '52'):
            (proc / pid).mkdir(parents=True)
            (proc / pid / 'stat').write_text(pid + ' (system ui) S ' + '0 ' * 18 + '1000')
            (proc / pid / 'cmdline').write_bytes(b'com.android.systemui\0' if pid == '42' else b'com.oplus.systemui.plugins\0')
        (proc / 'uptime').write_text('10.01 0\n')  # A newly started process must not impose a cooldown.
        hostpid.write_text('' if mode == 'missing' else '1' if mode == 'bad-pid' else '42 44' if mode == 'multiple' else '42')
        pluginpid.write_text('52')
        if mode == 'wrong-name':
            (proc / '42/cmdline').write_bytes(b'com.android.systemui:other\0')
        if mode == 'unreadable':
            (proc / '42/stat').unlink()

        def command(name, body):
            p = tmp / name
            p.write_text('#!/bin/sh\n' + body)
            p.chmod(0o755)

        command('id', 'echo ' + ('2000' if mode == 'denied' else '0'))
        command('date', 'echo 100')
        command('sleep', 'true')
        command('timeout', 'shift\nexec "$@"')
        command('pidof', '''echo "pidof $*" >> "$TEST_CALLS"
case "$1" in
com.android.systemui)
 if [ -f "$TEST_QUERIED" ]; then
  [ "$TEST_MODE" != changed ] || echo 44 > "$TEST_HOST"
  [ "$TEST_MODE" != reused-pid ] || echo '42 (system ui) S 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 2000' > "$TEST_PROC/42/stat"
 else touch "$TEST_QUERIED"; fi
 cat "$TEST_HOST";;
com.oplus.systemui.plugins) cat "$TEST_PLUGIN";;
*) exit 1;;
esac''')
        command('kill', '''echo "kill $*" >> "$TEST_CALLS"
[ "$*" = "-9 42" ] || exit 1
[ "$TEST_MODE" != signal-failed ] || exit 1
[ "$TEST_MODE" != old-alive ] || exit 0
[ "$TEST_MODE" != phantom-new ] || { echo 43 > "$TEST_HOST"; exit 0; }
rm "$TEST_PROC/42/stat"
if [ "$TEST_MODE" = signal-exited-race ]; then echo 43 > "$TEST_HOST"; exit 1; fi
case "$TEST_MODE" in
 immediate|signal-exited-race) echo 43 > "$TEST_HOST";;
 *) echo "" > "$TEST_HOST";;
esac
''')
        command('am', '''echo "am $*" >> "$TEST_CALLS"
[ "$*" = "startservice --user 0 -n com.android.systemui/com.android.systemui.SystemUIService" ] || exit 1
if [ "$TEST_MODE" = start-failed ]; then echo 'Error: service not found'; exit 0; fi
[ "$TEST_MODE" != start-exit-failed ] || exit 1
if [ "$TEST_MODE" = start-retry ] && [ ! -f "$TEST_RETRIED" ]; then touch "$TEST_RETRIED"; echo 'Error: temporary'; exit 1; fi
[ "$TEST_MODE" = no-respawn ] || echo 43 > "$TEST_HOST"
''')
        test_script = script.replace('/proc/', str(proc) + '/').replace('/system/bin/kill', str(tmp / 'kill'))
        result = subprocess.run(['/bin/sh', '-c', test_script], capture_output=True, text=True, timeout=10,
            env={**os.environ, 'PATH': str(tmp) + ':/usr/bin:/bin', 'TEST_MODE': mode,
                 'TEST_HOST': str(hostpid), 'TEST_PLUGIN': str(pluginpid), 'TEST_CALLS': str(calls),
                 'TEST_PROC': str(proc), 'TEST_RETRIED': str(tmp / 'retried'), 'TEST_QUERIED': str(tmp / 'queried'),
                 'THUNDER_RESTART_DEADLINE': '0' if mode == 'expired' else '200'})
        actions = calls.read_text().splitlines() if calls.exists() else []
        mutations = [a for a in actions if a.startswith(('am ', 'kill '))]
        host_stop = 'kill -9 42'
        host_start = 'am startservice --user 0 -n com.android.systemui/com.android.systemui.SystemUIService'
        assert actions.count(host_stop) <= 1 and actions.count(host_start) <= 2, actions
        assert all('com.oplus.systemui.plugins' not in a for a in actions), actions
        assert pluginpid.read_text() == '52' and (proc / '52/stat').exists()
        if mode in {'immediate', 'service-recovery', 'start-retry', 'signal-exited-race'}:
            assert result.returncode == 0, result.stdout + result.stderr
            assert 'THUNDER_RESTART_HOST_OLD=42' in result.stdout and 'THUNDER_RESTART_HOST_NEW=43' in result.stdout
            assert 'THUNDER_RESTART_HOST_EXITED=1' in result.stdout
            assert not (proc / '42/stat').exists()
            assert mutations.count(host_stop) == 1, actions
            if mode in {'immediate', 'signal-exited-race'}: assert mutations == [host_stop], actions
            if mode == 'start-retry': assert mutations.count(host_start) == 2
        else:
            error = {'denied': 'ROOT_DENIED', 'expired': 'EXPIRED', 'missing': 'NOT_RUNNING',
                     'bad-pid': 'BAD_PID', 'multiple': 'BAD_PID', 'changed': 'PROCESS_CHANGED',
                     'reused-pid': 'PROCESS_CHANGED', 'wrong-name': 'UNCONFIRMED_PROCESS',
                     'unreadable': 'UNCONFIRMED_PROCESS', 'signal-failed': 'STOP_FAILED',
                     'old-alive': 'OLD_PROCESS_ALIVE', 'phantom-new': 'OLD_PROCESS_ALIVE',
                     'start-failed': 'START_FAILED', 'start-exit-failed': 'START_FAILED', 'no-respawn': 'NO_NEW_PROCESS'}[mode]
            assert result.returncode != 0 and 'THUNDER_RESTART_ERROR=' + error in result.stdout, result.stdout
            if mode in {'signal-failed', 'old-alive', 'phantom-new'}: assert mutations == [host_stop], actions
            elif mode not in {'start-failed', 'start-exit-failed', 'no-respawn'}: assert not mutations, actions
            if mode in {'old-alive', 'phantom-new'}: assert '_NEW=' not in result.stdout
        print('PASS:', mode)


for mode in ['immediate', 'service-recovery', 'start-retry', 'signal-exited-race', 'denied', 'expired',
             'missing', 'bad-pid', 'multiple', 'changed', 'reused-pid', 'wrong-name', 'unreadable',
             'signal-failed', 'old-alive', 'phantom-new', 'start-failed', 'start-exit-failed', 'no-respawn']:
    run_case(mode)
