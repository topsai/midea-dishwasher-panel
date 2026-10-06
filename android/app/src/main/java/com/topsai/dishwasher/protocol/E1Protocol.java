package com.topsai.dishwasher.protocol;
import com.topsai.dishwasher.model.*;import javax.crypto.Cipher;import java.util.*;import java.time.*;
/** E1 uses additive checksum, not CRC8. Ported from midea-lan 2026.9.2 (MIT). */
public final class E1Protocol {
 private static final byte[] AES=V3Codec.hex("6a92ef406bad2f0359baad994171ea6d"),SALT=V3Codec.hex("78686469776a6e6368656b6434643531326368646a783564386534633339344432443753");
 private static byte[] message(int type,int bodyType,byte[] body){
  byte[] m=new byte[12+body.length];m[0]=(byte)0xaa;m[1]=(byte)(m.length-1);m[2]=(byte)0xe1;m[9]=(byte)type;m[10]=(byte)bodyType;System.arraycopy(body,0,m,11,body.length);int sum=0;for(int i=1;i<m.length-1;i++)sum+=m[i]&255;m[m.length-1]=(byte)-sum;return m;
 }
 public static byte[] queryMessage(){return message(3,0,new byte[0]);}
 public static byte[] query(long id){return packet(id,queryMessage());}
 public static byte[] switchCommand(long id,String field,boolean value){
  byte[] b;int type;switch(field){
   case "power":type=8;b=new byte[4];b[0]=(byte)(value?1:0);break;
   case "child_lock":type=0x83;b=new byte[37];b[0]=(byte)(value?3:4);break;
   case "storage":type=0x81;b=new byte[37];b[3]=(byte)(value?1:0);Arrays.fill(b,4,10,(byte)0xff);break;
   default:throw new IllegalArgumentException("只读或不支持的字段");
  }return packet(id,message(2,type,b));
 }
 public static byte[] modeCommand(long id,int mode){if(mode==0||!DishwasherState.MODES.containsKey(mode))throw new IllegalArgumentException("无效的洗涤模式");return packet(id,message(2,8,new byte[]{3,(byte)mode,0,0}));}
 private static byte[] packet(long id,byte[] message){
  byte[] cipher=V3Codec.crypt("AES/ECB/PKCS5Padding",Cipher.ENCRYPT_MODE,AES,message);byte[] p=new byte[40+cipher.length];p[0]=0x5a;p[1]=0x5a;p[2]=1;p[3]=0x11;p[4]=(byte)(p.length+16);p[5]=(byte)((p.length+16)>>8);p[6]=0x20;
  ZonedDateTime t=ZonedDateTime.now(ZoneOffset.UTC);int[] date={t.getNano()/10000000,t.getSecond(),t.getMinute(),t.getHour(),t.getDayOfMonth(),t.getMonthValue(),t.getYear()%100,t.getYear()/100};
  for(int i=0;i<8;i++){p[12+i]=(byte)date[i];p[20+i]=(byte)(id>>(8*i));}System.arraycopy(cipher,0,p,40,cipher.length);return V3Codec.concat(p,V3Codec.digest("MD5",V3Codec.concat(p,SALT)));
 }
 public static byte[] unwrap(byte[] p){
  if(p.length<72||p[0]!=0x5a||p[1]!=0x5a||(((p[4]&255)|((p[5]&255)<<8))!=p.length)||(p.length-56)%16!=0)throw new IllegalArgumentException("设备报文长度错误");
  if(!java.security.MessageDigest.isEqual(Arrays.copyOfRange(p,p.length-16,p.length),V3Codec.digest("MD5",V3Codec.concat(Arrays.copyOf(p,p.length-16),SALT))))throw new IllegalArgumentException("设备报文校验失败");
  byte[] msg=V3Codec.crypt("AES/ECB/PKCS5Padding",Cipher.DECRYPT_MODE,AES,Arrays.copyOfRange(p,40,p.length-16));
  if(msg.length<12||(msg[0]&255)!=0xaa||(msg[2]&255)!=0xe1||(msg[1]&255)!=msg.length-1)throw new IllegalArgumentException("E1 消息格式错误");
  int sum=0;for(int i=1;i<msg.length;i++)sum+=msg[i]&255;if((sum&255)!=0)throw new IllegalArgumentException("E1 校验失败");return msg;
 }
 public static DishwasherState parse(byte[] packet){
  byte[] msg=unwrap(packet);int type=msg[9]&255,bodyType=msg[10]&255;
  if(!((type==2&&bodyType<=7)||((type==3||type==4)&&bodyType==0)))throw new IllegalArgumentException("非 E1 状态消息");
  byte[] b=Arrays.copyOfRange(msg,10,msg.length-1);if(b.length<17)throw new IllegalArgumentException("状态报文不完整");
  Map<String,Object> v=new LinkedHashMap<>();String[] status={"power_off","cancel","delay","running","error","soft_gear"},progress={"idle","pre_wash","wash","rinse","dry","complete"};
  int s=b[1]&255,mode=b[2]&255,pr=b[9]&255;v.put("power",s>0);v.put("status",s<status.length?status[s]:"unknown_"+s);v.put("mode",DishwasherState.MODES.getOrDefault(mode,"unknown_"+mode));v.put("additional",b[3]&255);
  v.put("door",(b[5]&1)==0);v.put("rinse_aid",(b[5]&2)!=0);v.put("salt",(b[5]&4)!=0);v.put("child_lock",(b[5]&16)!=0);v.put("uv",(b[4]&2)!=0);v.put("dry",(b[4]&16)!=0);v.put("dry_status",(b[4]&32)!=0);v.put("storage",(b[5]&32)!=0);v.put("storage_status",(b[5]&64)!=0);
  v.put("time_remaining",(b[6]&255)+(b.length>33?(b[32]&255)*256:0));v.put("progress",pr<progress.length?progress[pr]:"unknown_"+pr);v.put("storage_remaining",b.length>18?b[18]&255:null);v.put("temperature",b[11]&255);v.put("humidity",b.length>33?b[33]&255:null);v.put("waterswitch",(b[4]&4)!=0);v.put("water_lack",(b[5]&128)!=0);v.put("error_code",b[10]&255);v.put("softwater",b[13]&255);v.put("wrong_operation",b[16]&255);v.put("bright",b.length>24?b[24]&255:null);return new DishwasherState(v);
 }
}
