"""直接通过局域网读取美的洗碗机温度，不依赖 中转服务。"""
import json
from datetime import datetime
from pathlib import Path

from midealan.const import ProtocolVersion
from midealan.devices.e1 import MideaAppliance


def main():
    config_path = Path(__file__).with_name("dishwasher.json")
    config = json.loads(config_path.read_text(encoding="utf-8"))
    config["device_protocol"] = ProtocolVersion(config["device_protocol"])
    device = MideaAppliance(**config)
    try:
        # 建立 V3 加密连接并查询状态；不发送任何控制命令。
        if not device.connect(check_protocol=True):
            raise RuntimeError("无法连接洗碗机，请检查网络、IP 和配对参数。")
        temperature = device.get_attribute("temperature")
        if temperature is None:
            raise RuntimeError("设备未返回温度，本次没有可用读数。")
        print(f"{datetime.now():%Y-%m-%d %H:%M:%S} 洗碗机温度：{temperature} °C")
    finally:
        device.close_socket()


if __name__ == "__main__":
    main()
