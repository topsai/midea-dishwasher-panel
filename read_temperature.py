"""直接通过局域网读取美的洗碗机温度，不依赖 中转服务。"""
from datetime import datetime
from pathlib import Path

from dishwasher_pairing import connect_paired, read_config, write_config


def main():
    config_path = Path(__file__).with_name("dishwasher.json")
    config = read_config(config_path)
    device, verified = connect_paired(config)
    try:
        if verified != config:
            try:
                write_config(config_path, verified)
            except OSError:
                print("已通过新地址连接，但配置目录不可写，地址未保存。")
        temperature = device.get_attribute("temperature")
        if temperature is None:
            raise RuntimeError("设备未返回温度，本次没有可用读数。")
        print(f"{datetime.now():%Y-%m-%d %H:%M:%S} 洗碗机温度：{temperature} °C")
    finally:
        device.close_socket()


if __name__ == "__main__":
    main()
