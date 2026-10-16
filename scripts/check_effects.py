"""Run actual Java handlers using synthetic Android/Oplus stand-ins (JDK 17+).
This checks state, interpolation and package/feature guards, not device rendering.
"""
from pathlib import Path
import json
import re
import argparse
import os
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--only', nargs='+', help='Run only the named Java check classes')
parser.add_argument('--entries', action='store_true', help='Also check module package lifecycle routing with --only')
args = parser.parse_args()

ROOT = Path(__file__).resolve().parent.parent
required = {'com.android.systemui', 'com.oplus.systemui.plugins'}
manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
visible = {p.attrib['{http://schemas.android.com/apk/res/android}name'] for p in manifest.findall('./queries/package')}
assert required <= visible, 'SettingsProvider must be able to resolve both target package UIDs'
scope = set((ROOT / 'app/src/main/resources/META-INF/xposed/scope.list').read_text().splitlines())
assert scope == required, 'Only SystemUI and its plugin belong in the module scope'
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
    for fixture in ['statusbar', 'cloud', 'qs', 'text', 'iconstyle', 'gesture', 'volume']:
        for path in (ROOT / 'scripts/fixtures' / fixture).rglob('*.java'):
            target = src / path.relative_to(ROOT / 'scripts/fixtures' / fixture)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(path.read_text())
    for name in ['VolumeHook', 'VolumePanelGeometry', 'GestureBarHook', 'ShadeHook', 'QsCardHook', 'QsCornerHook', 'NotificationCardHook', 'AdaptiveTextColor', 'AdaptiveTextHook', 'LiveSettings', 'HookConfig', 'SettingsProvider', 'ResourceOverrides', 'Reflect', 'CloudHook', 'HookEntry', 'StatusBarIcons', 'StatusBarIconHook', 'IosStatusIcons', 'IosIconAssets', 'IosIconDrawable', 'IosStatusBarHook', 'IosStatusResources']:
        path = Path('io/github/baishangui/thunder') / (name + '.java')
        (src / path).write_text((ROOT / 'app/src/main/java' / path).read_text())
    # Match generated compiled-resource references without pretending to inflate Android XML.
    resource_names = list(dict.fromkeys(re.findall(r'R\.drawable\.(\w+)', (ROOT / 'app/src/main/java/io/github/baishangui/thunder/IosStatusResources.java').read_text())))
    (src / 'io/github/baishangui/thunder/R.java').write_text('package io.github.baishangui.thunder; final class R { static class drawable {' + ''.join(f'static final int {name}={index};' for index, name in enumerate(resource_names, 1000)) + '} }')
    subprocess.run([javac, '-d', str(classes), *map(str, src.rglob('*.java'))], check=True)
    checks = ['VolumePanelCheck', 'GestureBarCheck', 'CardBlendCheck', 'HookCheck', 'ProviderCheck', 'CloudCheck', 'CloudLayerCheck', 'StatusBarIconCheck', 'IosStatusBarCheck', 'IosDrawSafetyCheck', 'IosRenderPathCheck', 'IosCoverageCheck', 'QsTintCheck', 'QsStateCheck', 'QsGeometryCheck', 'NotificationStyleCheck', 'NotificationExpansionCheck', 'AdaptiveTextCheck']
    if args.only:
        unknown = set(args.only) - set(checks)
        if unknown: raise SystemExit('Unknown checks: ' + ', '.join(sorted(unknown)))
    for main in (args.only or checks):
        subprocess.run([java, '-cp', str(classes), 'io.github.baishangui.thunder.' + main], check=True)

    for scene in ([] if args.only and not args.entries else ['host', 'plugin', 'secondary', 'other', 'system', 'launcher']):
        subprocess.run([java, '-cp', str(classes), 'io.github.baishangui.thunder.EntryCheck', scene], check=True)
