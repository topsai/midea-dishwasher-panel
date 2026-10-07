import asyncio
import unittest
import tempfile
from pathlib import Path
from unittest.mock import patch
import dishwasher_pairing as p

def config():
    return dict(name='洗碗机', device_id=123456, ip_address='192.0.2.7', port=6444,
                model='7600V1E0', subtype=3, device_protocol=3, customize='', token='ab'*64, key='cd'*32)

class PairingTests(unittest.TestCase):
    def test_invalid_write_preserves_existing_backup(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'device.json'
            p.write_config(path,config())
            original=path.read_bytes()
            invalid=config();invalid['key']='invalid'
            with self.assertRaises(ValueError):p.write_config(path,invalid)
            self.assertEqual(original,path.read_bytes())
            self.assertEqual(config(),p.read_config(path))

    def test_cloud_library_error_is_sanitized(self):
        class Cloud:
            def __init__(self,*args): pass
            async def login(self): raise ValueError('private-password')
        with patch.object(p,'MeijuCloud',Cloud):
            try:asyncio.run(p.cloud_config('account','password',config()))
            except ValueError as e:self.assertNotIn('private-password',str(e))
            else:self.fail('must fail')
    def test_roundtrip_and_reject_wrong_model_or_bad_key(self):
        self.assertEqual(p.validate_config(config()), config())
        for field, value in [('model', 'OTHER'), ('key', 'xx'*32), ('port', 70000), ('device_id', 0)]:
            c=config(); c[field]=value
            with self.assertRaises(ValueError): p.validate_config(c)

    def test_discovery_rejects_other_models(self):
        d=dict(device_id=123456, type=225, protocol=3, model='7600V1E0', ip_address='192.0.2.7', port=6444)
        self.assertTrue(p.supported(d))
        d['model']='OTHER'; self.assertFalse(p.supported(d))

    def test_cloud_credentials_verified_before_return(self):
        calls=[]
        class Cloud:
            def __init__(self,*args): pass
            async def login(self): return True
            async def get_cloud_keys(self, device_id):
                self_id=device_id
                self.assert_id=self_id
                return {1: {'token': 'ef'*64, 'key':'01'*32}, 2:{'token':'ab'*64,'key':'cd'*32}}
        def verify(c):
            calls.append(c['token'])
            if c['token']=='ef'*64: raise ValueError('do not expose secret')
        with patch.object(p, 'MeijuCloud', Cloud), patch.object(p, 'verify_config', verify):
            c=asyncio.run(p.cloud_config('account','password', config()))
        self.assertEqual(c['token'], 'ab'*64)
        self.assertEqual(calls, ['ef'*64, 'ab'*64])

    def test_auth_failure_has_sanitized_message(self):
        class Cloud:
            def __init__(self,*args): pass
            async def login(self): return False
        with patch.object(p,'MeijuCloud',Cloud):
            with self.assertRaisesRegex(ValueError, '登录失败'):
                asyncio.run(p.cloud_config('account','password',config()))

if __name__=='__main__': unittest.main()
