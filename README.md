# 洗碗机局域网面板

Python 桌面版与原生 Android App，通过家庭局域网直接连接洗碗机，展示状态并控制设备。**日常使用不需要 中转服务、电脑中转或美居云端在线。** 首次从美居获取设备密钥需要互联网和绑定该设备的个人账号，也可以导入已有配置。

当前版本 **1.1.0**。仅适配已验证的 **7600V1E0 / E1 / V3 / subtype 3**，暂不支持其他型号，也不能覆盖美居 App 的全部功能。本项目与美的官方无隶属关系。

- [下载 APK 和完整源码](https://github.com/topsai/midea-dishwasher-panel/releases/latest)
- [连接、配对与备份恢复](docs/CONNECTION.md)
- [Android 安装、构建与测试](android/README.md)
- [更新记录](CHANGELOG.md) · [验证记录](android/VERIFICATION.md)

## 功能与页面

两个版本保持相同的主要功能。启动默认进入主页，右上角齿轮菜单进入三个子页面。

| 页面 | 内容 |
| --- | --- |
| 主页 | 连接状态与更新时间、电源按钮；缺水、保管与剩余小时、电源与运行状态、机门、温度、洗涤阶段与剩余分钟；启动洗涤与单个保管切换按钮 |
| 状态 | 全部 24 个参数及原始字段和值 |
| 控制 | 童锁、返回主页选择模式和启动洗涤、功能范围说明 |
| 设备 | 局域网搜索、美居登录获取或更新密钥、地址修改、连接验证、手动刷新／重连、配置导入／导出；Python 另有操作记录 |

- 一次读取完成后约 **2 秒**发起下次自动读取，网络耗时会影响实际更新周期；不会并发轮询。
- 启动洗涤与关闭电源需要确认；选择模式本身不发送命令。
- 保管按钮：开启时绿色、关闭时蓝色，按钮表示下一步动作。
- 温度：低于 35℃ 蓝色，35–50℃ 橙色，高于 50℃ 红色；洗涤阶段空闲蓝色，其他已上报阶段橙色。
- 未上报的数据显示“未上报”，不会当成 0；断线保留最后读数并提示可能过期，禁用设备控制。失败控制不自动重发。

“保管功能”表示开关，“保管当前运行”表示当前动作。**开启保管但当前未运行属于不同状态，不能据此判断保管已关闭。** 保管剩余时间单位为小时，洗涤剩余时间单位为分钟。

模式列表来自通用协议，尚未在本机逐项验证，请只选择机器实际支持的程序。独立暂停、预约、独立烘干／紫外控制、盐和亮碟剂档位设置尚未实现。发送成功不等于设备执行成功，以设备实际返回状态为准。

## Android 快速开始

1. 从 [Releases](https://github.com/topsai/midea-dishwasher-panel/releases/latest) 下载 `dishwasher-debug.apk` 并安装。Android 最低版本为 8.0（API 26）。这是调试构建，安装时可能需要允许该来源安装应用。
2. 用官方美居给洗碗机配网、绑定自己的账号，再让手机连接同一家庭 Wi-Fi。
3. 打开 App → 齿轮 → **设备**，选择以下一种方式：
   - **首次配对**：搜索局域网设备，选中洗碗机，输入美居账号密码，点击“登录并获取 / 更新密钥”。
   - **已有配置**：点击“导入配对 JSON”，选择有效的 `dishwasher.json` 或备份文件。
4. 连接认证和状态读取通过后保存配置，返回主页查看状态。

真实美居账号登录／获取新密钥**尚未完成实测**；现有测试验证了签名、请求格式和失败保护。云端是否允许获取密钥取决于账号、绑定关系及美居接口，验证码登录暂不支持。已有密钥的局域网搜索、认证和状态读取已通过实机验证。

手机 App 直接与洗碗机通信，电脑只用于开发或安装。当前不提供离开家庭局域网后的远程控制。

## Python 安装和运行

需要 **Python 3.12+** 和 Tkinter，依赖固定为 `midea-lan==2026.9.2`；本机实测 Python 3.14。电脑与洗碗机需要连接同一局域网。

下载源码后，Windows 可双击 `start_panel.bat`。首次运行会创建项目目录下的 `.venv` 并安装依赖，需要互联网连接；请确认 `python` 命令指向符合版本要求的 Python。

也可以在项目根目录执行：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe dishwasher_gui.py
```

Linux/macOS：

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python dishwasher_gui.py
```

Linux 的 Tkinter 可能需要通过系统包管理器安装。`dishwasher_gui.py`、`dishwasher_settings.py`、`dishwasher_pairing.py` 需要保留在同一目录。

首次启动没有 `dishwasher.json` 时会打开界面，从齿轮 → **设备**搜索并登录配对或导入现有配置即可。不要把空凭据示例复制为实际配置后直接启动：示例中的设备 ID 为 0，Token/Key 为空，仅用于说明文件格式。如果现有 `dishwasher.json` 损坏而无法启动，可将它移到私人备份位置，再打开程序导入有效配置。

成功配置后，只读温度可执行：

```powershell
.\.venv\Scripts\python.exe read_temperature.py
```

Windows 也可双击 `read_temperature.bat`；此脚本需要根目录已有有效的 `dishwasher.json`。

## 配对信息与恢复

- 美居密码只用于本次获取密钥，不写入配置文件。
- Android 使用 Keystore 加密保存设备 Token/Key，禁用配置的系统云备份和设备迁移；卸载 App 会丢失本机配置。
- Python 的 `dishwasher.json` 和导出的 JSON 备份包含明文 Token/Key，已忽略提交；请保存到私人位置，不要公开分享。
- 导入配置、修改地址、获取新密钥都会先认证并读取状态，通过后才替换旧配置；验证失败保留原配置。因此导入时也需要设备在线且地址可达。

详细步骤、密钥有效性说明及故障排查见 [连接指南](docs/CONNECTION.md)。

## 开发与验证

桌面测试在已安装依赖、具备 Tkinter 图形环境的项目根目录运行：

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests -p "test_*.py"
```

Android 构建与测试见 [Android 开发说明](android/README.md)。1.1.0 的验证记录：Python 22 项测试、Android 26 项单元测试通过；安卓设备页入口测试及真实设备的只读搜索／认证／状态读取通过。测试没有主动发送实际电源、童锁、保管或洗涤控制指令，物理执行效果仍需用户操作确认。

协议来源：[midea-lan](https://github.com/wuwentao/midea-lan) 2026.9.2；移植部分的 MIT 许可与来源见 [第三方说明](android/THIRD_PARTY_NOTICES.md)。源码、APK 和下载包均不包含实际配对密钥。
