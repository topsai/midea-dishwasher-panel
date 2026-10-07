package com.topsai.dishwasher;
import com.topsai.dishwasher.device.*;import com.topsai.dishwasher.model.*;import com.topsai.dishwasher.protocol.*;import java.net.*;import java.io.*;import org.junit.Test;import static org.junit.Assert.*;
public class DeviceSessionTest {
 @Test public void coalescedStatusesDoNotSatisfyFutureQuery()throws Exception{
  byte[] key=ProtocolTest.hex(ProtocolTest.vectors().getString("key")),token=ProtocolTest.hex(ProtocolTest.vectors().getString("token")),reply=ProtocolTest.hex(ProtocolTest.vectors().getString("reply")),status=ProtocolTest.hex(ProtocolTest.vectors().getString("statusFrame"));
  byte[] handshake=V3Codec.concat(new byte[]{(byte)0x83,0x70,0,64,0x20,1,0,0},reply);
  byte[] ack=ProtocolTest.hex(ProtocolTest.vectors().getString("ackFrame"));
  InputStream input=new InputStream(){byte[][] chunks={handshake,V3Codec.concat(status,status,ack)};int index;public int read(){throw new UnsupportedOperationException();}public int read(byte[] b,int off,int len){if(index==chunks.length)return -1;byte[] c=chunks[index++];System.arraycopy(c,0,b,off,c.length);return c.length;}public int available(){return 0;}};
  Socket socket=new Socket(){public void connect(SocketAddress a,int timeout){}public void setSoTimeout(int n){}public void setTcpNoDelay(boolean b){}public boolean isConnected(){return true;}public InputStream getInputStream(){return input;}public OutputStream getOutputStream(){return new ByteArrayOutputStream();}};
  DeviceSession session=new DeviceSession(new DeviceConfig(123456,"127.0.0.1",6444,token,key)){protected Socket openSocket(){return socket;}};
  session.connect();try{assertEquals(31,session.query().values.get("temperature"));}catch(IOException e){fail("valid coalesced status followed by acknowledgement was lost");}assertThrows(IOException.class,session::query);
 }
}
