# Android 洗碗机面板

原生 Java App，手机通过家庭 Wi-Fi 直接连接 7600V1E0（E1/V3、subtype 3）。无需电脑或 中转服务 在线。当前版本 **1.1.0**（versionCode 24），最低 Android 8.0 / API 26。

同一发布页还提供 [Python 独立源码包和 Windows EXE 包](../desktop/README.md)。本次桌面分发补充不改变安卓 APK 或通信功能。

## 安装与使用

从 [Releases](https://github.com/topsai/midea-dishwasher-panel/releases/latest) 下载 `dishwasher-debug.apk`。这是调试签名构建，安装时按系统提示允许该来源安装应用。

默认主页显示重点状态、电源、洗涤启动和保管切换；齿轮菜单提供状态、控制、设备三个子页面。状态页展示全部 24 个字段，控制页提供童锁，设备页集中提供连接与配对工具。子页面左上角和系统返回键均可返回主页。

在“设备”页可以搜索并选择洗碗机、登录美居获取或更新密钥，也可以导入已有配对 JSON。新配置通过设备认证和状态读取后才保存；密码不保存，设备凭据由 Android Keystore 加密。详细操作见 [连接与恢复指南](../docs/CONNECTION.md)。真实美居账号获取新密钥尚待实测，验证码登录暂不支持。

自动读取在一次请求完成后间隔 2 秒。断线时标明读数可能过期并禁用控制；手动刷新／重连位于设备页。启动和关机需要确认，保管按钮根据设备返回的开关状态切换颜色和动作。独立暂停、预约、独立烘干／紫外及耗材档位设置尚未实现。

切到后台关闭连接，回到前台重新连接；旋转恢复页面和模式选择，不重发控制。手机在家庭 Wi-Fi 以外时无法直接连接洗碗机。Socket 显式使用 Wi-Fi 网络，避免移动网络默认路由影响局域网连接。

卸载前请在设备页导出配置备份。若用另一台电脑自行构建，调试签名可能不同，不能覆盖安装现有 APK；不要在未备份配置时直接卸载。导出 JSON 含 Token/Key，不要公开分享。

## 构建

项目使用 JDK 17、Gradle 8.13、Android Gradle Plugin 8.13.2、compile/target SDK 36 和 Build Tools 36.0.0。配置 `JAVA_HOME` 与 `ANDROID_HOME`，也可在本机忽略的 `local.properties` 中设置 SDK 路径。

在仓库根目录执行：

```powershell
.\android\gradlew.bat -p android :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Linux/macOS 使用 `./android/gradlew -p android`。首次构建需要网络下载 Gradle 和 Maven/Google 依赖；`--offline` 只适用于缓存已完整的环境。

APK 输出：`android/app/build/outputs/apk/debug/app-debug.apk`。应用 ID：`com.topsai.dishwasher`。测试包 ID：`com.topsai.dishwasher.test`。

## 手机测试

ADB 需要已安装，手机开启 USB 调试并授权本机。在仓库根目录执行：

```powershell
.\android\tools\run_device_tests.ps1 -Adb adb
```

脚本构建、覆盖安装应用与测试 APK，并运行 instrumentation。常规测试不点击真实设备控制；存储测试使用隔离目录。测试完成后可卸载测试包。安全锁屏或系统悬浮层可能影响涉及窗口焦点和返回键的 UI 测试，应在解锁且可交互时运行。

以下为显式的只读实机验收，不属于默认测试扫描；需要应用已保存有效凭据，手机与设备在同一 Wi-Fi，且测试 APK 已安装：

```powershell
adb shell am instrument -w -e class com.topsai.dishwasher.PairingInstrumentedTest#verifyLiveDiscoveryAndSavedCredentials com.topsai.dishwasher.test/android.test.InstrumentationTestRunner
```

该方法搜索并按设备 ID 匹配，随后认证和读取状态，不发送物理控制，也不更新保存的配置。

## 调试配对与密钥检查

常规使用优先通过设备页配对。开发时也可在 `android` 目录执行：

```powershell
python tools/provision_debug.py --adb adb --config ../dishwasher.json
python tools/scan_secrets.py --config ../dishwasher.json --apk app/build/outputs/apk/debug/app-debug.apk
```

私有 `dishwasher.json` 需要由开发者提供，仓库不包含。导入脚本通过标准输入传输，不把 Token/Key 放进命令行；仅 debug APK 支持，验证并加密保存后删除临时 `bootstrap.json`。密钥检查覆盖 Git 跟踪文件、暂存差异和解压后的 APK。

## 验证范围

1.1.0 在当前连接的 PKG110 / Android 15（API 35）手机上安装，设备页入口与实机搜索、现有凭据认证及状态读取通过。Android 26 项单元测试通过，构建与 lint 无错误。更早版本另有 Android 12 设备验收，不能据此声称所有系统版本均已实测。

真实美居账号登录／获取新密钥未实测；模拟服务与 Python 对照测试验证签名和请求结构。测试未主动执行物理控制，模式兼容性及设备执行效果仍需用户确认。详细历史证据见 [验证记录](VERIFICATION.md)。

故障排查见 [连接指南](../docs/CONNECTION.md#常见问题)。协议来源及 MIT 许可见 [第三方说明](THIRD_PARTY_NOTICES.md)。
