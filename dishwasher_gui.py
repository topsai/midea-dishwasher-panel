"""美的洗碗机局域网桌面面板：Tkinter + midea-lan，不依赖 中转服务。"""
import json
import queue
import threading
import time
import tkinter as tk
from datetime import datetime
from pathlib import Path
from tkinter import messagebox, ttk

from midealan.const import ProtocolVersion
from midealan.devices.e1 import MideaAppliance

FIELDS = {
    'power': '电源', 'status': '运行状态', 'mode': '洗涤模式',
    'additional': '附加功能代码', 'uv': '紫外功能', 'dry': '烘干功能',
    'dry_status': '烘干状态', 'door': '门状态', 'rinse_aid': '漂洗剂不足',
    'salt': '软水盐不足', 'child_lock': '童锁', 'storage': '保管功能',
    'storage_status': '保管当前运行', 'time_remaining': '洗涤剩余时间',
    'progress': '洗涤阶段', 'storage_remaining': '保管剩余时间',
    'temperature': '温度', 'humidity': '湿度', 'waterswitch': '水路开关',
    'water_lack': '缺水状态', 'error_code': '错误码', 'softwater': '软水盐档位',
    'wrong_operation': '错误操作代码', 'bright': '亮碟剂档位',
}
TEXT = {
    'power_off': '关机', 'cancel': '待机／取消', 'delay': '预约等待',
    'running': '运行中', 'error': '故障', 'soft_gear': '软水设置',
    'idle': '空闲', 'pre_wash': '预洗', 'wash': '主洗', 'rinse': '漂洗',
    'dry': '烘干', 'complete': '完成', 'neutral_gear': '未选择',
    'auto_wash': '自动洗', 'strong_wash': '强力洗', 'standard_wash': '标准洗',
    'eco_wash': '节能洗', 'glass_wash': '玻璃洗', 'hour_wash': '一小时洗',
    'fast_wash': '快速洗', 'soak_wash': '浸泡洗', '90min': '90 分钟洗',
    'self_clean': '自清洁', 'fruit_wash': '水果洗', 'self_define': '自定义洗',
    'germ': '除菌模式', 'bowl_wash': '餐具洗', 'kill_germ': '消毒洗',
    'sea_food_wash': '海鲜洗', 'hot_pot_wash': '火锅洗',
    'quiet_night_wash': '夜间静音洗', 'less_wash': '少量洗',
    'oil_net_wash': '油网洗', 'cloud_wash': '云洗',
}


def display_value(field, value):
    if value is None:
        return '未上报'
    if field == 'storage_status' and isinstance(value, bool):
        return '运行中' if value else '当前未运行'
    if isinstance(value, bool):
        if field == 'door':
            return '打开' if value else '关闭'
        if field in ('rinse_aid', 'salt', 'water_lack'):
            return '是' if value else '否'
        return '开启' if value else '关闭'
    if field == 'temperature':
        return f'{value} °C'
    if field == 'humidity':
        return f'{value} %'
    if field == 'time_remaining':
        return f'{value} 分钟'
    if field == 'storage_remaining':
        return f'{value} 小时'
    return TEXT.get(str(value), str(value))


def validate_command(action, value, modes):
    if action in ('power', 'storage', 'child_lock') and type(value) is bool:
        return action, value
    if action == 'mode' and type(value) is int and value != 0 and value in modes:
        return action, value
    raise ValueError('该控制或参数不受当前协议支持。')


class DeviceWorker(threading.Thread):
    """所有网络读写在单个后台线程执行，避免界面卡住和并发报文冲突。"""
    def __init__(self, config, events):
        super().__init__(daemon=True)
        self.config = dict(config)
        self.events = events
        self.commands = queue.Queue()
        self.stopping = threading.Event()
        self.device = None

    def stop(self):
        self.stopping.set()
        self.commands.put(('stop', None))

    def run(self):
        deadline = 0
        try:
            while not self.stopping.is_set():
                try:
                    action, value = self.commands.get(timeout=max(0, deadline - time.monotonic()))
                except queue.Empty:
                    action, value = 'refresh', None
                if self.stopping.is_set():
                    break
                try:
                    if action == 'reconnect' and self.device:
                        self.device.close_socket()
                        self.device = None
                    if self.device is None:
                        # 重连后丢弃旧的快照，不能显示成新的设备状态。
                        self.events.put(('connecting', None))
                        cfg = dict(self.config)
                        cfg['device_protocol'] = ProtocolVersion(cfg['device_protocol'])
                        candidate = MideaAppliance(**cfg)
                        if not candidate.connect(check_protocol=True):
                            candidate.close_socket()
                            raise ConnectionError('无法连接或认证，请检查设备网络和配对参数。')
                        self.device = candidate
                    elif action in ('refresh', 'reconnect'):
                        self.device.refresh_status(check_protocol=True)
                    if action not in ('refresh', 'reconnect'):
                        action, value = validate_command(action, value, self.device.modes)
                        if action == 'mode':
                            self.device.set_work_mode(value)
                        else:
                            self.device.set_attribute(action, value)
                        self.events.put(('sent', (action, value)))
                        self.device.refresh_status(check_protocol=True)
                    self.events.put(('snapshot', (self.device.attributes, self.device.modes)))
                    deadline = time.monotonic() + 5
                except Exception:
                    # 不把可能包含配对数据的库异常写入界面或日志。
                    self.events.put(('error', '读取／发送失败，请检查设备网络后重连。失败操作不会自动重发。'))
                    if self.device:
                        self.device.close_socket()
                        self.device = None
                    deadline = time.monotonic() + 5
        finally:
            if self.device:
                self.device.close_socket()


class DishwasherApp:
    def __init__(self, root, config, start_worker=True):
        self.root = root
        self.config = config
        self.events = queue.Queue()
        self.worker = DeviceWorker(config, self.events)
        self.snapshot = {}
        self.modes = {}
        self.mode_lookup = {}
        self.online = False
        self.busy = False
        self.controls = []
        self.root.title('美的洗碗机 · 局域网控制面板')
        self.root.geometry('1140x850')
        self.root.minsize(940, 700)
        self.root.configure(bg='#eef3f8')
        style = ttk.Style(root)
        style.theme_use('clam')
        style.configure('.', font=('Microsoft YaHei UI', 10))
        style.configure('TFrame', background='#eef3f8')
        style.configure('TLabel', background='#eef3f8', foreground='#233047')
        style.configure('Title.TLabel', font=('Microsoft YaHei UI', 21, 'bold'))
        style.configure('Metric.TLabel', font=('Microsoft YaHei UI', 23, 'bold'), foreground='#126dbe')
        style.configure('Priority.TFrame', background='white')
        style.configure('PriorityTitle.TLabel', background='white', foreground='#66778a')
        style.configure('Start.TButton', font=('Microsoft YaHei UI', 12, 'bold'), foreground='white', background='#2266d5', padding=(18, 10))
        style.map('Start.TButton', background=[('disabled', '#a7b6c8'), ('active', '#1854b7')])
        for name, color in (('Info', '#126dbe'), ('Good', '#168365'), ('Alert', '#c0392b'), ('Muted', '#66778a'), ('Warning', '#b86e0a')):
            style.configure(f'{name}Priority.TLabel', font=('Microsoft YaHei UI', 25, 'bold'), background='white', foreground=color)
        style.configure('TButton', padding=(10, 6))
        style.configure('Treeview', rowheight=25, background='white', fieldbackground='white')
        style.configure('Treeview.Heading', font=('Microsoft YaHei UI', 10, 'bold'))
        outer = ttk.Frame(root, padding=20)
        outer.pack(fill='both', expand=True)
        ttk.Label(outer, text='洗碗机控制面板', style='Title.TLabel').pack(anchor='w')
        ttk.Label(outer, text=f"7600V1E0  ·  {config['ip_address']}:{config['port']}  ·  本机直接连接").pack(anchor='w', pady=(4, 10))
        connection = ttk.Frame(outer)
        connection.pack(fill='x')
        self.connection = tk.StringVar(value='正在连接…')
        self.updated = tk.StringVar(value='尚未获取数据')
        ttk.Label(connection, textvariable=self.connection).pack(side='left')
        ttk.Label(connection, textvariable=self.updated).pack(side='left', padx=18)
        ttk.Button(connection, text='重新连接', command=lambda: self.request('reconnect')).pack(side='right')
        ttk.Button(connection, text='立即刷新', command=lambda: self.request('refresh')).pack(side='right', padx=8)
        priority = ttk.Frame(outer)
        priority.pack(fill='x', pady=(16, 0))
        self.priority_values = {}
        self.priority_labels = {}
        for index, field in enumerate(('water_lack', 'storage', 'power', 'door')):
            card = ttk.Frame(priority, padding=(16, 12), style='Priority.TFrame')
            card.grid(row=0, column=index, sticky='nsew', padx=(0 if index == 0 else 8, 0))
            priority.columnconfigure(index, weight=1, uniform='priority')
            ttk.Label(card, text='保管' if field == 'storage' else '电源状态' if field == 'power' else FIELDS[field], style='PriorityTitle.TLabel', anchor='center', justify='center').pack(fill='x')
            self.priority_values[field] = tk.StringVar(value='未上报')
            label = ttk.Label(card, textvariable=self.priority_values[field], style='MutedPriority.TLabel', anchor='center', justify='center')
            label.pack(fill='x', pady=(6, 0))
            self.priority_labels[field] = label
            if field == 'storage':
                self.storage_remaining_label = tk.StringVar(value='剩余时间未上报')
                ttk.Label(card, textvariable=self.storage_remaining_label, style='PriorityTitle.TLabel', anchor='center').pack(fill='x', pady=(4, 0))
            if field == 'power':
                self.running_status_label = tk.StringVar(value='运行状态未上报')
                ttk.Label(card, textvariable=self.running_status_label, style='PriorityTitle.TLabel', anchor='center').pack(fill='x', pady=(4, 0))
        style.configure('Wash.TFrame', background='#edf4ff')
        style.configure('WashTitle.TLabel', background='#edf4ff', foreground='#2266d5', font=('Microsoft YaHei UI', 14, 'bold'))
        style.configure('WashCaption.TLabel', background='#edf4ff', foreground='#66778a')
        style.configure('Wash.TCombobox', padding=(12, 8), font=('Microsoft YaHei UI', 12))
        wash = ttk.Frame(outer, padding=(18, 14), style='Wash.TFrame')
        wash.pack(fill='x', pady=(12, 0))
        heading = ttk.Frame(wash, style='Wash.TFrame')
        heading.pack(side='left', padx=(0, 24))
        ttk.Label(heading, text='启动洗涤', style='WashTitle.TLabel').pack(anchor='w')
        ttk.Label(heading, text='请选择本机支持的模式', style='WashCaption.TLabel').pack(anchor='w')
        self.mode = tk.StringVar()
        self.mode_combo = ttk.Combobox(wash, textvariable=self.mode, state='disabled', width=24, style='Wash.TCombobox', font=('Microsoft YaHei UI', 12))
        self.mode_combo.pack(side='left', fill='x', expand=True, padx=(0, 16))
        actions = ttk.Frame(wash, style='Wash.TFrame')
        actions.pack(side='right')
        button = ttk.Button(actions, text='▶  启动洗涤', command=self.start_wash, style='Start.TButton')
        button.pack(side='left')
        self.controls.append(button)
        for value, label in ((True, '开启保管'), (False, '关闭保管')):
            button = ttk.Button(actions, text=label, command=lambda v=value: self.control('storage', v))
            button.pack(side='left', padx=(8, 0))
            self.controls.append(button)
        self.wash_storage_status = tk.StringVar(value='保管状态未上报')
        ttk.Label(outer, textvariable=self.wash_storage_status, foreground='#66778a', anchor='e').pack(fill='x', pady=(4, 0))
        metrics = ttk.Frame(outer)
        metrics.pack(fill='x', pady=16)
        self.metrics = {}
        for index, field in enumerate(('temperature', 'status', 'time_remaining', 'progress')):
            card = ttk.Frame(metrics, padding=(12, 5))
            card.grid(row=0, column=index, sticky='nsew')
            metrics.columnconfigure(index, weight=1)
            ttk.Label(card, text=FIELDS[field]).pack(anchor='w')
            self.metrics[field] = tk.StringVar(value='—')
            ttk.Label(card, textvariable=self.metrics[field], style='Metric.TLabel').pack(anchor='w', pady=5)
        body = ttk.Frame(outer)
        body.pack(fill='both', expand=True)
        body.columnconfigure(0, weight=0)
        body.columnconfigure(1, weight=1)
        body.rowconfigure(0, weight=1)
        left = ttk.LabelFrame(body, text='设备控制', padding=14)
        left.grid(row=0, column=0, sticky='nsew', padx=(0, 16))
        self.switch_labels = {}
        for field in ('power', 'child_lock'):
            self.switch_labels[field] = tk.StringVar(value=f'{FIELDS[field]}：—')
            ttk.Label(left, textvariable=self.switch_labels[field]).pack(anchor='w', pady=(8, 4))
            row = ttk.Frame(left)
            row.pack(fill='x')
            for value, label in ((True, '开启'), (False, '关闭')):
                button = ttk.Button(row, text=label, command=lambda f=field, v=value: self.control(f, v))
                button.pack(side='left', expand=True, fill='x', padx=2)
                self.controls.append(button)
        ttk.Label(left, text='模式列表来自通用协议，\n请只选择该机型实际支持的模式。\n选择列表本身不会发送命令。', wraplength=255).pack(anchor='w', pady=9)
        ttk.Label(left, text='当前协议未实现独立暂停、预约、\n烘干／紫外控制及耗材档位设置。\n对应参数仍可在右侧查看。', wraplength=255).pack(anchor='w', pady=9)
        right = ttk.LabelFrame(body, text='全部状态参数 · 每 5 秒刷新', padding=8)
        right.grid(row=0, column=1, sticky='nsew')
        self.table = ttk.Treeview(right, columns=('label', 'value', 'raw'), show='headings', height=14)
        for col, title, width in (('label', '参数', 155), ('value', '当前值', 140), ('raw', '原始字段 / 值', 245)):
            self.table.heading(col, text=title)
            self.table.column(col, width=width, minwidth=90)
        scrollbar = ttk.Scrollbar(right, orient='vertical', command=self.table.yview)
        self.table.configure(yscrollcommand=scrollbar.set)
        scrollbar.pack(side='right', fill='y')
        self.table.pack(fill='both', expand=True)
        for field, label in FIELDS.items():
            self.table.insert('', 'end', iid=field, values=(label, '等待读取', field))
        ttk.Label(outer, text='以下为设备返回的字段，数值含义及功能是否有效以该机型实际表现为准。').pack(anchor='w', pady=(10, 4))
        self.feedback = tk.StringVar(value='连接成功后可操作；配对参数不会显示在界面中。')
        ttk.Label(outer, textvariable=self.feedback, wraplength=1050).pack(anchor='w', pady=4)
        self.log = tk.Text(outer, height=4, font=('Microsoft YaHei UI', 9), bg='#e3eaf2', relief='flat')
        self.log.pack(fill='x', pady=(4, 0))
        self.log.configure(state='disabled')
        self.set_controls()
        self.root.protocol('WM_DELETE_WINDOW', self.close)
        if start_worker:
            self.worker.start()
        self.poll_id = root.after(150, self.poll)

    def set_controls(self):
        for button in self.controls:
            button.configure(state='normal' if self.online and not self.busy else 'disabled')
        self.mode_combo.configure(state='readonly' if self.online and not self.busy else 'disabled')

    def log_message(self, text):
        self.log.configure(state='normal')
        self.log.insert('end', f'{datetime.now():%H:%M:%S}  {text}\n')
        if int(self.log.index('end-1c').split('.')[0]) > 100:
            self.log.delete('1.0', '2.0')
        self.log.see('end')
        self.log.configure(state='disabled')

    def request(self, action, value=None):
        self.worker.commands.put((action, value))

    def control(self, field, value):
        if not self.online or self.busy:
            return
        if field == 'power' and value is False:
            if not messagebox.askyesno('关闭洗碗机', '确认关闭电源？正在执行的洗涤可能中断。', parent=self.root):
                return
        validate_command(field, value, self.modes)
        self.busy = True
        self.feedback.set('正在发送，请等待设备返回状态…')
        self.set_controls()
        self.request(field, value)

    def start_wash(self):
        if not self.online or self.busy:
            return
        code = self.mode_lookup.get(self.mode.get())
        if code is None:
            messagebox.showinfo('选择模式', '请先选择洗涤模式。', parent=self.root)
            return
        if not messagebox.askyesno('启动洗涤', f'确认启动「{self.mode.get()}」？\n此操作会向洗碗机发送实际运行指令。', parent=self.root):
            return
        self.control('mode', code)

    def poll(self):
        try:
            while True:
                event, payload = self.events.get_nowait()
                if event == 'snapshot':
                    self.snapshot, self.modes = payload
                    self.online = True
                    self.busy = False
                    self.connection.set('● 已连接')
                    self.updated.set(f'更新于 {datetime.now():%H:%M:%S}')
                    for field, value in self.snapshot.items():
                        row = (FIELDS.get(field, field), display_value(field, value), f'{field} = {json.dumps(value, ensure_ascii=False)}')
                        if self.table.exists(field):
                            self.table.item(field, values=row)
                        else:
                            self.table.insert('', 'end', iid=field, values=row)
                    for field, variable in self.metrics.items():
                        variable.set(display_value(field, self.snapshot.get(field)))
                    for field, variable in self.priority_values.items():
                        value = self.snapshot.get(field)
                        text = display_value(field, value)
                        if field == 'water_lack' and isinstance(value, bool):
                            text = '缺水' if value else '不缺水'
                        if field == 'power' and isinstance(value, bool):
                            text = '已开机' if value else '已关机'
                        variable.set(text)
                        color = 'Muted' if value is None else 'Warning' if field == 'door' and value is True else 'Alert' if field == 'water_lack' and value is True else 'Good' if (field in ('water_lack', 'door') and value is False) or (field in ('storage', 'power') and value is True) else 'Info'
                        self.priority_labels[field].configure(style=f'{color}Priority.TLabel')
                    running = self.snapshot.get('status')
                    self.running_status_label.set('运行状态未上报' if running is None else f"运行：{display_value('status', running)}")
                    remaining = self.snapshot.get('storage_remaining')
                    self.storage_remaining_label.set('剩余时间未上报' if remaining is None else f'剩余 {remaining} 小时')
                    self.wash_storage_status.set(f"保管：{display_value('storage', self.snapshot.get('storage'))}  ·  剩余：{display_value('storage_remaining', remaining)}  ·  {display_value('storage_status', self.snapshot.get('storage_status'))}")
                    for field, variable in self.switch_labels.items():
                        label = f'{FIELDS[field]}：{display_value(field, self.snapshot.get(field))}'
                        if field == 'storage':
                            label += f"\n当前动作：{display_value('storage_status', self.snapshot.get('storage_status'))}\n剩余：{display_value('storage_remaining', self.snapshot.get('storage_remaining'))}"
                        variable.set(label)
                    self.mode_lookup = {f'{TEXT.get(name, name)}': code for code, name in self.modes.items() if code != 0}
                    self.mode_combo.configure(values=list(self.mode_lookup))
                    if not self.mode.get():
                        for label, code in self.mode_lookup.items():
                            if self.modes[code] == self.snapshot.get('mode'):
                                self.mode.set(label)
                                break
                    self.set_controls()
                elif event == 'connecting':
                    self.online = False
                    self.connection.set('● 正在连接…')
                    self.set_controls()
                elif event == 'sent':
                    field, value = payload
                    label = TEXT.get(self.modes.get(value), str(value)) if field == 'mode' else display_value(field, value)
                    text = f'已发送：{FIELDS.get(field, field)} → {label}；以设备实际返回状态为准。'
                    self.feedback.set(text)
                    self.log_message(text)
                elif event == 'error':
                    self.online = False
                    self.busy = False
                    self.connection.set('● 连接异常 · 数据可能过期')
                    self.feedback.set(payload)
                    self.log_message(payload)
                    self.set_controls()
        except queue.Empty:
            pass
        self.poll_id = self.root.after(150, self.poll)

    def close(self):
        self.worker.stop()
        self.root.after_cancel(self.poll_id)
        self.root.destroy()


def main():
    root = tk.Tk()
    try:
        config = json.loads(Path(__file__).with_name('dishwasher.json').read_text(encoding='utf-8'))
        DishwasherApp(root, config)
    except Exception:
        messagebox.showerror('启动失败', '无法加载设备配置或依赖，请检查同目录的 dishwasher.json。', parent=root)
        root.destroy()
        return
    root.mainloop()


if __name__ == '__main__':
    main()
