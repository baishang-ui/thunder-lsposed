"""Execute the production scoped TextView renderer on isolated Android stand-ins, not device graphics."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
java_home = os.environ.get('JAVA_HOME')
javac = str(Path(java_home) / 'bin/javac') if java_home else shutil.which('javac')
java = str(Path(java_home) / 'bin/java') if java_home else shutil.which('java')
if not javac or not java:
    raise SystemExit('Set JAVA_HOME to JDK 17 or newer.')
production = root / 'app/src/main/java/io/github/baishangui/thunder'
with tempfile.TemporaryDirectory(prefix='thunder-cloud-text-') as work:
    work = Path(work)
    sources = list((root / 'scripts/fixtures/cloudtext').rglob('*.java'))
    sources += [production / (name + '.java') for name in ('HookCallback', 'CloudTextRenderer', 'CloudTextTransition')]
    sources += [root / 'scripts/checks/cloud/CloudTextRendererCheck.java']
    args = work / 'javac.args'
    args.write_text('\n'.join('"' + str(path) + '"' for path in sources))
    subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(work), '@' + str(args)], check=True)
    subprocess.run([java, '-cp', str(work), 'io.github.baishangui.thunder.CloudTextRendererCheck'], check=True)
