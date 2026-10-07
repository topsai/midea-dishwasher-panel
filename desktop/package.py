"""Build desktop release artifacts without collecting private configuration."""
import argparse
from importlib.metadata import distributions
from pathlib import Path
import subprocess
import sys
import zipfile

VERSION='1.1.0'
ROOT=Path(__file__).resolve().parents[1]
MODULES=['dishwasher_gui.py','dishwasher_settings.py','dishwasher_pairing.py','dishwasher_runtime.py']

def archive(path,files):
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as z:
        for source,name in files:z.write(source,name)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,default=ROOT/'desktop/dist')
    args=parser.parse_args()
    if sys.platform!='win32' or sys.maxsize<=2**32:
        parser.error('Windows x64 is required for the Windows executable build')
    output=args.output.resolve();output.mkdir(parents=True,exist_ok=True)
    build=ROOT/'desktop/build';dist=ROOT/'desktop/dist/bin'
    subprocess.run([sys.executable,'-m','PyInstaller','--noconfirm','--onefile','--windowed',
                    '--name','DishwasherPanel','--distpath',str(dist),'--workpath',str(build),
                    '--specpath',str(ROOT/'desktop'),'--collect-submodules','midealan',
                    '--collect-all','Crypto',str(ROOT/'dishwasher_gui.py')],cwd=ROOT,check=True)
    exe=dist/'DishwasherPanel.exe'
    license_path=ROOT/'android/app/src/main/assets/midea-lan-LICENSE.txt'
    common=[(ROOT/'desktop/README.md','README.md'),(license_path,'midea-lan-LICENSE.txt')]
    for distribution in distributions():
        name=distribution.metadata['Name']
        for file in distribution.files or []:
            if '.dist-info' in str(file) and (file.name.lower().startswith(('license','copying','copyright')) or 'licenses' in file.parts):
                path=Path(distribution.locate_file(file))
                if path.is_file():common.append((path,f'licenses/{name}/{file.as_posix().split(".dist-info/",1)[-1]}'))
    for filename in ['LICENSE.txt','Lib/LICENSE.txt','tcl/license.terms']:
        path=Path(sys.base_prefix)/filename
        if path.is_file():common.append((path,'licenses/Python/'+filename.replace('/','_')))
    archive(output/f'dishwasher-python-windows-x64-v{VERSION}.zip',[(exe,'DishwasherPanel.exe')]+common)
    source_names=MODULES+['requirements.txt','setup_environment.bat','start_panel.bat',
                        'read_temperature.py','read_temperature.bat','dishwasher.example.json']
    archive(output/f'dishwasher-python-source-v{VERSION}.zip',[(ROOT/name,name) for name in source_names]+common)
    print('Created Windows executable and Python source release packages:',output)

if __name__=='__main__':main()
