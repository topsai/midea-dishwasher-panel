"""Pairing/recovery services. No account credentials are persisted or logged."""
import ipaddress
import json
import logging
import re
from pathlib import Path

from aiohttp import ClientSession, ClientTimeout
from midealan.cloud import MeijuCloud
from midealan.const import ProtocolVersion
from midealan.devices.e1 import MideaAppliance
from midealan.discover import discover

# The upstream debug logger may contain cloud payloads; this UI never enables it.
logging.getLogger('midealan.cloud').disabled = True

DEFAULT = dict(name='洗碗机', device_id=0, ip_address='', port=6444, model='7600V1E0',
               subtype=3, device_protocol=3, customize='')

def validate_config(data):
    c = {key: data.get(key, default) for key, default in DEFAULT.items()}
    c.update(token=data.get('token', ''), key=data.get('key', ''))
    try:
        if c['model'] != '7600V1E0' or int(c['subtype']) != 3 or int(c['device_protocol']) != 3:
            raise ValueError()
        if 'device_type' in data and int(data['device_type']) != 225:
            raise ValueError()
        for field in ('device_id', 'port', 'subtype', 'device_protocol'):
            if not re.fullmatch(r'[0-9]+', str(c[field])): raise ValueError()
            c[field] = int(c[field])
        if not 0 < c['device_id'] <= 0xffffffffffff or not 1 <= c['port'] <= 65535:
            raise ValueError()
        if int(ipaddress.IPv4Address(c['ip_address'])) == 0: raise ValueError()
        for field, length in (('token',128), ('key',64)):
            if not isinstance(c[field],str) or not re.fullmatch('[0-9a-fA-F]{'+str(length)+'}',c[field]):
                raise ValueError()
        return c
    except (ValueError, TypeError):
        raise ValueError('配置无效：需要 7600V1E0、E1/V3、subtype 3 和完整配对信息。') from None

def supported(d):
    return d.get('type') == 225 and d.get('protocol') == 3 and d.get('model') == '7600V1E0'

def scan_devices(hint=None):
    devices=discover(discover_type=[225])
    if hint:
        try:
            ipaddress.IPv4Address(hint)
            devices.update(discover(discover_type=[225],ip_address=hint))
        except ValueError:
            pass
    return list(devices.values())

def verify_config(c):
    cfg=validate_config(c)
    cfg['device_protocol']=ProtocolVersion(cfg['device_protocol'])
    device=MideaAppliance(**cfg)
    try:
        if not device.connect(check_protocol=True): raise ValueError()
        device.refresh_status(check_protocol=True)
    except Exception:
        raise ValueError('连接验证失败，请检查 Wi-Fi、设备地址和密钥；旧配置已保留。') from None
    finally:
        device.close_socket()

async def cloud_config(account, password, target):
    if not account.strip() or not password: raise ValueError('请输入美居账号和密码。')
    async with ClientSession(timeout=ClientTimeout(total=15)) as session:
        cloud=MeijuCloud('美的美居',session,account.strip(),password)
        try:
            logged_in = await cloud.login()
        except Exception:
            raise ValueError('美居登录失败，请检查网络、账号验证或接口限制。') from None
        if not logged_in:
            raise ValueError('美居登录失败，请检查账号密码；账号验证或接口限制也可能导致失败。')
        try:
            keys=await cloud.get_cloud_keys(int(target['device_id']))
        except Exception:
            raise ValueError('美居获取密钥失败，请检查设备绑定和接口限制；旧配置已保留。') from None
        for pair in keys.values():
            candidate=dict(target,**pair)
            try:
                candidate=validate_config(candidate)
                verify_config(candidate)
                return candidate
            except ValueError:
                continue
        raise ValueError('未获得可用密钥：请确认设备绑定在该账号下且地址正确；旧配置已保留。')

def read_config(path):
    if Path(path).stat().st_size > 32768: raise ValueError('配对文件过大。')
    return validate_config(json.loads(Path(path).read_text(encoding='utf-8-sig')))

def write_config(path, config):
    path=Path(path)
    c=validate_config(config)
    temporary=path.with_name(path.name+'.tmp')
    try:
        temporary.write_text(json.dumps(c,ensure_ascii=False,indent=2),encoding='utf-8')
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)
