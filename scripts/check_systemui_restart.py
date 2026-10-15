"""Run the app's real shell command against disposable processes, never the desktop UI."""
from pathlib import Path
import os, re, subprocess, sys, tempfile, threading, time

ROOT = Path(__file__).resolve().parent.parent
asset = ROOT/'app/src/main/assets/restart-systemui.sh'
if asset.exists():
    script = asset.read_text()
else:
    source = (ROOT/'app/src/main/kotlin/io/github/baishangui/thunder/MainActivity.kt').read_text()
    script = re.search(r'ProcessBuilder\("su","-c","(.*?)"\)', source).group(1)
    script = script.replace('\\$', '$').replace('\\"', '"')

def child():
    p = subprocess.Popen([sys.executable, '-u', '-c',
        'import signal,time;signal.signal(signal.SIGTERM,signal.SIG_IGN);print("ready",flush=True);time.sleep(90)'],
        stdout=subprocess.PIPE, text=True)
    assert p.stdout.readline().strip() == 'ready'
    return p

def run_case(mode):
    processes = []
    stop = threading.Event()
    with tempfile.TemporaryDirectory(prefix='thunder-restart-check-') as tmp:
        tmp = Path(tmp); pidfile=tmp/'pid'; killfile=tmp/'killed'
        initial = child(); unrelated = child(); processes.extend([initial, unrelated])
        pidfile.write_text(str(initial.pid) if mode != 'missing' else '')
        def command(name, text):
            path=tmp/name;path.write_text('#!/bin/sh\n'+text);path.chmod(0o755)
        command('id', 'echo '+('2000' if mode == 'denied' else '0')+'\n')
        command('pidof', 'test "$1" = com.android.systemui || exit 1\ncat "'+str(pidfile)+'"\n')
        command('sleep', '/bin/sleep 0.05\n')
        def respawn():
            while not stop.wait(.01):
                if initial.poll() is not None:
                    killfile.touch()
                    if mode == 'normal':
                        replacement = child();processes.append(replacement);pidfile.write_text(str(replacement.pid))
                    else:
                        pidfile.write_text('')
                    return
        watcher=threading.Thread(target=respawn);watcher.start()
        try:
            result=subprocess.run(['/bin/sh','-c',script],env={**os.environ,'PATH':str(tmp)+':/usr/bin:/bin'},
                capture_output=True,text=True,timeout=5)
            assert unrelated.poll() is None, 'unrelated process was terminated'
            if mode == 'normal':
                assert initial.poll() is not None, 'BUG: TERM returned success while SystemUI process stayed alive'
                assert result.returncode == 0, result.stdout+result.stderr
                assert 'THUNDER_RESTART_OLD='+str(initial.pid) in result.stdout
                assert len(processes) == 3 and 'THUNDER_RESTART_NEW='+str(processes[-1].pid) in result.stdout
            elif mode == 'denied':
                assert result.returncode != 0 and 'THUNDER_RESTART_ERROR=ROOT_DENIED' in result.stdout
                assert initial.poll() is None
            elif mode == 'missing':
                assert result.returncode != 0 and 'THUNDER_RESTART_ERROR=NOT_RUNNING' in result.stdout
                assert initial.poll() is None
            else:
                assert result.returncode != 0 and 'THUNDER_RESTART_ERROR=NO_NEW_PROCESS' in result.stdout
            print('PASS:',mode,'real process termination / verified replacement / bounded error')
        finally:
            stop.set();watcher.join()
            for p in processes:
                if p.poll() is None:p.kill()
                p.wait()

for mode in ['normal','denied','missing','no-respawn']:run_case(mode)
