"""Run actual Java handlers using synthetic Android/Oplus stand-ins (JDK 17+).
This checks state, interpolation and package/feature guards, not device rendering.
"""
from pathlib import Path
import json
import os
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
required = {'com.android.systemui', 'com.oplus.systemui.plugins'}
manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
visible = {p.attrib['{http://schemas.android.com/apk/res/android}name'] for p in manifest.findall('./queries/package')}
assert required <= visible, 'SettingsProvider must be able to resolve both target package UIDs'
scope = set((ROOT / 'app/src/main/resources/META-INF/xposed/scope.list').read_text().splitlines())
assert scope == required, 'Both target packages must be recommended to LSPosed'
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
    for path in (ROOT / 'scripts/fixtures/statusbar').rglob('*.java'):
        target = src / path.relative_to(ROOT / 'scripts/fixtures/statusbar')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(path.read_text())
    for name in ['ShadeHook', 'HookConfig', 'SettingsProvider', 'ResourceOverrides', 'Reflect', 'CloudHook', 'HookEntry', 'StatusBarIcons', 'StatusBarIconHook']:
        path = Path('io/github/baishangui/thunder') / (name + '.java')
        (src / path).write_text((ROOT / 'app/src/main/java' / path).read_text())
    subprocess.run([javac, '-d', str(classes), *map(str, src.rglob('*.java'))], check=True)
    for main in ['HookCheck', 'ProviderCheck', 'CloudCheck', 'StatusBarIconCheck']:
        subprocess.run([java, '-cp', str(classes), 'io.github.baishangui.thunder.' + main], check=True)

    for scene in ['host', 'plugin', 'secondary', 'other']:
        subprocess.run([java, '-cp', str(classes), 'io.github.baishangui.thunder.EntryCheck', scene], check=True)
