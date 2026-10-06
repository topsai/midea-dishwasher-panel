package com.topsai.dishwasher.device;
import com.topsai.dishwasher.model.*;import com.topsai.dishwasher.protocol.*;import java.net.*;import java.io.*;import java.util.*;
/** One authenticated TCP connection; no command retries. */
public class DeviceSession {
 private final DeviceConfig config;private volatile Socket socket;private final V3Codec codec;private final FrameBuffer frames=new FrameBuffer();private final ArrayDeque<byte[]> received=new ArrayDeque<>();private volatile boolean closed;
 public DeviceSession(DeviceConfig config){this.config=config;codec=new V3Codec(config.key);}
 public void connect()throws IOException{
  Socket s=new Socket();socket=s;if(closed){s.close();throw new IOException("session closed");}
  s.connect(new InetSocketAddress(config.ipAddress,config.port),4000);s.setSoTimeout(4000);s.setTcpNoDelay(true);
  s.getOutputStream().write(codec.handshakeRequest(config.token));byte[] frame=readFrame(System.nanoTime()+4000000000L);
  if((frame[5]&15)!=1)throw new IOException("authentication response missing");
  codec.acceptHandshake(codec.decode(frame));
 }
 private byte[] readFrame(long deadline)throws IOException{
  while(received.isEmpty()){
   long remaining=deadline-System.nanoTime();if(remaining<=0)throw new SocketTimeoutException();
   Socket s=socket;if(closed||s==null)throw new IOException("session closed");s.setSoTimeout((int)Math.max(1,Math.min(4000,remaining/1000000)));
   byte[] chunk=new byte[4096];int n=s.getInputStream().read(chunk);if(n<0)throw new EOFException();received.addAll(frames.feed(Arrays.copyOf(chunk,n)));
  }return received.removeFirst();
 }
 public DishwasherState query()throws IOException{
  send(E1Protocol.query(config.deviceId));long deadline=System.nanoTime()+4000000000L;
  while(true){
   if(System.nanoTime()>deadline)throw new SocketTimeoutException();
   byte[] frame=readFrame(deadline);if((frame[5]&15)!=3)throw new IOException("unexpected response type");byte[] payload=codec.decode(frame);
   if(payload.length>=4&&payload[0]==0x5a&&payload[1]==0x5a){
    int type=(payload[2]&255)|((payload[3]&255)<<8);if(type==0x1001||type==1)continue;
   }
   byte[] msg=E1Protocol.unwrap(payload);int type=msg[9]&255,body=msg[10]&255;
   if((type==2&&body<=7)||((type==3||type==4)&&body==0))return E1Protocol.parse(payload);
   // Valid command acknowledgements do not count as fresh status.
  }
 }
 public void send(byte[] packet)throws IOException{Socket s=socket;if(closed||s==null||!s.isConnected())throw new IOException("not connected");s.getOutputStream().write(codec.encode(packet));s.getOutputStream().flush();}
 public void close(){closed=true;Socket s=socket;if(s!=null)try{s.close();}catch(IOException ignored){}}
}

