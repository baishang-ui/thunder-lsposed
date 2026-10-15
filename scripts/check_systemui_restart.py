"""Check managed SystemUI restart sequencing with fake Android commands; never stop a host process."""
from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
asset = ROOT / 'app/src/main/assets/restart-systemui.sh'
script = asset.read_text()
# Android manages the package lifecycle. A signal fallback would reintroduce the original defect.
assert not re.search(r'\b(?:kill|killall|pkill|reboot|zygote)\b|\bam\s+crash\b', script)
for source in [*(ROOT / 'app/src/main/java').rglob('*.java'),
               ROOT / 'app/src/main/kotlin/io/github/baishangui/thunder/SettingsWriter.kt',
               ROOT / 'app/src/main/kotlin/io/github/baishangui/thunder/RemoteSettings.kt']:
    text = source.read_text()
    assert 'ProcessBuilder(' not in text and 'SystemUiSettings.restart(' not in text, source


def run_case(mode):
    with tempfile.TemporaryDirectory(prefix='thunder-restart-') as folder:
        tmp = Path(folder)
        calls, pidfile = tmp / 'calls', tmp / 'pid'
        proc = tmp / 'proc'
        (proc / '42').mkdir(parents=True)
        # stat field 22 is process start time in clock ticks; include spaces in comm.
        (proc / '42/stat').write_text('42 (system ui) S ' + '0 ' * 18 + ('12000' if mode == 'reused-pid' else '1000'))
        (proc / 'uptime').write_text('159.99 0\n' if mode == 'not-stable' else '165.01 0\n')
        pidfile.write_text('' if mode == 'missing' else '1' if mode == 'bad-pid' else '42')
        def command(name, body):
            p = tmp / name
            p.write_text('#!/bin/sh\n' + body)
            p.chmod(0o755)
        command('id', 'echo ' + ('2000' if mode == 'denied' else '0'))
        command('pidof', 'test "$1" = com.android.systemui || exit 1\ncat "$TEST_PIDFILE"')
        command('date', 'echo 100')
        command('getconf', 'echo 100')
        command('sleep', 'true')
        command('cmd', '''echo "cmd $*" >> "$TEST_CALLS"
[ "$*" = "package query-services --components --user 0 -n com.android.systemui/com.android.systemui.SystemUIService" ] || exit 1
[ "$TEST_MODE" != unavailable ] || exit 1
if [ "$TEST_MODE" = changed-during-lookup ]; then echo 43 > "$TEST_PIDFILE"; fi
echo com.android.systemui/.SystemUIService
''')
        command('am', '''echo "am $*" >> "$TEST_CALLS"
case "$1" in
 force-stop)
  [ "$*" = "force-stop --user 0 com.android.systemui" ] || exit 1
  [ "$TEST_MODE" != stop-failed ] || exit 1
  echo "" > "$TEST_PIDFILE";;
 startservice)
  [ "$*" = "startservice --user 0 -n com.android.systemui/com.android.systemui.SystemUIService" ] || exit 1
  if [ "$TEST_MODE" = start-failed ]; then echo 'Error: service not found'; exit 0; fi
  if [ "$TEST_MODE" = start-exit-failed ]; then exit 1; fi
  [ "$TEST_MODE" = no-respawn ] || echo 43 > "$TEST_PIDFILE"
  echo 'Starting service';;
 *) exit 1;;
esac
''')
        result = subprocess.run(['/bin/sh', '-c', script.replace('/proc/', str(proc) + '/')], capture_output=True, text=True, timeout=5,
            env={**os.environ, 'PATH': str(tmp) + ':/usr/bin:/bin', 'TEST_MODE': mode,
                 'TEST_PIDFILE': str(pidfile), 'TEST_CALLS': str(calls),
                 'THUNDER_RESTART_CONFIRMED_ELAPSED': '' if mode == 'unconfirmed' else '100000',
                 'THUNDER_RESTART_EXPECTED_PID': '41' if mode == 'changed' else '42',
                 'THUNDER_RESTART_DEADLINE': '0' if mode == 'expired' else '200'})
        actions = calls.read_text().splitlines() if calls.exists() else []
        stop = 'am force-stop --user 0 com.android.systemui'
        start = 'am startservice --user 0 -n com.android.systemui/com.android.systemui.SystemUIService'
        assert actions.count(stop) <= 1, 'Never stop a replacement process or retry termination'
        if mode == 'managed':
            assert result.returncode == 0, result.stdout + result.stderr
            assert 'THUNDER_RESTART_OLD=42' in result.stdout and 'THUNDER_RESTART_NEW=43' in result.stdout
            assert actions[1:] == [stop, start], actions
        else:
            error = {'denied': 'ROOT_DENIED', 'expired': 'EXPIRED', 'missing': 'NOT_RUNNING',
                     'bad-pid': 'BAD_PID', 'changed': 'PROCESS_CHANGED',
                     'reused-pid': 'PROCESS_CHANGED', 'not-stable': 'NOT_STABLE',
                     'unconfirmed': 'UNCONFIRMED_PROCESS',
                     'changed-during-lookup': 'PROCESS_CHANGED', 'unavailable': 'SERVICE_UNAVAILABLE',
                     'stop-failed': 'STOP_FAILED', 'start-failed': 'START_FAILED',
                     'start-exit-failed': 'START_FAILED', 'no-respawn': 'NO_NEW_PROCESS'}[mode]
            assert result.returncode != 0 and 'THUNDER_RESTART_ERROR=' + error in result.stdout, result.stdout
            if mode not in {'stop-failed', 'start-failed', 'start-exit-failed', 'no-respawn'}:
                assert stop not in actions and start not in actions, actions
            if mode == 'stop-failed': assert start not in actions
        print('PASS:', mode)


for mode in ['managed', 'denied', 'expired', 'missing', 'bad-pid', 'changed', 'changed-during-lookup',
             'unconfirmed', 'reused-pid', 'not-stable', 'unavailable', 'stop-failed', 'start-failed', 'start-exit-failed', 'no-respawn']:
    run_case(mode)
