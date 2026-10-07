"""Capture the real Tk interface with synthetic data; never starts a network worker."""
import argparse
from pathlib import Path
import sys
import tkinter as tk
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from dishwasher_gui import DishwasherApp, FIELDS

def main():
    from PIL import ImageGrab
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,default=ROOT/'docs/images')
    args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=True)
    root=tk.Tk()
    config={'device_id':123456,'ip_address':'192.0.2.7','port':6444}
    app=DishwasherApp(root,config,start_worker=False,config_path=args.output/'unused-demo-config.json')
    root.title('洗碗机面板 · 演示数据');root.geometry('1040x780+30+30');root.attributes('-topmost',True)
    values=dict.fromkeys(FIELDS,0)
    values.update(power=True,status='running',mode='strong_wash',door=False,water_lack=False,
                  storage=True,storage_status=False,storage_remaining=72,temperature=42,
                  progress='wash',time_remaining=38,child_lock=False,humidity=65,
                  uv=False,dry=False,dry_status=False,rinse_aid=False,salt=False)
    app.events.put(('snapshot',(values,{0:'neutral_gear',2:'strong_wash',3:'standard_wash'})))
    app.poll();root.update();root.after_cancel(app.poll_id)
    app.updated.set('更新于 10:30:00 · 演示数据')
    pages=iter(('home','status','control','device'))
    def capture():
        try:page=next(pages)
        except StopIteration:app.close();return
        app.show_page(page);root.update();root.lift()
        def save():
            root.update()
            ImageGrab.grab(window=root.winfo_id()).save(args.output/f'python-{page}.png')
            root.after(200,capture)
        root.after(700,save)
    root.after(500,capture);root.mainloop()

if __name__=='__main__':main()
