"""Reproducible fictional fixtures from midea-lan 2026.9.2; no real device access."""
import json
from pathlib import Path
from unittest.mock import patch
from hashlib import sha256
from midealan.security import LocalSecurity
from midealan.devices.e1.message import MessageQuery, MessagePower, MessageLock, MessageStorage, MessageWork
from midealan.packet_builder import PacketBuilder
key=bytes(range(32)); token=bytes(range(64)); plain=bytes(range(32,64))
s=LocalSecurity(); reply=s.aes_cbc_encrypt(plain,key)+sha256(plain).digest()
handshake=s.encode_8370(token,0); tcp=s.tcp_key(reply,key)
query=MessageQuery(0).serialize()
with patch('midealan.security.get_random_bytes',lambda n: bytes([0xa5])*n):
    frame=s.encode_8370(b'hello dishwasher',6)
    # actual full E1 status fixture, generated offline
    body=bytearray(25); body[1]=1; body[2]=2; body[5]=0xa1; body[11]=31; body[13]=4; body[18]=72; body[24]=3
    msg=bytearray([0xaa,10+len(body),0xe1,0,0,0,0,0,0,3])+body
    msg.append((-sum(msg[1:]))&255)
    with patch.object(PacketBuilder,'packet_time',staticmethod(lambda: bytes(8))):
        packet=PacketBuilder(123456,msg).finalize()
    response=s.encode_8370(packet,3)
commands={}
for name,cls,attr in [('power',MessagePower,'power'),('child_lock',MessageLock,'lock'),('storage',MessageStorage,'storage')]:
    for v in [False,True]:
        c=cls(0); setattr(c,attr,v); commands[name+str(v)]=c.serialize().hex()
c=MessageWork(0); c.mode=2; commands['mode2']=c.serialize().hex()
out=dict(key=key.hex(),token=token.hex(),reply=reply.hex(),handshake=handshake.hex(),tcp=tcp.hex(),frame=frame.hex(),query=query.hex(),statusPacket=packet.hex(),statusFrame=response.hex(),commands=commands)
p=Path(__file__).resolve().parents[1]/'app/src/test/resources/vectors.json'; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(out,indent=2)+'\n')
print('Fictional vectors generated')


