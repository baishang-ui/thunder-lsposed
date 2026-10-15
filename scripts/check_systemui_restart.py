"""Exercise the explicit restart script with disposable processes, never the desktop UI."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile
import threading

ROOT = Path(__file__).resolve().parent.parent
asset = ROOT / 'app/src/main/assets/restart-systemui.sh'
assert asset.exists(), 'The explicit SystemUI restart action has no restart implementation'
script = asset.read_text()
assert 'reboot' not in script and 'zygote' not in script
assert script.count('kill -KILL') == 1, 'Allow one fallback only, never repeated termination'
# The automatic writer and Hook code must never acquire Root or terminate a process.
for source in [*(ROOT / 'app/src/main/java').rglob('*.java'),
               ROOT / 'app/src/main/kotlin/io/github/baishangui/thunder/SettingsWriter.kt',
               ROOT / 'app/src/main/kotlin/io/github/baishangui/thunder/RemoteSettings.kt']:
    text = source.read_text()
    assert 'ProcessBuilder(' not in text and 'SystemUiSettings.restart(' not in text, source



def run_case(mode):
    processes = []
    stop = threading.Event()
    with tempfile.TemporaryDirectory(prefix='thunder-restart-') as tmp:
        tmp = Path(tmp)
        pidfile = tmp / 'pid'
        def child(ignore):
            p = subprocess.Popen([sys.executable, '-u', '-c',
                'import signal,time;'
                + ('signal.signal(signal.SIGTERM,signal.SIG_IGN);' if ignore else '')
                + 'print("ready",flush=True);time.sleep(90)'], stdout=subprocess.PIPE, text=True)
            assert p.stdout.readline().strip() == 'ready'
            processes.append(p)
            return p
        initial = child(mode != 'term')
        unrelated = child(True)
        pidfile.write_text('' if mode == 'missing' else '1' if mode == 'bad-pid' else str(initial.pid))
        def command(name, body):
            path = tmp / name
            path.write_text('#!/bin/sh\n' + body)
            path.chmod(0o755)
        command('id', 'echo ' + ('2000' if mode == 'denied' else '0'))
        command('pidof', 'test "$1" = com.android.systemui || exit 1\ncat "' + str(pidfile) + '"')
        command('sleep', '/bin/sleep 0.05')
        def respawn():
            while not stop.wait(.005):
                if initial.poll() is not None:
                    if mode != 'no-respawn':
                        replacement = child(True)
                        pidfile.write_text(str(replacement.pid))
                    else:
                        pidfile.write_text('')
                    return
        watcher = threading.Thread(target=respawn)
        watcher.start()
        try:
            result = subprocess.run(['/bin/sh', '-c', script], capture_output=True, text=True, timeout=5,
                env={**os.environ, 'PATH': str(tmp) + ':/usr/bin:/bin', 'THUNDER_RESTART_DEADLINE': '0' if mode == 'expired' else '4102444800'})
            assert unrelated.poll() is None, 'unrelated process was terminated'
            if mode in ('term', 'fallback'):
                assert result.returncode == 0, result.stdout + result.stderr
                assert 'THUNDER_RESTART_OLD=' + str(initial.pid) in result.stdout
                assert 'THUNDER_RESTART_NEW=' + str(processes[-1].pid) in result.stdout
                assert initial.poll() is not None
                assert ('THUNDER_RESTART_FALLBACK' in result.stdout) == (mode == 'fallback')
            else:
                error = {'denied': 'ROOT_DENIED', 'missing': 'NOT_RUNNING', 'bad-pid': 'BAD_PID',
                         'no-respawn': 'NO_NEW_PROCESS', 'expired': 'EXPIRED'}[mode]
                assert result.returncode != 0 and 'THUNDER_RESTART_ERROR=' + error in result.stdout, result.stdout
                if mode != 'no-respawn':
                    assert initial.poll() is None
            print('PASS:', mode)
        finally:
            stop.set()
            watcher.join()
            for p in processes:
                if p.poll() is None:
                    p.kill()
                p.wait()

for mode in ['term', 'fallback', 'denied', 'missing', 'bad-pid', 'no-respawn', 'expired']:
    run_case(mode)
