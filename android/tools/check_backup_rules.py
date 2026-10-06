"""Verify backup/transfer policy in the built APK, not merely the source XML."""
import argparse, subprocess
p=argparse.ArgumentParser();p.add_argument('--apk',required=True);p.add_argument('--aapt2',required=True);a=p.parse_args()
def dump(file):return subprocess.check_output([a.aapt2,'dump','xmltree',a.apk,'--file',file]).decode()
manifest=dump('AndroidManifest.xml')
assert 'dataExtractionRules' in manifest, 'APK missing Android 12 transfer policy'
rules=dump('res/xml/data_extraction_rules.xml')
assert 'cloud-backup' in rules and 'device-transfer' in rules
assert rules.count('domain="file"')==2 and rules.count('domain="root"')==2
assert rules.count('path="."')==8, 'Private data must be excluded from both backup routes'
print('PASS: APK excludes private configuration from cloud backup and device transfer')
