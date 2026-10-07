"""Desktop device settings. All network/file work runs outside the Tk thread."""
import asyncio
import threading
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import dishwasher_pairing as pairing

class DeviceSettings:
    def __init__(self, app, parent, config_path):
        self.app, self.config_path = app, config_path
        self.busy=False
        self.target=None
        self.buttons=[]
        self.info=tk.StringVar()
        self.status=tk.StringVar(value='选择已配置设备或搜索洗碗机；美居密码只用于本次登录。')
        card=ttk.LabelFrame(parent,text='连接与配对',padding=12);card.pack(fill='x')
        ttk.Label(card,textvariable=self.info).pack(anchor='w')
        row=ttk.Frame(card);row.pack(fill='x',pady=4)
        self.ip=tk.StringVar(value=app.config.get('ip_address',''))
        self.port=tk.StringVar(value=str(app.config.get('port',6444)))
        ttk.Label(row,text='IP').pack(side='left');ttk.Entry(row,textvariable=self.ip,width=22).pack(side='left',padx=6)
        ttk.Label(row,text='端口').pack(side='left');ttk.Entry(row,textvariable=self.port,width=8).pack(side='left',padx=6)
        for title,action in [('搜索局域网设备',self.scan),('保存地址并重连',self.save_address),('验证当前连接',self.verify),('导入配置',self.import_config),('导出备份',self.export_config)]:
            self.button(row if title.startswith('搜索') else card,title,action)
        login=ttk.LabelFrame(parent,text='美居账号 · 获取 / 更新设备密钥',padding=12);login.pack(fill='x',pady=8)
        loginrow=ttk.Frame(login);loginrow.pack(fill='x')
        self.account=tk.StringVar();self.password=tk.StringVar()
        ttk.Label(loginrow,text='账号').pack(side='left');ttk.Entry(loginrow,textvariable=self.account,width=25).pack(side='left',padx=6)
        ttk.Label(loginrow,text='密码').pack(side='left');self.password_entry=ttk.Entry(loginrow,textvariable=self.password,show='●',width=25);self.password_entry.pack(side='left',padx=6)
        self.button(login,'登录并获取 / 更新密钥',self.login)
        ttk.Label(login,text='先在官方美居配网并绑定设备。新密钥通过连接验证后才保存；当前仅支持 7600V1E0。').pack(anchor='w')
        ttk.Label(parent,textvariable=self.status,wraplength=1000).pack(anchor='w',pady=4)
        self.update_info()

    def button(self,parent,title,action):
        b=ttk.Button(parent,text=title,command=action);b.pack(anchor='w',pady=3);self.buttons.append(b)

    def update_info(self):
        c=self.target or self.app.config
        self.info.set(f"{'待配对' if self.target else '当前设备'} · 7600V1E0 · ID {c.get('device_id',0)}")

    def run(self,task,success):
        if self.busy: return
        self.busy=True
        for b in self.buttons:b.configure(state='disabled')
        self.status.set('正在处理…')
        def work():
            try: result=(True,task())
            except ValueError as e: result=(False,str(e))
            except Exception: result=(False,'操作失败，请检查网络、配对文件或美居接口；原配置已保留。')
            self.app.events.put(('settings', (self,success,result)))
        threading.Thread(target=work,daemon=True).start()

    def finish(self,success,result):
        self.busy=False
        for b in self.buttons:b.configure(state='normal')
        if result[0]:success(result[1])
        else:self.status.set(result[1])

    def apply(self,c):
        self.app.config_generation+=1
        self.app.config=c
        self.target=None
        self.ip.set(c['ip_address']);self.port.set(str(c['port']))
        self.app.snapshot={};self.app.online=False;self.app.busy=False
        self.app.connection.set('正在重新连接…');self.app.updated.set('等待新设备数据')
        for variable in list(self.app.priority_values.values())+list(self.app.metrics.values()):variable.set('未上报')
        for field,label in self.app.switch_labels.items():label.set('未上报')
        for field in self.app.table.get_children():self.app.table.item(field, values=(self.app.table.item(field,'values')[0],'未上报',field))
        self.app.storage_remaining_label.set('剩余时间未上报');self.app.wash_remaining_label.set('剩余时间未上报');self.app.running_status_label.set('运行状态未上报');self.app.wash_storage_status.set('保管状态未上报')
        self.app.set_controls()
        self.app.worker.commands.put(('configure',(c,self.app.config_generation)))
        self.update_info();self.status.set('配置已保存，正在重新连接。')

    def save_verified(self,c):
        pairing.verify_config(c);pairing.write_config(self.config_path,c);return c

    def candidate(self):
        c=dict(self.target or self.app.config)
        c['ip_address']=self.ip.get().strip()
        try:c['port']=int(self.port.get())
        except ValueError:raise ValueError('端口无效。') from None
        return c

    def scan(self):
        def display(devices):
            if not devices:self.status.set('未发现洗碗机，请检查同一 Wi-Fi 和路由器隔离设置。');return
            dialog=tk.Toplevel(self.app.root);dialog.title('选择洗碗机')
            for d in devices:
                def select(device=d):
                    if not pairing.supported(device):messagebox.showinfo('暂不支持','当前仅支持 7600V1E0 / E1 / V3。',parent=dialog);return
                    self.ip.set(device['ip_address']);self.port.set(str(device['port']))
                    if device['device_id']!=self.app.config.get('device_id'):
                        self.target=dict(pairing.DEFAULT,device_id=device['device_id'],ip_address=device['ip_address'],port=device['port'])
                        self.status.set('已选择设备，请登录美居获取密钥。')
                    else:self.target=None;self.status.set('已找回设备地址，请保存地址并重连。')
                    self.update_info();dialog.destroy()
                ttk.Button(dialog,text=f"{d['model']} · {d['ip_address']} · ID {d['device_id']}",command=select).pack(fill='x',padx=12,pady=6)
            self.status.set('搜索完成，请选择设备。')
        hint=self.ip.get().strip()
        self.run(lambda:pairing.scan_devices(hint),display)

    def save_address(self):
        try:c=pairing.validate_config(self.candidate())
        except ValueError as e:self.status.set(str(e));return
        self.run(lambda:self.save_verified(c),self.apply)

    def verify(self):
        c=dict(self.app.config)
        self.run(lambda:pairing.verify_config(c),lambda _:self.status.set('当前密钥认证和状态读取成功。'))

    def login(self):
        try:c=self.candidate();id_value=int(c.get('device_id',0));assert id_value>0
        except (ValueError,AssertionError):self.status.set('请先搜索并选择洗碗机。');return
        account,password=self.account.get(),self.password.get()
        self.password.set('')
        def task():
            result=asyncio.run(pairing.cloud_config(account,password,c))
            pairing.write_config(self.config_path,result)
            return result
        self.run(task,self.apply)

    def import_config(self):
        path=filedialog.askopenfilename(parent=self.app.root,title='导入配对配置',filetypes=[('JSON','*.json')])
        if path:self.run(lambda:self.save_verified(pairing.read_config(path)),self.apply)

    def export_config(self):
        c=dict(self.app.config)
        try:pairing.validate_config(c)
        except ValueError as e:self.status.set(str(e));return
        if not messagebox.askokcancel('导出备份','备份包含设备 Token 和 Key，请保存到私人位置，不要公开分享。',parent=self.app.root):return
        path=filedialog.asksaveasfilename(parent=self.app.root,title='导出配对备份',initialfile='dishwasher-backup.json',defaultextension='.json')
        if path:self.run(lambda:pairing.write_config(path,c),lambda _:self.status.set('备份已导出。'))
