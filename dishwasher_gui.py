"""美的洗碗机局域网桌面面板：Tkinter + midea-lan，不依赖 中转服务。"""
import json
import queue
import threading
import time
import tkinter as tk
from datetime import datetime
from pathlib import Path
from tkinter import messagebox, ttk
from dishwasher_settings import DeviceSettings
from dishwasher_pairing import DEFAULT, read_config

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
        self.generation = 0

    def emit(self, event, payload):
        self.events.put((event, payload, self.generation))

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
                    if action == 'configure':
                        if self.device:
                            self.device.close_socket()
                        self.device = None
                        next_config, generation = value
                        self.config = dict(next_config)
                        self.generation = generation
                        action, value = 'reconnect', None
                    if action == 'reconnect' and self.device:
                        self.device.close_socket()
                        self.device = None
                    if self.device is None:
                        # 重连后丢弃旧的快照，不能显示成新的设备状态。
                        self.emit('connecting', None)
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
                        self.emit('sent', (action, value))
                        self.device.refresh_status(check_protocol=True)
                    self.emit('snapshot', (self.device.attributes, self.device.modes))
                    deadline = time.monotonic() + 2
                except Exception:
                    # 不把可能包含配对数据的库异常写入界面或日志。
                    self.emit('error', '读取／发送失败，请检查设备网络后重连。失败操作不会自动重发。')
                    if self.device:
                        self.device.close_socket()
                        self.device = None
                    deadline = time.monotonic() + 2
        finally:
            if self.device:
                self.device.close_socket()


class RoundedCard(tk.Canvas):
    """Resizable rounded background for connection and status cards."""
    def __init__(self, parent, fill='white', outer_bg='#eef3f8', content_style='StatusBar.TFrame', padding=(12, 8)):
        super().__init__(parent, height=80, bg=outer_bg, highlightthickness=0)
        self.fill_color = fill
        self.content = ttk.Frame(self, padding=padding, style=content_style)
        self.window = self.create_window(8, 8, anchor='nw', window=self.content)
        self.bind('<Configure>', self.redraw)
        self.content.bind('<Configure>', self.fit_height)

    def fit_height(self, event):
        desired = self.content.winfo_reqheight() + 16
        if int(self.cget('height')) != desired:
            self.configure(height=desired)

    def redraw(self, event):
        w, h, r = event.width, event.height, 16
        self.delete('background')
        self.create_polygon(r, 0, w-r, 0, w, 0, w, r, w, h-r, w, h, w-r, h, r, h, 0, h, 0, h-r, 0, r, 0, 0, fill=self.fill_color, outline=self.fill_color, smooth=True, tags='background')
        self.tag_lower('background')
        self.itemconfigure(self.window, width=max(1, w-16))

    def set_color(self, color):
        self.fill_color = color
        self.itemconfigure('background', fill=color, outline=color)


class DishwasherApp:
    def __init__(self, root, config, start_worker=True, config_path=None):
        self.root = root
        self.config = config
        self.config_generation = 0
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
        style.configure('Priority.TFrame', background='white')
        style.configure('PriorityTitle.TLabel', background='white', foreground='#66778a')
        style.configure('Start.TButton', font=('Microsoft YaHei UI', 12, 'bold'), foreground='white', background='#2266d5', padding=(18, 10))
        style.map('Start.TButton', background=[('disabled', '#a7b6c8'), ('active', '#1854b7')])
        for name, color, active in [('ConnectedStart.TButton', '#2266d5', '#1854b7'), ('StorageOn.TButton', '#168365', '#116a51'), ('StorageOff.TButton', '#2266d5', '#1854b7')]:
            style.configure(name, font=('Microsoft YaHei UI', 12, 'bold'), foreground='white', background=color, padding=(18, 10))
            style.map(name, background=[('disabled', color), ('active', active)], foreground=[('disabled', 'white')])
        for name, color in (('Info', '#126dbe'), ('Good', '#168365'), ('Alert', '#c0392b'), ('Muted', '#66778a'), ('Warning', '#b86e0a')):
            style.configure(f'{name}Priority.TLabel', font=('Microsoft YaHei UI', 23, 'bold'), background='white', foreground=color)
        style.configure('TButton', padding=(10, 6))
        style.configure('Treeview', rowheight=25, background='white', fieldbackground='white')
        style.configure('Treeview.Heading', font=('Microsoft YaHei UI', 10, 'bold'))
        outer = ttk.Frame(root, padding=20)
        outer.pack(fill='both', expand=True)
        header = ttk.Frame(outer)
        header.pack(fill='x')
        self.home_button = ttk.Button(header, text='‹ 返回主页', command=lambda: self.show_page('home'))
        self.page_title = tk.StringVar(value='洗碗机控制面板')
        self.title_label = ttk.Label(header, textvariable=self.page_title, style='Title.TLabel')
        self.title_label.pack(side='left')
        self.settings_menu = tk.Menu(root, tearoff=False)
        for page, label in (('status', '状态'), ('control', '控制'), ('device', '设备')):
            self.settings_menu.add_command(label=label, command=lambda name=page: self.show_page(name))
        self.settings_button = ttk.Button(header, text='⚙', width=3, command=self.open_settings)
        self.settings_button.pack(side='right')
        ttk.Label(outer, text='7600V1E0').pack(anchor='w', pady=(4, 10))
        self.page_container = ttk.Frame(outer)
        self.page_container.pack(fill='both', expand=True, pady=(12, 0))
        self.pages = {name: ttk.Frame(self.page_container) for name in ('home', 'status', 'control', 'device')}
        home = self.pages['home']
        style.configure('StatusBar.TFrame', background='white')
        style.configure('StatusBar.TLabel', background='white', foreground='#66778a')
        self.status_bar = RoundedCard(home)
        self.status_bar.pack(fill='x', pady=(0, 12))
        connection = self.status_bar.content
        self.connection = tk.StringVar(value='正在连接…')
        self.updated = tk.StringVar(value='尚未获取数据')
        connection_details = ttk.Frame(connection, style='StatusBar.TFrame')
        connection_details.pack(side='left')
        ttk.Label(connection_details, textvariable=self.connection, style='StatusBar.TLabel').pack(anchor='w')
        ttk.Label(connection_details, textvariable=self.updated, style='StatusBar.TLabel').pack(anchor='w')
        self.power_toggle = ttk.Button(connection, text='电源状态未上报', command=self.toggle_power, style='Start.TButton')
        self.power_toggle.pack(side='right')
        self.controls.append(self.power_toggle)
        priority = ttk.Frame(home, padding=8, style='Priority.TFrame')
        priority.pack(fill='x', pady=(0, 0))
        self.priority_values = {}
        self.priority_labels = {}
        self.status_cards = {}
        self.status_value_labels = {}
        for index, field in enumerate(('water_lack', 'storage', 'power', 'door')):
            card = self.make_status_card(priority, field)
            card.grid(row=0, column=index, sticky='nsew', padx=(0 if index == 0 else 8, 0))
            priority.columnconfigure(index, weight=1, uniform='priority')
            ttk.Label(card.content, text='保管' if field == 'storage' else '电源状态' if field == 'power' else FIELDS[field], style=f'{field}.StatusTitle.TLabel', anchor='center', justify='center').pack(fill='x')
            self.priority_values[field] = tk.StringVar(value='未上报')
            label = ttk.Label(card.content, textvariable=self.priority_values[field], style=f'{field}.StatusValue.TLabel', anchor='center', justify='center')
            label.pack(fill='x', pady=(4, 0))
            self.priority_labels[field] = label
            self.status_value_labels[field] = label
            if field == 'storage':
                self.storage_remaining_label = tk.StringVar(value='剩余时间未上报')
                ttk.Label(card.content, textvariable=self.storage_remaining_label, style=f'{field}.StatusTitle.TLabel', anchor='center').pack(fill='x', pady=(4, 0))
            if field == 'power':
                self.running_status_label = tk.StringVar(value='运行状态未上报')
                ttk.Label(card.content, textvariable=self.running_status_label, style=f'{field}.StatusTitle.TLabel', anchor='center').pack(fill='x', pady=(4, 0))
        style.configure('Wash.TFrame', background='#edf4ff')
        style.configure('WashTitle.TLabel', background='#edf4ff', foreground='#2266d5', font=('Microsoft YaHei UI', 14, 'bold'))
        style.configure('WashCaption.TLabel', background='#edf4ff', foreground='#66778a')
        style.configure('Wash.TCombobox', padding=(12, 8), font=('Microsoft YaHei UI', 12))
        style.configure('Storage.TFrame', background='#eaf7f1')
        style.configure('StorageTitle.TLabel', background='#eaf7f1', foreground='#168365', font=('Microsoft YaHei UI', 14, 'bold'))
        style.configure('StorageCaption.TLabel', background='#eaf7f1', foreground='#66778a')
        action_cards = ttk.Frame(home)
        action_cards.pack(fill='x', pady=(12, 0))
        action_cards.columnconfigure(0, weight=1, uniform='actions')
        action_cards.columnconfigure(1, weight=1, uniform='actions')
        wash = ttk.Frame(action_cards, padding=(18, 14), style='Wash.TFrame')
        wash.grid(row=0, column=0, sticky='nsew', padx=(0, 6))
        ttk.Label(wash, text='启动洗涤', style='WashTitle.TLabel').pack(anchor='w')
        ttk.Label(wash, text='请选择本机支持的模式', style='WashCaption.TLabel').pack(anchor='w', pady=(2, 6))
        self.mode = tk.StringVar()
        self.mode_combo = ttk.Combobox(wash, textvariable=self.mode, state='disabled', width=24, style='Wash.TCombobox', font=('Microsoft YaHei UI', 12))
        self.mode_combo.pack(fill='x', pady=(0, 10))
        button = self.wash_start = ttk.Button(wash, text='▶  启动洗涤', command=self.start_wash, style='Start.TButton')
        button.pack(fill='x', side='bottom')
        self.controls.append(button)
        storage = ttk.Frame(action_cards, padding=(18, 14), style='Storage.TFrame')
        storage.grid(row=0, column=1, sticky='nsew', padx=(6, 0))
        ttk.Label(storage, text='保管控制', style='StorageTitle.TLabel').pack(anchor='w')
        self.wash_storage_status = tk.StringVar(value='保管状态未上报')
        ttk.Label(storage, textvariable=self.wash_storage_status, style='StorageCaption.TLabel', justify='left').pack(fill='x', pady=(8, 12))
        actions = ttk.Frame(storage, style='Storage.TFrame')
        actions.pack(fill='x', side='bottom')
        self.storage_toggle = ttk.Button(actions, text='保管状态未上报', command=self.toggle_storage, style='Start.TButton')
        self.storage_toggle.pack(fill='x')
        self.controls.append(self.storage_toggle)
        metrics = self.metric_panel = ttk.Frame(priority, style='Priority.TFrame')
        metrics.grid(row=1, column=0, columnspan=4, sticky='ew', pady=(8, 0))
        self.metrics = {}
        for index, field in enumerate(('temperature', 'progress')):
            card = self.make_status_card(metrics, field)
            card.grid(row=0, column=index, sticky='nsew', padx=(0 if index == 0 else 8, 0))
            metrics.columnconfigure(index, weight=1, uniform='metrics')
            ttk.Label(card.content, text=FIELDS[field], style=f'{field}.StatusTitle.TLabel', anchor='center').pack(fill='x')
            self.metrics[field] = tk.StringVar(value='未上报')
            self.status_value_labels[field] = ttk.Label(card.content, textvariable=self.metrics[field], style=f'{field}.StatusValue.TLabel', anchor='center')
            self.status_value_labels[field].pack(fill='x', pady=(4, 0))
            if field == 'progress':
                self.wash_remaining_label = tk.StringVar(value='剩余时间未上报')
                ttk.Label(card.content, textvariable=self.wash_remaining_label, style=f'{field}.StatusTitle.TLabel', anchor='center').pack(fill='x', pady=(4, 0))
        left = ttk.LabelFrame(self.pages['control'], text='设备控制', padding=14)
        left.pack(fill='x')
        self.switch_labels = {}
        for field in ('child_lock',):
            self.switch_labels[field] = tk.StringVar(value=f'{FIELDS[field]}：—')
            ttk.Label(left, textvariable=self.switch_labels[field]).pack(anchor='w', pady=(8, 4))
            row = ttk.Frame(left)
            row.pack(fill='x')
            for value, label in ((True, '开启'), (False, '关闭')):
                button = ttk.Button(row, text=label, command=lambda f=field, v=value: self.control(f, v))
                button.pack(side='left', expand=True, fill='x', padx=2)
                self.controls.append(button)
        ttk.Label(left, text='模式列表来自通用协议，\n请只选择该机型实际支持的模式。\n选择列表本身不会发送命令。', wraplength=255).pack(anchor='w', pady=9)
        ttk.Label(left, text='当前协议未实现独立暂停、预约、\n烘干／紫外控制及耗材档位设置。\n对应参数可在状态页查看。', wraplength=255).pack(anchor='w', pady=9)
        ttk.Button(self.pages['control'], text='选择模式 / 启动洗涤', command=lambda: self.show_page('home')).pack(anchor='w', pady=12)
        right = ttk.LabelFrame(self.pages['status'], text='全部参数 · 每 2 秒刷新', padding=8)
        right.pack(fill='both', expand=True)
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
        ttk.Label(self.pages['status'], text='以下为设备返回的字段，数值含义及功能是否有效以该机型实际表现为准。').pack(anchor='w', pady=(10, 4))
        self.device_canvas = tk.Canvas(self.pages['device'], bg='#eef3f8', highlightthickness=0)
        device_scrollbar = ttk.Scrollbar(self.pages['device'], orient='vertical', command=self.device_canvas.yview)
        self.device_canvas.configure(yscrollcommand=device_scrollbar.set)
        device_scrollbar.pack(side='right', fill='y')
        self.device_canvas.pack(side='left', fill='both', expand=True)
        device_content = ttk.Frame(self.device_canvas)
        device_window = self.device_canvas.create_window(0, 0, window=device_content, anchor='nw')
        device_content.bind('<Configure>', lambda e: self.device_canvas.configure(scrollregion=self.device_canvas.bbox('all')))
        self.device_canvas.bind('<Configure>', lambda e: self.device_canvas.itemconfigure(device_window, width=e.width))
        self.root.bind('<MouseWheel>', lambda e: self.device_canvas.yview_scroll(int(-e.delta/120), 'units') if self.current_page == 'device' else None)
        device = ttk.LabelFrame(device_content, text='设备信息', padding=16)
        device.pack(fill='x')
        ttk.Label(device, text='电脑与洗碗机需要连接同一局域网。配对 Token / Key 不在界面中显示。').pack(anchor='w', pady=(12, 4))
        device_actions = ttk.Frame(device)
        device_actions.pack(anchor='w', pady=8)
        self.reconnect_button = ttk.Button(device_actions, text='重新连接', command=lambda: self.request('reconnect'))
        self.reconnect_button.pack(side='left')
        self.refresh_button = ttk.Button(device_actions, text='立即刷新', command=lambda: self.request('refresh'))
        self.refresh_button.pack(side='left', padx=8)
        self.device_settings = DeviceSettings(self, device_content, config_path or Path(__file__).with_name('dishwasher.json'))
        ttk.Label(device_content, text='操作记录').pack(anchor='w', pady=(16, 4))
        self.feedback = tk.StringVar(value='连接成功后可操作；配对参数不会显示在界面中。')
        ttk.Label(outer, textvariable=self.feedback, wraplength=1050).pack(anchor='w', pady=4)
        self.log = tk.Text(device_content, height=4, font=('Microsoft YaHei UI', 9), bg='#e3eaf2', relief='flat')
        self.log.pack(fill='x', pady=(4, 0))
        self.log.configure(state='disabled')
        self.show_page('home')
        self.root.bind('<Escape>', lambda event: self.show_page('home'))
        self.set_controls()
        self.root.protocol('WM_DELETE_WINDOW', self.close)
        if start_worker:
            self.worker.start()
        self.poll_id = root.after(150, self.poll)

    def make_status_card(self, parent, field):
        style = ttk.Style(self.root)
        style.configure(f'{field}.Status.TFrame', background='#f2f5f8')
        style.configure(f'{field}.StatusTitle.TLabel', background='#f2f5f8', foreground='#66778a')
        style.configure(f'{field}.StatusValue.TLabel', font=('Microsoft YaHei UI', 23, 'bold'), background='#f2f5f8', foreground='#66778a')
        card = RoundedCard(parent, fill='#f2f5f8', outer_bg='white', content_style=f'{field}.Status.TFrame', padding=(6, 4))
        self.status_cards[field] = card
        return card

    def update_status_colors(self):
        style = ttk.Style(self.root)
        for field, card in self.status_cards.items():
            value = self.snapshot.get(field)
            color, background = '#66778a', '#f2f5f8'
            if self.online and value is not None:
                if field == 'temperature':
                    if type(value) not in (int, float):
                        color, background = '#66778a', '#f2f5f8'
                    elif value < 35:
                        color, background = '#2266d5', '#edf4ff'
                    elif value <= 50:
                        color, background = '#b86e0a', '#fff4df'
                    else:
                        color, background = '#c0392b', '#ffefed'
                elif field == 'progress':
                    color, background = ('#2266d5', '#edf4ff') if value == 'idle' else ('#b86e0a', '#fff4df')
                elif field == 'water_lack' and value is True:
                    color, background = '#c0392b', '#ffefed'
                elif field == 'door' and value is True:
                    color, background = '#b86e0a', '#fff4df'
                elif field in ('water_lack', 'door') or field in ('storage', 'power') and value is True:
                    color, background = '#168365', '#eaf7f1'
                else:
                    color, background = '#2266d5', '#edf4ff'
            card.set_color(background)
            style.configure(f'{field}.Status.TFrame', background=background)
            style.configure(f'{field}.StatusTitle.TLabel', background=background)
            style.configure(f'{field}.StatusValue.TLabel', background=background, foreground=color)

    def open_settings(self):
        try:
            self.settings_menu.tk_popup(self.settings_button.winfo_rootx(), self.settings_button.winfo_rooty() + self.settings_button.winfo_height())
        finally:
            self.settings_menu.grab_release()

    def show_page(self, name):
        if name not in self.pages:
            raise ValueError('未知页面')
        for frame in self.pages.values():
            frame.pack_forget()
        self.pages[name].pack(fill='both', expand=True)
        self.current_page = name
        self.page_title.set({'home': '洗碗机控制面板', 'status': '状态', 'control': '控制', 'device': '设备'}[name])
        if name == 'home':
            self.home_button.pack_forget()
        else:
            self.home_button.pack(side='left', before=self.title_label, padx=(0, 12))

    def set_controls(self):
        self.update_status_colors()
        for button in self.controls:
            button.configure(state='normal' if self.online and not self.busy else 'disabled')
        self.mode_combo.configure(state='readonly' if self.online and not self.busy else 'disabled')
        self.wash_start.configure(style='ConnectedStart.TButton' if self.online else 'Start.TButton')
        power = self.snapshot.get('power')
        self.power_toggle.configure(style=('StorageOn.TButton' if power else 'StorageOff.TButton') if self.online and type(power) is bool else 'Start.TButton')
        self.power_toggle.configure(text='关闭电源' if power is True else '开启电源' if power is False else '电源状态未上报', state='normal' if self.online and not self.busy and type(power) is bool else 'disabled')
        current = self.snapshot.get('storage')
        self.storage_toggle.configure(style=('StorageOn.TButton' if current else 'StorageOff.TButton') if self.online and type(current) is bool else 'Start.TButton')
        self.storage_toggle.configure(text='关闭保管' if current is True else '开启保管' if current is False else '保管状态未上报', state='normal' if self.online and not self.busy and type(current) is bool else 'disabled')

    def toggle_power(self):
        current = self.snapshot.get('power')
        if type(current) is bool:
            self.control('power', not current)

    def toggle_storage(self):
        current = self.snapshot.get('storage')
        if type(current) is bool:
            self.control('storage', not current)

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
                item = self.events.get_nowait()
                event, payload = item[:2]
                if len(item) == 3 and item[2] != self.config_generation:
                    continue
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
                    remaining = self.snapshot.get('time_remaining')
                    self.wash_remaining_label.set('剩余时间未上报' if remaining is None else f"剩余：{display_value('time_remaining', remaining)}")
                    for field, variable in self.priority_values.items():
                        value = self.snapshot.get(field)
                        text = display_value(field, value)
                        if field == 'water_lack' and isinstance(value, bool):
                            text = '缺水' if value else '不缺水'
                        if field == 'power' and isinstance(value, bool):
                            text = '已开机' if value else '已关机'
                        variable.set(text)
                    running = self.snapshot.get('status')
                    self.running_status_label.set('运行状态未上报' if running is None else f"运行：{display_value('status', running)}")
                    remaining = self.snapshot.get('storage_remaining')
                    self.storage_remaining_label.set('剩余时间未上报' if remaining is None else f'剩余 {remaining} 小时')
                    self.wash_storage_status.set(f"保管：{display_value('storage', self.snapshot.get('storage'))}\n剩余：{display_value('storage_remaining', remaining)}\n{display_value('storage_status', self.snapshot.get('storage_status'))}")
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
                elif event == 'settings':
                    settings, success, result = payload
                    settings.finish(success, result)
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
        path = Path(__file__).with_name('dishwasher.json')
        config = read_config(path) if path.exists() else dict(DEFAULT)
        DishwasherApp(root, config)
    except Exception:
        messagebox.showerror('启动失败', '无法加载设备配置或依赖，请检查同目录的 dishwasher.json。', parent=root)
        root.destroy()
        return
    root.mainloop()


if __name__ == '__main__':
    main()
