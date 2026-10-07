# Android 洗碗机面板

原生 Java 应用，手机通过家庭 Wi-Fi 直接连接美的 7600V1E0（E1/V3，subtype 3）。不需要电脑或 中转服务 在线。

## 使用

1. 安装 `dist/dishwasher-debug.apk`，手机连接洗碗机所在 Wi-Fi。
2. 在“设备”页导入现有 `dishwasher.json`（字段见根目录 example 文件）。Token 为 64 字节的十六进制串，Key 为 32 字节；导入后使用 Android Keystore AES-GCM 加密保存，界面不显示明文，应用不备份配置。
3. “状态”页每 2 秒刷新，显示温度等指标和全部 24 个字段。读数过期会标明更新时间、禁用控制；点击“立即刷新 / 重连”可重连。
4. “控制”页支持电源、童锁、保管、选择并启动洗涤模式。选择模式不会发命令；启动和关机需要确认。通用模式并非本机全部已验证支持，请选择本机实际支持的程序。

独立暂停、预约、独立烘干/紫外控制、耗材档位设置尚未实现。缺失参数显示“未上报”，不转为 0。发送成功不等于执行成功，界面以设备返回状态为准；失败写入不自动重试。切到后台关闭连接，返回前台重新连接。旋转屏幕恢复页面和模式选择，不重发控制。

## 构建

固定环境：JDK 17、Gradle 8.13、Android Gradle Plugin 8.13.2、compile/target SDK 36、Build Tools 36.0.0，最低 API 26。兼容性依据：[Android 官方 AGP 8.13 文档](https://developer.android.com/build/releases/agp-8-13-0-release-notes)。

配置 JAVA_HOME 和 ANDROID_HOME（或本机忽略的 local.properties），在 android 目录运行：

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。首次构建需要网络下载 Google/Maven 依赖；wrapper 使用官方 Gradle 下载地址。测试与设备报文对照不使用真实凭据。

手机测试（已授权 ADB）：

```powershell
./tools/run_device_tests.ps1 -Adb C:/scrcpy-win64-v4.1/adb.exe
```

它构建并安装应用与测试 APK，通过 Android instrumentation 执行测试。配置存储测试使用隔离目录，不覆盖真实配对配置；UI 测试不点击设备控制。无需 Gradle UTP 主机插件。测试完成后可卸载测试包 `com.topsai.dishwasher.test`。

调试配置可从电脑直接导入私有目录（仅 debug APK 支持）：

```powershell
python tools/provision_debug.py --adb adb --config ../dishwasher.json
python tools/scan_secrets.py --config ../dishwasher.json --apk app/build/outputs/apk/debug/app-debug.apk
```

导入脚本通过标准输入传输，不把 Token/Key 放入命令行；应用校验后加密，删除私有 `bootstrap.json`。不要提交或共享实际配对 JSON。APK 及源码均不含实际配对信息；扫描解压后的 APK、Git 跟踪内容和暂存变更。

## 本机验收

2026-10-07 在华为 TAS-AN00、Android 12/API 31 上完成安装，Wi-Fi 地址 192.0.2.4，直连洗碗机 192.0.2.7:6444。真实读取温度 26℃、待机/取消、空闲、剩余 0 分钟。电脑仅用于安装/测试，不作为应用通信中转。

单元测试覆盖 Python 协议对照、拆包粘包、认证/摘要失败、白名单控制、缺失值、断线保留旧状态、重复提交和失败写入不重发；手机测试覆盖 Keystore 加密、损坏密文拒绝、24 字段、断线禁用控制和旋转选择恢复。

未执行实际电源、童锁、保管或洗涤命令。它们的协议和提交规则经过测试，物理执行效果需用户实际操作确认。仅支持上述 E1/V3 型号配置，不能承诺覆盖美居 App 全部功能。

## 排查

- “Wi-Fi 未连接”：连接家庭 Wi-Fi；移动网络不能直接到达家庭设备。Socket 显式使用 Wi-Fi 网络，即使移动网络仍开启。
- 连接失败或超时：检查设备 IP、端口、Wi-Fi 客户端隔离和设备在线情况；“设备”页可修改 IP/端口。
- 认证/校验失败：重新导入有效配对文件，避免修改 Token/Key。
- 保存配置无法解密：重新导入；卸载应用会删除本机配置。

协议实现来源及 MIT 原文见 THIRD_PARTY_NOTICES.md 和 app/src/main/assets/midea-lan-LICENSE.txt。

### 页面导航

打开应用默认进入主页：重点状态、洗涤启动和保管按钮。右上角齿轮菜单进入“状态”（全部参数）、“控制”（童锁）、“设备”（配对与地址配置）。子页面左上角和系统返回键均可返回主页。保管开启时按钮为绿色，关闭时为蓝色。

电源开关位于主页状态卡片区域的第一排，与连接状态和更新时间并排，子页面隐藏。关闭电源仍需确认。

自动刷新间隔为2秒；Python的手动刷新和重连入口位于设备子页面。
