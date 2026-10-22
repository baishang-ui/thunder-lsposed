"""Run actual compiled motion/content helpers on a connected Android device, without installing or restarting SystemUI."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java-source-root', type=Path, help='Motion source snapshot for a baseline CPU comparison')
parser.add_argument('--benchmark-only', action='store_true')
parser.add_argument('--root-probe-install', action='store_true', help='Use an already-authorized rooted device to install the temporary test package')
args = parser.parse_args()
jdk = Path(os.environ.get('JAVA_HOME', root / '.local/work/android-tools/jdk-17.0.20.1+1/Contents/Home'))
sdk = Path(os.environ.get('ANDROID_HOME', root / '.local/work/android-sdk'))
android = sdk / 'platforms/android-37.0/android.jar'
production = root / 'app/build/intermediates/compile_app_classes_jar/debug/bundleDebugClassesToCompileJar/classes.jar'
# The compile class jar is produced for test consumers, not by assembleDebug.
# Build it explicitly so device pixels always exercise the current helper.
env = os.environ.copy()
env['JAVA_HOME'] = str(jdk)
subprocess.run([str(root / 'gradlew'), '--no-watch-fs', '--offline', ':app:bundleDebugClassesToCompileJar'],
               cwd=root, env=env, check=True, stdout=subprocess.DEVNULL)

package = 'io/github/baishangui/thunder/'
adb = str(sdk / 'platform-tools/adb')

with tempfile.TemporaryDirectory(prefix='thunder-motion-device-') as folder:
    folder = Path(folder)
    classes = folder / 'classes'
    classes.mkdir()
    source = root / 'app/src/main/java' if args.java_source_root is None else args.java_source_root
    sources = [source / package / 'CloudMotion.java', root / 'scripts/fixtures/cloud' / package / 'CloudMotionCheck.java',
               root / 'scripts/checks/cloud/CloudMotionDeviceCheck.java', root / 'scripts/checks/cloud/CloudCapsuleContentDeviceCheck.java',
               root / 'scripts/checks/cloud/CloudMotionInstrumentation.java']
    subprocess.run([str(jdk / 'bin/javac'), '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, [android, production])),
                    '-d', str(classes), *map(str, sources)], check=True)
    # Use the actual production Frame record, visibility, drawing and input transform.
    with zipfile.ZipFile(production) as archive:
        for name in ['CloudMorphHook', 'CloudMorphHook$Frame', 'CloudVisibilityState', 'CloudCardTiming', 'CloudContentTransform', 'CloudCapsuleContent']:
            archive.extract(package + name + '.class', classes)
    subprocess.run([str(jdk / 'bin/jar'), '--create', '--file', str(folder / 'classes.jar'), '-C', str(classes), '.'], check=True)
    subprocess.run([str(jdk / 'bin/java'), '-cp', str(sdk / 'build-tools/35.0.0/lib/d8.jar'), 'com.android.tools.r8.D8',
                    '--min-api', '37', '--lib', str(android), '--classpath', str(production), '--output', str(folder / 'probe.jar'),
                    str(folder / 'classes.jar')], check=True)
    remote = '/data/local/tmp/' + folder.name + '.jar'
    subprocess.run([adb, 'push', str(folder / 'probe.jar'), remote], check=True)
    try:
        command = ['env', 'CLASSPATH=' + remote, 'app_process', '-Xmx128m', '/system/bin', 'io.github.baishangui.thunder.CloudMotionDeviceCheck']
        if args.benchmark_only:
            command.append('--benchmark-only')
        subprocess.run([adb, 'shell', *command], check=True)
        if not args.benchmark_only:
            # Android 17 View initialization requires ApplicationSharedMemory,
            # supplied by a normal app process, not bare app_process. The
            # self-targeted instrumentation starts no foreground Activity.
            probe_package = 'io.github.baishangui.thunder.motionprobe'
            existing = subprocess.run([adb, 'shell', 'pm', 'path', probe_package], capture_output=True, text=True)
            if existing.stdout.strip():
                raise SystemExit('Temporary motion probe package already exists; refusing to replace it.')
            manifest = folder / 'AndroidManifest.xml'
            manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="' + probe_package + '">'
                                '<uses-sdk android:minSdkVersion="37" android:targetSdkVersion="37"/>'
                                '<application android:debuggable="true" android:label="Cloud motion probe"/>'
                                '<instrumentation android:name="io.github.baishangui.thunder.CloudMotionInstrumentation" '
                                'android:targetPackage="' + probe_package + '"/></manifest>')
            build_tools = sdk / 'build-tools/35.0.0'
            apk = folder / 'probe.apk'
            subprocess.run([str(build_tools / 'aapt2'), 'link', '-I', str(android), '--manifest', str(manifest), '-o', str(apk)], check=True)
            with zipfile.ZipFile(folder / 'probe.jar') as dex, zipfile.ZipFile(apk, 'a') as archive:
                for name in dex.namelist():
                    if name.endswith('.dex'):
                        archive.writestr(name, dex.read(name))
            env = os.environ.copy()
            env['JAVA_HOME'] = str(jdk)
            subprocess.run([str(build_tools / 'apksigner'), 'sign', '--ks', str(Path.home() / '.android/debug.keystore'),
                            '--ks-pass', 'pass:android', '--out', str(folder / 'signed.apk'), str(apk)], env=env, check=True)
            remote_apk = remote + '.apk'
            if args.root_probe_install:
                subprocess.run([adb, 'push', str(folder / 'signed.apk'), remote_apk], check=True)
                try:
                    subprocess.run([adb, 'shell', 'su', '-c', 'pm install -t ' + remote_apk], check=True, timeout=45)
                finally:
                    subprocess.run([adb, 'shell', 'rm', '-f', remote_apk], check=True)
            else:
                subprocess.run([adb, 'install', '--no-incremental', '-t', str(folder / 'signed.apk')], check=True, timeout=45)
            try:
                report = subprocess.run([adb, 'shell', 'am', 'instrument', '-w', '-r', probe_package +
                                         '/io.github.baishangui.thunder.CloudMotionInstrumentation'], capture_output=True, text=True, check=True, timeout=45)
                print(report.stdout, flush=True)
                if 'INSTRUMENTATION_CODE: -1' not in report.stdout or 'checks: PASS' not in report.stdout:
                    raise SystemExit('Native content instrumentation did not pass.')
            finally:
                subprocess.run([adb, 'uninstall', probe_package], check=True)
    finally:
        subprocess.run([adb, 'shell', 'rm', '-f', remote], check=True)
