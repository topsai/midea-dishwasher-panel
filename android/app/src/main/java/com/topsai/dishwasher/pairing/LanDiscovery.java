package com.topsai.dishwasher.pairing;
import android.net.*;
import com.topsai.dishwasher.model.DeviceConfig;
import javax.crypto.Cipher;import javax.crypto.spec.SecretKeySpec;
import java.net.*;import java.util.*;
import java.nio.charset.StandardCharsets;

/** Read-only UDP discovery, ported from midea-lan (MIT), strictly filtered before pairing. */
public final class LanDiscovery {
 public static final class Found {
  public final long id;public final String ip,model;public final int port,type,protocol;
  public Found(long id,String ip,int port,String model,int type,int protocol){this.id=id;this.ip=ip;this.port=port;this.model=model;this.type=type;this.protocol=protocol;}
  public boolean supported(){return type==225&&protocol==3&&model.equals("7600V1E0");}
  @Override public String toString(){return model+" · "+ip+" · ID "+id+(supported()?"":"（暂不支持）");}
 }
 public static Found parse(byte[] bytes,String ip){
  try{
   byte[] data=bytes;int protocol=2;
   if(data.length>=10&&(data[0]&255)==0x83&&data[1]==0x70){protocol=3;if(data[8]!=0x5a||data[9]!=0x5a)return null;data=Arrays.copyOfRange(data,8,data.length-16);}
   if(data.length<72||data[0]!=0x5a||data[1]!=0x5a)return null;
   long id=0;for(int i=0;i<6;i++)id|=(data[20+i]&255L)<<(i*8);
   Cipher cipher=Cipher.getInstance("AES/ECB/PKCS5Padding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(unhex("6a92ef406bad2f0359baad994171ea6d"),"AES"));byte[] plain=cipher.doFinal(Arrays.copyOfRange(data,40,data.length-16));
   if(plain.length<41)return null;int n=plain[40]&255;if(n==0||41+n>plain.length)return null;
   String[] ssid=new String(plain,41,n,StandardCharsets.US_ASCII).split("_");if(ssid.length<2)return null;
   int port=0;for(int i=0;i<4;i++)port|=(plain[4+i]&255)<<(i*8);
   String model=new String(plain,17,8,StandardCharsets.US_ASCII);int type=Integer.parseInt(ssid[1],16);
   if(id<=0||port<1||port>65535)return null;
   return new Found(id,ip,port,model,type,protocol);
  }catch(Exception e){return null;}
 }
 public static byte[] unhex(String s){byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(2*i,2*i+2),16);return b;}
 public static List<Found> scan(Network wifi,LinkProperties links)throws Exception{
  return scan(wifi,links,null);
 }
 public static List<Found> scan(Network wifi,LinkProperties links,String hint)throws Exception{
  if(wifi==null)throw new IllegalArgumentException("请连接洗碗机所在 Wi-Fi");
  Set<String> broadcasts=new LinkedHashSet<>();broadcasts.add("255.255.255.255");
  if(hint!=null)try{new DeviceConfig(1,hint,6444,new byte[64],new byte[32]);broadcasts.add(hint);}catch(IllegalArgumentException ignored){}
  if(links!=null)for(LinkAddress link:links.getLinkAddresses())if(link.getAddress() instanceof Inet4Address){byte[] b=link.getAddress().getAddress();int prefix=link.getPrefixLength();for(int i=prefix;i<32;i++)b[i/8]|=(byte)(1<<(7-i%8));broadcasts.add(InetAddress.getByAddress(b).getHostAddress());}
  byte[] message=unhex("5a5a01114800920000000000000000000000000000000000000000000000000000000000000000007f75bd6b3e4f8b762e849c6e578d6590036e9d4342a50f1f569eb8ec918e92e5");
  Map<Long,Found> found=new LinkedHashMap<>();long deadline=System.nanoTime()+6000000000L;
  try(DatagramSocket socket=new DatagramSocket(null)){
   socket.bind(new InetSocketAddress(0));wifi.bindSocket(socket);socket.setBroadcast(true);
   for(String ip:broadcasts)for(int port:new int[]{6445,20086})try{socket.send(new DatagramPacket(message,message.length,InetAddress.getByName(ip),port));}catch(java.io.IOException ignored){}
   while(System.nanoTime()<deadline){socket.setSoTimeout((int)Math.max(1,(deadline-System.nanoTime())/1000000));DatagramPacket packet=new DatagramPacket(new byte[2048],2048);try{socket.receive(packet);}catch(SocketTimeoutException e){break;}Found f=parse(Arrays.copyOf(packet.getData(),packet.getLength()),packet.getAddress().getHostAddress());if(f!=null&&f.type==225)found.put(f.id,f);}
  }return new ArrayList<>(found.values());
 }
 private LanDiscovery(){}
}
