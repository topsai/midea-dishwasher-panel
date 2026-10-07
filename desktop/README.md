# Python 桌面版下载与运行

版本 1.1.0，支持 7600V1E0 / E1 / V3 / subtype 3。连接洗碗机不需要 中转服务。

## Windows 可执行版

从 [GitHub Releases](https://github.com/topsai/midea-dishwasher-panel/releases/tag/v1.1.0) 下载 `dishwasher-python-windows-x64-v1.1.0.zip`，解压到当前用户可写的目录，双击 `DishwasherPanel.exe`。无需安装 Python，也不需要管理员权限。不要直接在压缩包内运行。

这是基于 Python 3.14 的 Windows x64 构建，本机完成启动验证；其他系统、ARM 和 32 位环境没有实测。EXE 首次启动会解压运行组件，可能需要等待几秒。

默认进入主页。齿轮 → 设备中搜索洗碗机并登录美居获取密钥，或导入已有配对 JSON。真实美居账号获取新密钥尚未实测，验证码登录暂不支持；现有凭据的局域网认证和读取已验证。

成功配置后，`dishwasher.json` 保存在 **EXE 同目录**，不保存在临时解压目录。更新时可以保留原配置，但不要把实际配置公开分享。软件包不附带任何真实配对密钥。

## Python 源码版

下载 `dishwasher-python-source-v1.1.0.zip` 并解压。需要 Python 3.12+ 与 Tkinter；Windows 双击 `start_panel.bat`，首次启动自动创建 `.venv` 并安装依赖，需要互联网。也可在解压目录运行：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe dishwasher_gui.py
```

Linux/macOS 使用 `.venv/bin/python`；Tkinter 可能需要系统包管理器安装。源码包中的四个 `dishwasher_*.py` 模块必须在同一目录。

有有效配置后，Windows 也可以双击 `read_temperature.bat` 只读温度。

完整配对、备份恢复和故障排查见 [仓库连接指南](https://github.com/topsai/midea-dishwasher-panel/blob/main/docs/CONNECTION.md)。导入配置需要设备在线、地址可达，并通过认证才会替换旧配置。

## 开发者打包

在 Windows x64 上使用包含 Tkinter 的 Python 3.12+，于仓库根目录执行：

```powershell
python -m pip install -r requirements.txt -r desktop/requirements-build.txt
python desktop/package.py --output desktop/dist
```

产出 Windows 可执行 ZIP 和独立 Python 源码 ZIP。脚本使用文件白名单打包，不收集本地 `dishwasher.json`、虚拟环境、构建缓存或签名文件。打包 EXE 时收集 `midealan` 的动态模块与 Crypto 组件；冻结程序使用 `sys.executable` 定位外部配置，见 [PyInstaller 运行时说明](https://pyinstaller.org/en/stable/runtime-information.html)。

每次发布都应提供这两个桌面包，并将它们加入 `SHA256SUMS.txt`。安卓仍提供独立 APK，完整仓库源码 ZIP 作为另一个下载项。
