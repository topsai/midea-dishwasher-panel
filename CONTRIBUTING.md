# 开发与贡献

本项目当前只验证 7600V1E0 / E1 / V3 / subtype 3。增加型号支持时，先提供已脱敏的协议证据与只读验证，不应直接放宽设备白名单。

## 两个平台同步

功能、状态含义、配对保护与控制规则需要同时更新 Python 和 Android。布局可以适应平台尺寸，启动确认、失败不重发、未知状态不当作 0 等规则保持一致。

Python 安装依赖后执行 `python -m unittest discover -s tests -p "test_*.py"`；Android 按 [开发说明](android/README.md#构建) 构建、运行单元测试与 lint。有界面变化时更新两个版本的 [演示展示图](docs/SCREENSHOTS.md#生成展示图)。

## 设备测试

使用模拟状态测试布局和控制报文。涉及真实设备时，先做局域网发现、认证与状态读取；物理洗涤、电源和保管操作应在机主知情、设备准备完成时单独验证。文档明确区分模拟测试与实机执行。

真实美居登录尚未实测，不要将请求格式测试描述为云端配对成功。

## 提交与发布

禁止提交真实账号、密码、设备 Token/Key、签名文件、手机编号或配置导出。测试凭据使用固定模拟值，文档地址使用示例地址。提交前运行：

```sh
gitleaks git . --config .gitleaks.toml --redact
git diff --check
```

`.gitleaks.toml` 仅允许协议测试中一条明确的顺序字节 Token，不对所有测试目录或所有密钥字段放行。配合自己的私人配置，可使用 `android/tools/scan_secrets.py` 检查实际 Token/Key 是否进入源码或 APK，见 [安卓密钥检查说明](android/README.md#调试配对与密钥检查)。

发布应包含 APK、Windows x64 EXE ZIP、Python 独立源码 ZIP、完整源码 ZIP 及 SHA256 清单。运行 [桌面打包脚本](desktop/README.md#开发者打包)，检查压缩包中没有真实 `dishwasher.json`，同时检查 EXE 的内嵌模块。保留上游第三方许可与来源说明。

问题反馈请提供平台、版本、复现步骤与已脱敏错误信息；配对文件不适合作为公开附件。详见 [安全说明](SECURITY.md)。
