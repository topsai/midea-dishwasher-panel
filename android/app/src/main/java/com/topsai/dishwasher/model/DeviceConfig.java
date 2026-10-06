package com.topsai.dishwasher.model;
import org.json.JSONObject;
/** Validated legacy JSON config. toString never contains pairing data. */
public final class DeviceConfig {
 public final long deviceId;public final String ipAddress;public final int port;public final byte[] token,key;
 public DeviceConfig(long id,String ip,int port,byte[] token,byte[] key){
  if(id<=0||id>0xffffffffffffL||port<1||port>65535||token==null||token.length!=64||key==null||key.length!=32||!validIp(ip))throw new IllegalArgumentException("设备配置无效，请检查 IP、ID、端口和配对文件");
  deviceId=id;ipAddress=ip;this.port=port;this.token=token.clone();this.key=key.clone();
 }
 private static boolean validIp(String ip){
  if(ip==null)return false;String[] parts=ip.split("\\.",-1);if(parts.length!=4)return false;
  for(String p:parts){if(!p.matches("[0-9]{1,3}"))return false;int n=Integer.parseInt(p);if(n>255)return false;}return !ip.equals("0.0.0.0");
 }
 private static long integer(JSONObject o,String name)throws Exception{String s=o.get(name).toString();if(!s.matches("[0-9]+"))throw new IllegalArgumentException();return Long.parseLong(s);}
 private static byte[] hex(String text,int length){
  if(text==null||text.length()!=length*2||!text.matches("[0-9a-fA-F]+"))throw new IllegalArgumentException();
  byte[] out=new byte[length];for(int i=0;i<length;i++)out[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);return out;
 }
 public static DeviceConfig parse(String json){
  try{
   if(json==null||json.length()>32768)throw new IllegalArgumentException();
   JSONObject o=new JSONObject(json);
   if(integer(o,"device_protocol")!=3||integer(o,"subtype")!=3||(o.has("device_type")&&integer(o,"device_type")!=225)||(o.has("model")&&!o.getString("model").equals("7600V1E0")))throw new IllegalArgumentException();
   long port=integer(o,"port");if(port>65535)throw new IllegalArgumentException();
   return new DeviceConfig(integer(o,"device_id"),o.getString("ip_address"),(int)port,hex(o.getString("token"),64),hex(o.getString("key"),32));
  }catch(Exception e){throw new IllegalArgumentException("配对文件格式无效：需要 E1/V3、subtype 3、有效 IP/ID/端口及完整 Token/Key");}
 }
 private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return s.toString();}
 public String toJson(){
  try{return new JSONObject().put("device_id",deviceId).put("ip_address",ipAddress).put("port",port).put("token",hex(token)).put("key",hex(key)).put("device_protocol",3).put("subtype",3).put("model","7600V1E0").toString();}
  catch(Exception e){throw new IllegalStateException("配置序列化失败");}
 }
}

