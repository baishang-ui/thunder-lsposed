"""Validate Gradle's APK metadata against the release tag before signing."""
import json
import os
from pathlib import Path
import re
import sys
import zipfile

metadata_path = Path(sys.argv[1])
tag = sys.argv[2]
metadata = json.loads(metadata_path.read_text())
assert metadata['applicationId'] == 'io.github.baishangui.thunder', 'Unexpected application ID'
elements = metadata['elements']
assert len(elements) == 1, 'Expected one universal APK'
element = elements[0]
version = element['versionName']
assert re.fullmatch(r'\d+\.\d+(?:\.\d+)?(?:-[A-Za-z0-9.-]+)?', version), 'Invalid version'
assert tag == f'v{version}', f'Tag {tag} does not match APK version v{version}'
assert isinstance(element['versionCode'], int) and element['versionCode'] > 0
apk = metadata_path.parent / element['outputFile']
assert apk.is_file(), 'Built APK is missing'
with zipfile.ZipFile(apk) as archive:
    scope = set(archive.read('META-INF/xposed/scope.list').decode().splitlines())
    expected_scope = {'com.android.systemui', 'com.oplus.systemui.plugins', 'com.android.settings'}
    assert scope == expected_scope, (
        f'Unexpected APK scopes: missing={sorted(expected_scope - scope)}, '
        f'unexpected={sorted(scope - expected_scope)}'
    )
    entry = archive.read('META-INF/xposed/java_init.list').decode().strip()
    assert entry == 'io.github.baishangui.thunder.HookEntry', 'Unexpected module entry'
values = {'version': version, 'version_code': str(element['versionCode']),
          'apk': str(apk), 'asset': f'thunder-{tag}.apk',
          'prerelease': str('-' in version).lower()}
if output := os.environ.get('GITHUB_OUTPUT'):
    with open(output, 'a') as stream:
        for key, value in values.items():
            stream.write(f'{key}={value}\n')
print(f'Verified thunder {version} ({element["versionCode"]}), tag {tag}')
