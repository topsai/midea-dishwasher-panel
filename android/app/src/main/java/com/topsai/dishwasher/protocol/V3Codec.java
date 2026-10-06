package com.topsai.dishwasher.protocol;
import javax.crypto.Cipher;import javax.crypto.spec.*;import java.security.*;import java.util.*;
/** E1/V3 LAN crypto ported from midea-lan 2026.9.2 (MIT). */
public final class V3Codec {
 private final byte[] key;private byte[] tcpKey;private int sequence;
 public V3Codec(byte[] key){if(key.length!=32)throw new IllegalArgumentException("认证密钥长度错误");this.key=key.clone();}
 public byte[] handshakeRequest(byte[] token){return frame(token,0);}
 public void acceptHandshake(byte[] reply){
  if(reply.length!=64)throw new IllegalArgumentException("认证失败，请重新导入配对文件");
  byte[] plain=crypt("AES/CBC/NoPadding",Cipher.DECRYPT_MODE,key,Arrays.copyOf(reply,32));
  if(!MessageDigest.isEqual(digest("SHA-256",plain),Arrays.copyOfRange(reply,32,64)))throw new IllegalArgumentException("认证摘要不匹配");
  tcpKey=new byte[32];for(int i=0;i<32;i++)tcpKey[i]=(byte)(plain[i]^key[i]);sequence=0;
 }
 public byte[] encode(byte[] payload){if(tcpKey==null)throw new IllegalStateException("尚未认证");return frame(payload,6);}
 private byte[] frame(byte[] payload,int type){
  boolean encrypted=type==6;int padding=encrypted?(16-(payload.length+2)%16)%16:0;int size=payload.length+padding+(encrypted?32:0);
  if(size>65535)throw new IllegalArgumentException("报文过大");
  byte[] header={(byte)0x83,0x70,(byte)(size>>8),(byte)size,0x20,(byte)(padding<<4|type)};
  byte[] data=new byte[2+payload.length+padding];data[0]=(byte)(sequence>>8);data[1]=(byte)sequence;sequence=(sequence+1)%65535;System.arraycopy(payload,0,data,2,payload.length);
  if(padding>0){byte[] random=new byte[padding];new SecureRandom().nextBytes(random);System.arraycopy(random,0,data,data.length-padding,padding);}
  return encrypted?concat(header,crypt("AES/CBC/NoPadding",Cipher.ENCRYPT_MODE,tcpKey,data),digest("SHA-256",concat(header,data))):concat(header,data);
 }
 public byte[] decode(byte[] frame){
  if(frame.length<8||(frame[0]&255)!=0x83||frame[1]!=0x70||frame[4]!=0x20||frame.length!=(((frame[2]&255)<<8)|(frame[3]&255))+8)throw new IllegalArgumentException("V3 报文格式错误");
  int type=frame[5]&15,padding=(frame[5]&255)>>4;byte[] data=Arrays.copyOfRange(frame,6,frame.length);
  if(type==3||type==6){
   if(tcpKey==null||data.length<48||(data.length-32)%16!=0)throw new IllegalArgumentException("加密报文长度错误");
   byte[] sign=Arrays.copyOfRange(data,data.length-32,data.length);data=crypt("AES/CBC/NoPadding",Cipher.DECRYPT_MODE,tcpKey,Arrays.copyOf(data,data.length-32));
   if(!MessageDigest.isEqual(sign,digest("SHA-256",concat(Arrays.copyOf(frame,6),data))))throw new IllegalArgumentException("报文摘要不匹配");
  }else if(type!=1&&type!=0)throw new IllegalArgumentException("不支持的 V3 消息");
  if(((type==0||type==1)&&padding!=0)||data.length-padding<2)throw new IllegalArgumentException("报文填充错误");
  return Arrays.copyOfRange(data,2,data.length-padding);
 }
 static byte[] crypt(String alg,int mode,byte[] key,byte[] data){try{Cipher c=Cipher.getInstance(alg);SecretKeySpec k=new SecretKeySpec(key,"AES");if(alg.contains("CBC"))c.init(mode,k,new IvParameterSpec(new byte[16]));else c.init(mode,k);return c.doFinal(data);}catch(GeneralSecurityException e){throw new IllegalArgumentException("报文加解密失败");}}
 static byte[] digest(String alg,byte[] data){try{return MessageDigest.getInstance(alg).digest(data);}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
 public static byte[] concat(byte[]... parts){int n=0;for(byte[]p:parts)n+=p.length;byte[] out=new byte[n];int i=0;for(byte[]p:parts){System.arraycopy(p,0,out,i,p.length);i+=p.length;}return out;}
 static byte[] hex(String h){byte[] b=new byte[h.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(h.substring(i*2,i*2+2),16);return b;}
}
