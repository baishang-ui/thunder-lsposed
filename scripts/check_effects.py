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
parser.add_argument('--benchmark', action='store_true', help='Measure warmed hook hot paths on the host JVM (not device FPS)')
parser.add_argument('--entries', action='store_true', help='Also check module package lifecycle routing with --only')
parser.add_argument('--java-source-root', type=Path, help='Production Java source snapshot for baseline comparisons')
args = parser.parse_args()

ROOT = Path(__file__).resolve().parent.parent
JAVA_SOURCE = args.java_source_root or ROOT / 'app/src/main/java'
required = {'com.android.systemui', 'com.oplus.systemui.plugins', 'com.android.settings'}
manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
visible = {p.attrib['{http://schemas.android.com/apk/res/android}name'] for p in manifest.findall('./queries/package')}
assert required <= visible, 'SettingsProvider must be able to resolve all target package UIDs'
provider = manifest.find('./application/provider')
assert provider.attrib.get('{http://schemas.android.com/apk/res/android}grantUriPermissions') == 'true', 'SettingsProvider must allow explicit URI grants to the scope packages'
scope = set((ROOT / 'app/src/main/resources/META-INF/xposed/scope.list').read_text().splitlines())
assert scope == required, 'SystemUI, its plugin and Settings must be the exact module scope'
sources = json.loads((ROOT / 'scripts/fixtures/effect-runtime.json').read_text(encoding='utf-8'))
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
        path.write_text(data, encoding='utf-8')
    for fixture in ['statusbar', 'cloud', 'qs', 'text', 'iconstyle', 'gesture', 'volume', 'report', 'performance']:
        for path in (ROOT / 'scripts/fixtures' / fixture).rglob('*.java'):
            target = src / path.relative_to(ROOT / 'scripts/fixtures' / fixture)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(path.read_text(encoding='utf-8'), encoding='utf-8')
    for name in ['StatusBarLayoutHook', 'StatusBarClockHook', 'StatusBarClockTransitionHook', 'VolumeHook', 'VolumeShortcutFeedback', 'VolumeLiquidEntrance', 'VolumePanelGeometry', 'GestureBarHook', 'PanelSaturationHook', 'ShadeHook', 'QsCardHook', 'QsCornerHook', 'NotificationCardHook', 'AdaptiveTextColor', 'AdaptiveTextHook', 'LiveSettings', 'HookConfig', 'SettingsProvider', 'ResourceOverrides', 'Reflect', 'CloudHook', 'CloudLockHook', 'CloudSdkHook', 'CloudTemplateHook', 'HookEntry', 'StatusBarIcons', 'StatusBarIconHook', 'IosStatusIcons', 'IosStatusBarHook', 'IosStatusResources', 'IosBatteryResources']:
        path = Path('io/github/baishangui/thunder') / (name + '.java')
        (src / path).write_text((JAVA_SOURCE / path).read_text(encoding='utf-8'), encoding='utf-8')
    # Match generated compiled-resource references without pretending to inflate Android XML.
    resource_names = list(dict.fromkeys(re.findall(r'R\.drawable\.(\w+)', (ROOT / 'app/src/main/java/io/github/baishangui/thunder/IosStatusResources.java').read_text())))
    (src / 'io/github/baishangui/thunder/R.java').write_text('package io.github.baishangui.thunder; final class R { static class drawable {' + ''.join(f'static final int {name}={index};' for index, name in enumerate(resource_names, 1000)) + '} }')
    # Keep the Windows command line below CreateProcess's limit as fixtures grow.
    compile_args = folder / 'javac.args'
    compile_args.write_text('\n'.join('"' + p.as_posix() + '"' for p in src.rglob('*.java')), encoding='utf-8')
    subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(classes), '@' + str(compile_args)], check=True)
    checks = ['StatusBarLayoutCheck', 'StatusBarClockTransitionCheck', 'ReflectCheck', 'AdaptiveTextPerformanceCheck', 'RuntimeReportCheck', 'ProviderVisibilityCheck', 'SettingsUiProviderCheck', 'VolumePanelCheck', 'VolumeLiquidEntranceCheck', 'GestureBarCheck', 'CardBlendCheck', 'PanelStartFrameCheck', 'HookCheck', 'ProviderCheck', 'CloudCheck', 'CloudLayerCheck', 'CloudLockCheck', 'CloudSdkCheck', 'CloudTemplateCheck', 'StatusBarIconCheck', 'IosStatusBarCheck', 'IosDrawSafetyCheck', 'IosRenderPathCheck', 'IosCoverageCheck', 'QsTintCheck', 'QsStateCheck', 'QsGeometryCheck', 'NotificationStyleCheck', 'NotificationExpansionCheck', 'AdaptiveTextCheck']
    checks.extend(['PanelSaturationCheck', 'PanelColorContinuityCheck', 'ShortcutPerformanceCheck', 'CloudGeometryCheck', 'CloudNotificationGeometryCheck'])
    if args.only:
        unknown = set(args.only) - set(checks)
        if unknown: raise SystemExit('Unknown checks: ' + ', '.join(sorted(unknown)))
    for main in (args.only or checks):
        subprocess.run([java, '-cp', str(classes), 'io.github.baishangui.thunder.' + main], check=True)

    if args.benchmark:
        subprocess.run([java, '-cp', str(classes), 'io.github.baishangui.thunder.HookPerformanceCheck'], check=True)

    for scene in ([] if args.only and not args.entries else ['host', 'plugin', 'secondary', 'other', 'system', 'launcher']):
        subprocess.run([java, '-cp', str(classes), 'io.github.baishangui.thunder.EntryCheck', scene], check=True)
