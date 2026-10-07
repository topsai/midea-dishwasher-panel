"""Scan tracked/staged source and decompressed APK for actual local pairing material."""
import argparse,json,subprocess,zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--config',required=True);p.add_argument('--apk',required=True);a=p.parse_args()
c=json.loads(Path(a.config).read_text(encoding='utf-8-sig'));patterns=[]
for name in ('token','key'):
    v=c[name];patterns.extend((v.lower().encode(),v.upper().encode(),v.lower().encode('utf-16le'),bytes.fromhex(v)))
def check(data,label):
    if any(pattern in data for pattern in patterns):raise SystemExit('Pairing material found in '+label)
for path in subprocess.check_output(['git','ls-files','-z']).decode().split(chr(0)):
    if path and Path(path).is_file():check(Path(path).read_bytes(),path)
check(subprocess.check_output(['git','diff','--cached','--binary']),'staged diff')
with zipfile.ZipFile(a.apk) as z:
    for name in z.namelist():check(z.read(name),'APK:'+name)
print('PASS: tracked source, staged diff and decompressed APK contain no actual Token/Key')
