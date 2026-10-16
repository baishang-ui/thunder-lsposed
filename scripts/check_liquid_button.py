"""Check the compiled glass button contract against Backdrop 1.0.6 lens restrictions.

Run after :app:compileReleaseKotlin. javap inspects bytecode without loading the
Android UI dependencies, so this check also works with the project's JDK 17.
"""
from pathlib import Path
import os
import re
import shutil
import subprocess

root = Path(__file__).resolve().parent.parent
java_home = os.environ.get("JAVA_HOME")
javap = str(Path(java_home) / "bin/javap") if java_home else shutil.which("javap")
assert javap, "Set JAVA_HOME to JDK 17 or newer"
button = root / "app/build/tmp/kotlin-classes/release/com/kyant/backdrop/catalog/components/LiquidButtonKt.class"
assert button.is_file(), "Run :app:compileReleaseKotlin first"
signature = subprocess.check_output([javap, "-s", str(button)], text=True)
descriptor = re.search(r"LiquidButton[^\n]*\n\s+descriptor: ([^\n]+)", signature)
assert descriptor, "Cannot find the compiled LiquidButton signature"
assert "Lcom/kyant/shapes/RoundedRectangularShape;" in descriptor[1], (
    "LiquidButton accepts unsupported Shape values: Backdrop lens() throws on "
    "Miuix smooth shapes at first draw; restrict the parameter to RoundedRectangularShape"
)
print("LiquidButton rejects shapes unsupported by Backdrop lens()")
