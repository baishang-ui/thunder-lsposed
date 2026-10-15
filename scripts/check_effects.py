"""Run actual Java handlers using synthetic Android/Oplus stand-ins (JDK 17+).
This checks state, interpolation and package/feature guards, not device rendering.
"""
from pathlib import Path
import json
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
sources = json.loads((ROOT / 'scripts/fixtures/effect-runtime.json').read_text())
java_home = os.environ.get('JAVA_HOME')
java = str(Path(java_home) / 'bin/java') if java_home else shutil.which('java')
javac = str(Path(java_home) / 'bin/javac') if java_home else shutil.which('javac')
if not java or not javac:
    raise SystemExit('Set JAVA_HOME to JDK 17 or newer.')
with tempfile.TemporaryDirectory(prefix='thunder-effects-') as folder:
    folder = Path(folder)
    src, classes = folder / 'src', folder / 'classes'
    classes.mkdir()
    for name, data in sources.items():
        path = src / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(data)
    for name in ['ShadeHook', 'HookConfig', 'SettingsProvider', 'ResourceOverrides', 'Reflect', 'CloudHook', 'HookEntry']:
        path = Path('io/github/baishangui/thunder') / (name + '.java')
        (src / path).write_text((ROOT / 'app/src/main/java' / path).read_text())
    subprocess.run([javac, '-d', str(classes), *map(str, src.rglob('*.java'))], check=True)
    for main in ['HookCheck', 'ProviderCheck', 'CloudCheck']:
        subprocess.run([java, '-cp', str(classes), 'io.github.baishangui.thunder.' + main], check=True)

    for scene in ['host', 'plugin', 'secondary', 'other']:
        subprocess.run([java, '-cp', str(classes), 'io.github.baishangui.thunder.EntryCheck', scene], check=True)
