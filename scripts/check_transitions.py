"""Run the production system_server adapter against captured firmware API shapes."""
from pathlib import Path
import json, os, subprocess, tempfile
ROOT = Path(__file__).resolve().parent.parent
jdk = Path(os.environ['JAVA_HOME']) / 'bin'
sources = json.loads((ROOT / 'scripts/fixtures/effect-runtime.json').read_text())
with tempfile.TemporaryDirectory(prefix='thunder-transitions-') as folder:
    src = Path(folder) / 'src'
    classes = Path(folder) / 'classes'
    classes.mkdir()
    # Minimal dependencies; never use a fake transition implementation.
    prefixes = ('android/',)
    selected = {k: v for k, v in sources.items() if k.startswith(prefixes) and not k.startswith('android/view/')}
    for name in ['Api102Runtime', 'HookCallback', 'BuildConfig']:
        key = f'io/github/baishangui/thunder/{name}.java'
        selected[key] = sources[key]
    selected['io/github/baishangui/thunder/SettingsProvider.java'] = 'package io.github.baishangui.thunder;class SettingsProvider {static final String AUTHORITY="test.settings";}'
    for key, value in selected.items():
        path = src / key
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value)
    for path in (ROOT / 'scripts/fixtures/transitions').rglob('*.java'):
        if path.name == 'LauncherProviderCheck.java': continue
        dest = src / path.relative_to(ROOT / 'scripts/fixtures/transitions')
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(path.read_text())
    # Make replacement observable independently from the original result being void.
    callback = src / 'io/github/baishangui/thunder/HookCallback.java'
    callback.write_text(callback.read_text().replace('result=v;', 'result=v;extras.put("replaced",true);'))
    for name in ['StatusBarFollowHook', 'StatusBarTransition', 'SystemProviderCall', 'StatusBarOverlayIpc', 'LauncherStatusBarHook', 'StatusBarFrameGeometry', 'Reflect', 'LiveSettings']:
        rel = Path(f'io/github/baishangui/thunder/{name}.java')
        (src / rel).write_text((ROOT / 'app/src/main/java' / rel).read_text())
    subprocess.run([str(jdk / 'javac'), '-d', str(classes), *map(str, src.rglob('*.java'))], check=True)
    subprocess.run([str(jdk / 'java'), '-cp', str(classes), 'io.github.baishangui.thunder.StatusBarFollowCheck'], check=True)
    subprocess.run([str(jdk / 'java'), '-cp', str(classes), 'io.github.baishangui.thunder.LauncherOverlayCheck'], check=True)
    for scene in ('coalesce', 'retry', 'retry-limit'):
        subprocess.run([str(jdk / 'java'), '-cp', str(classes), 'io.github.baishangui.thunder.StatusBarReportCheck', scene], check=True)
