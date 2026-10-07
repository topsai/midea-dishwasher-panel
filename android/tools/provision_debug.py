"""Import local JSON through stdin into a debuggable app; never print credentials."""
import argparse,subprocess,json
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--config',required=True);a=p.parse_args()
data=Path(a.config).read_bytes();json.loads(data.decode('utf-8-sig'))
def adb(*args,**kw):return subprocess.run([a.adb,*args],check=True,**kw)
adb('shell','am','force-stop','com.topsai.dishwasher')
adb('shell','run-as','com.topsai.dishwasher','mkdir','-p','files')
adb('shell','run-as com.topsai.dishwasher sh -c "cat > files/bootstrap.json"',input=data)
adb('shell','am','start','-n','com.topsai.dishwasher/.ui.MainActivity',stdout=subprocess.DEVNULL)
print('Pairing JSON imported privately; app encrypts it and deletes bootstrap.json on startup')
