"""Prevent configuration changes from terminating SystemUI and consuming LSPosed's crash budget."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent
sources = list((ROOT / 'app/src/main').rglob('*.kt'))
sources += list((ROOT / 'app/src/main').rglob('*.java'))
sources += list((ROOT / 'app/src/main/assets').glob('*.sh'))
for source in sources:
    text = source.read_text()
    assert not re.search(r'ProcessBuilder\s*\(|\bkill(?:all)?\b|\bpkill\b|\bam\s+(?:crash|force-stop)\b', text), (
        f'{source.relative_to(ROOT)} still launches a shell or terminates a process; '
        'manual SystemUI termination consumes LSPosed safe-mode counters'
    )
print('PASS: applying settings cannot launch a shell or terminate SystemUI')
