# 美的洗碗机局域网 Python 面板

通过 TCP 直接连接美的 V3 洗碗机，使用 Tkinter 展示状态并控制设备。运行时不依赖 中转服务 或美居云服务；首次配对需要提供有效的设备 Token 和 Key。

## 功能

- 展示协议库解析的 24 个参数，每 5 秒刷新，可手动刷新及重连。
- 控制电源、童锁、保管。
- 选择洗涤模式并确认启动。选择下拉列表本身不会发送命令。
- 显示原始字段和值、连接状态及操作反馈。
- 连接异常时禁用控制，并标注读数可能过期；失败操作不会自动重发。

模式列表来自通用协议，尚未在 7600V1E0 上逐项验证，只应选择机型支持的模式。暂停、预约、独立烘干／紫外控制、盐和亮碟剂档位设置未在当前协议库实现。

部分通用字段由协议库初始化为默认值，不代表机型支持该功能。发送命令不等同于设备已执行，应查看设备实际返回状态。

## 安装和运行

需要 Python 3.11+（本机实测 Python 3.14），Python 需包含 Tkinter。电脑与洗碗机须在同一局域网。

1. 将 `dishwasher.example.json` 复制为 `dishwasher.json`。
2. 填写实际设备的 ID、IP、Token、Key、型号及协议参数。V3 常用端口为 6444。已有 Midea AC LAN 配置时可从其设备配置中获取这些值。
3. Windows 双击 `start_panel.bat`。首次运行会自动创建本目录 `.venv` 并安装依赖，需要互联网连接。

也可在项目目录中执行：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe dishwasher_gui.py
```

Linux/macOS 使用虚拟环境中的 Python 执行相同脚本；Tkinter 可能需要通过系统包管理器单独安装。

只读温度：

```powershell
.\.venv\Scripts\python.exe read_temperature.py
```

Windows 也可双击 `read_temperature.bat`。

## 配对配置

`dishwasher.json` 包含私有配对信息，已列入 `.gitignore`，不要提交或公开分享。仓库只提供空凭据示例。

## 验证范围

7600V1E0 实机验证了温度读取、桌面面板的 24 个字段展示、取消启动／关机确认、断线控制拦截及连接关闭。
控制按钮使用 `midea-lan` 的现有协议实现，未为测试启动洗涤或改变设备状态，需实机确认控制效果。

## 依赖

- Python Tkinter
- [midea-lan](https://github.com/wuwentao/midea_lan) 2026.9.2

本项目与美的官方无隶属关系。
