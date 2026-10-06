package com.topsai.dishwasher;
import com.topsai.dishwasher.protocol.*;
import com.topsai.dishwasher.model.*;
import org.junit.Test;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
public class ProtocolTest {
 public static JSONObject vectors() throws Exception { return new JSONObject(new String(ProtocolTest.class.getResourceAsStream("/vectors.json").readAllBytes(),StandardCharsets.UTF_8)); }
 public static byte[] hex(String h) { byte[] b=new byte[h.length()/2]; for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(h.substring(i*2,i*2+2),16); return b; }
 @Test public void goldenHandshakeAndFramesMatch() throws Exception { JSONObject v=vectors(); V3Codec c=new V3Codec(hex(v.getString("key"))); assertArrayEquals(hex(v.getString("handshake")),c.handshakeRequest(hex(v.getString("token")))); c.acceptHandshake(hex(v.getString("reply"))); assertArrayEquals("hello dishwasher".getBytes(StandardCharsets.UTF_8),c.decode(hex(v.getString("frame")))); assertArrayEquals(hex(v.getString("query")),E1Protocol.queryMessage()); }
 @Test public void e1StatusPreservesMissingValues() throws Exception { DishwasherState s=E1Protocol.parse(hex(vectors().getString("statusPacket"))); assertEquals(24,s.values.size()); assertNull(s.values.get("humidity")); assertEquals(0,s.values.get("time_remaining")); assertEquals(31,s.values.get("temperature")); assertEquals("strong_wash",s.values.get("mode")); }
 @Test public void rejectsReadonlyAndInvalidModes() { assertThrows(IllegalArgumentException.class,()->E1Protocol.switchCommand(1,"temperature",true)); assertThrows(IllegalArgumentException.class,()->E1Protocol.modeCommand(1,0)); assertThrows(IllegalArgumentException.class,()->E1Protocol.modeCommand(1,99)); }
 @Test public void commandBytesMatchPython() throws Exception { JSONObject commands=vectors().getJSONObject("commands"); for(String f:new String[]{"power","child_lock","storage"})for(boolean v:new boolean[]{false,true})assertArrayEquals(hex(commands.getString(f+(v?"True":"False"))),E1Protocol.unwrap(E1Protocol.switchCommand(123456,f,v))); assertArrayEquals(hex(commands.getString("mode2")),E1Protocol.unwrap(E1Protocol.modeCommand(123456,2))); }
 @Test public void rejectsBadLengthDigestAndChecksum() throws Exception { JSONObject v=vectors(); V3Codec c=new V3Codec(hex(v.getString("key"))); byte[] reply=hex(v.getString("reply")); reply[40]^=1; assertThrows(IllegalArgumentException.class,()->c.acceptHandshake(reply)); c.acceptHandshake(hex(v.getString("reply"))); byte[] frame=hex(v.getString("frame")); frame[frame.length-1]^=1; assertThrows(IllegalArgumentException.class,()->c.decode(frame)); byte[] packet=hex(v.getString("statusPacket")); packet[45]^=1; assertThrows(IllegalArgumentException.class,()->E1Protocol.parse(packet)); }
}
