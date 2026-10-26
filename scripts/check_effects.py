"""Run actual Java handlers using synthetic Android/Oplus stand-ins (JDK 17+).
This checks state, interpolation and package/feature guards, not device rendering.
"""
from pathlib import Path
import json
import re
import argparse
import struct
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
    for fixture in ['statusbar', 'cloud', 'qs', 'text', 'iconstyle', 'gesture', 'volume', 'report', 'performance', 'cloudnative']:
        for path in (ROOT / 'scripts/fixtures' / fixture).rglob('*.java'):
            target = src / path.relative_to(ROOT / 'scripts/fixtures' / fixture)
            if target.exists():
                raise SystemExit('Duplicate runtime fixture: ' + str(target.relative_to(src)))
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(path.read_text(encoding='utf-8'), encoding='utf-8')
    for name in ['CloudCardFeedbackCheck', 'CloudContentSwitchCheck', 'CloudTextTransitionCheck', 'CloudContentTransformCheck', 'CloudMaterialBlendCheck', 'CloudNativeMaterialCheck', 'CloudNativeDecorationCheck', 'CloudSurfacePolicyCheck', 'CloudVisibilityStateCheck', 'CloudInputWindowPolicyCheck', 'CloudInputOwnershipCheck', 'CloudCardRosterCheck']:
        path = Path('io/github/baishangui/thunder') / (name + '.java')
        (src / path).write_text((ROOT / 'scripts/checks/cloud' / (name + '.java')).read_text(encoding='utf-8'), encoding='utf-8')
    for name in ['StatusBarLayoutHook', 'StatusBarClockHook', 'StatusBarClockTransitionHook', 'VolumeHook', 'VolumeShortcutFeedback', 'VolumeLiquidEntrance', 'VolumePanelGeometry', 'GestureBarHook', 'PanelSaturationHook', 'ShadeHook', 'QsCardHook', 'QsCornerHook', 'NotificationCardHook', 'AdaptiveTextColor', 'AdaptiveTextHook', 'LiveSettings', 'HookConfig', 'SettingsProvider', 'ResourceOverrides', 'Reflect', 'CloudHook', 'CloudMorphHook', 'CloudCardFeedback', 'CloudMotion', 'CloudContentSwitch', 'CloudCardTiming', 'CloudCardRoster', 'CloudTextTransition', 'CloudContentTransform', 'CloudMaterialBlend', 'CloudNativeMaterial', 'CloudNativeDecoration', 'CloudSurfacePolicy', 'CloudVisibilityState', 'CloudInputWindowPolicy', 'CloudInputOwnership', 'CloudCornerGeometry', 'CloudLockHook', 'CloudSdkHook', 'CloudTemplateHook', 'CloudTextHook', 'HookEntry', 'StatusBarIcons', 'StatusBarIconHook', 'IosStatusIcons', 'IosStatusBarHook', 'IosStatusResources', 'IosBatteryResources']:
        path = Path('io/github/baishangui/thunder') / (name + '.java')
        (src / path).write_text((JAVA_SOURCE / path).read_text(encoding='utf-8'), encoding='utf-8')
    # Match generated compiled-resource references without pretending to inflate Android XML.
    resource_names = list(dict.fromkeys(re.findall(r'R\.drawable\.(\w+)', (ROOT / 'app/src/main/java/io/github/baishangui/thunder/IosStatusResources.java').read_text())))
    (src / 'io/github/baishangui/thunder/R.java').write_text('package io.github.baishangui.thunder; final class R { static class drawable {' + ''.join(f'static final int {name}={index};' for index, name in enumerate(resource_names, 1000)) + '} }')
    # Keep the Windows command line below CreateProcess's limit as fixtures grow.
    compile_args = folder / 'javac.args'
    compile_args.write_text('\n'.join('"' + p.as_posix() + '"' for p in src.rglob('*.java')), encoding='utf-8')
    subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(classes), '@' + str(compile_args)], check=True)
    # javac cannot declare bridge-only Kotlin/ART forwarders in source. Preserve
    # their actual bytecode while marking the fixture with the firmware's flags.
    bridge_class = classes / 'io/github/baishangui/thunder/ReflectCheck$BridgeOnlyRow.class'
    bytecode = bytearray(bridge_class.read_bytes())
    def u2(offset): return struct.unpack_from('>H', bytecode, offset)[0]
    def u4(offset): return struct.unpack_from('>I', bytecode, offset)[0]
    pos, index, utf8 = 10, 1, {}
    while index < u2(8):
        tag = bytecode[pos]; pos += 1
        if tag == 1:
            length = u2(pos); utf8[index] = bytecode[pos+2:pos+2+length].decode(); pos += 2+length
        elif tag in (5, 6): pos += 8; index += 1
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18): pos += 4
        elif tag in (7, 8, 16, 19, 20): pos += 2
        elif tag == 15: pos += 3
        else: raise AssertionError(f'Unexpected class constant {tag}')
        index += 1
    pos += 6
    pos += 2 + u2(pos)*2
    for section in ('fields', 'methods'):
        count = u2(pos); pos += 2
        for _ in range(count):
            if section == 'methods' and utf8[u2(pos+2)] != '<init>':
                struct.pack_into('>H', bytecode, pos, u2(pos) | 0x1040)
            attributes = u2(pos+6); pos += 8
            for _ in range(attributes): pos += 6 + u4(pos+2)
    bridge_class.write_bytes(bytecode)
    checks = ['StatusBarLayoutCheck', 'StatusBarClockTransitionCheck', 'ReflectCheck', 'AdaptiveTextPerformanceCheck', 'RuntimeReportCheck', 'ProviderVisibilityCheck', 'SettingsUiProviderCheck', 'VolumePanelCheck', 'VolumeLiquidEntranceCheck', 'GestureBarCheck', 'CardBlendCheck', 'PanelStartFrameCheck', 'HookCheck', 'ProviderCheck', 'CloudCheck', 'CloudLayerCheck', 'CloudLockCheck', 'CloudSdkCheck', 'CloudTemplateCheck', 'StatusBarIconCheck', 'IosStatusBarCheck', 'IosDrawSafetyCheck', 'IosRenderPathCheck', 'IosCoverageCheck', 'QsTintCheck', 'QsStateCheck', 'QsGeometryCheck', 'QsMediaGeometryCheck', 'NotificationStyleCheck', 'NotificationExpansionCheck', 'AdaptiveTextCheck']
    checks.extend(['CloudTextHookCheck', 'StyleScopeCheck', 'PanelSaturationCheck', 'PanelColorContinuityCheck', 'ShortcutPerformanceCheck', 'CloudGeometryCheck', 'CloudNotificationGeometryCheck', 'CloudTemplateReentryCheck', 'CloudCurveCheck', 'CloudCapsuleIsolationCheck', 'CloudCapsuleCornerCheck', 'CloudCardRosterCheck', 'CloudMorphCheck', 'CloudDismissalCheck', 'CloudStackCheck', 'CloudMotionCheck', 'CloudCardFeedbackCheck', 'CloudContentSwitchCheck', 'CloudTextTransitionCheck', 'CloudContentTransformCheck', 'CloudMaterialBlendCheck', 'CloudNativeMaterialCheck', 'CloudNativeDecorationCheck', 'CloudSurfacePolicyCheck', 'CloudVisibilityStateCheck', 'CloudInputWindowPolicyCheck', 'CloudInputOwnershipCheck'])
    if args.only:
        unknown = set(args.only) - set(checks)
        if unknown: raise SystemExit('Unknown checks: ' + ', '.join(sorted(unknown)))
    for main in (args.only or checks):
        subprocess.run([java, '-cp', str(classes), 'io.github.baishangui.thunder.' + main], check=True)

    if args.benchmark:
        subprocess.run([java, '-cp', str(classes), 'io.github.baishangui.thunder.HookPerformanceCheck'], check=True)
        subprocess.run([java, '-cp', str(classes), 'io.github.baishangui.thunder.CloudCardRosterPerformanceCheck'], check=True)
        subprocess.run([java, '-cp', str(classes), 'io.github.baishangui.thunder.CloudMotionPerformanceCheck'], check=True)

    for scene in ([] if args.only and not args.entries else ['host', 'plugin', 'secondary', 'other', 'system', 'launcher']):
        subprocess.run([java, '-cp', str(classes), 'io.github.baishangui.thunder.EntryCheck', scene], check=True)
