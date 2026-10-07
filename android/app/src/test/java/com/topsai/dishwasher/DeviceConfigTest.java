package com.topsai.dishwasher;
import com.topsai.dishwasher.model.*;import org.json.*;import org.junit.Test;import static org.junit.Assert.*;
public class DeviceConfigTest{
 public static String fixture()throws Exception{JSONObject v=ProtocolTest.vectors();return new JSONObject().put("device_id",123456).put("ip_address","192.0.2.7").put("port",6444).put("token",v.getString("token")).put("key",v.getString("key")).put("device_protocol",3).put("subtype",3).put("model","7600V1E0").put("name","Fixture").put("customize","").toString();}
 @Test public void acceptsLegacySchemaAndRoundTrip()throws Exception{DeviceConfig c=DeviceConfig.parse(fixture());assertNotNull(c);assertEquals(123456,c.deviceId);assertEquals(6444,c.port);assertArrayEquals(c.key,DeviceConfig.parse(c.toJson()).key);}
 @Test public void rejectsInvalidConfiguration()throws Exception{
  assertThrows(IllegalArgumentException.class,()->DeviceConfig.parse("{}"));assertThrows(IllegalArgumentException.class,()->DeviceConfig.parse("not json"));
  String[] fields={"device_id","port","port","token","key","device_protocol","subtype","device_type","ip_address"};Object[] values={-1,0,65536,"xyz","00",2,1,172,"192.168.1.999"};
  for(int i=0;i<fields.length;i++){String json=new JSONObject(fixture()).put(fields[i],values[i]).toString();assertThrows(fields[i],IllegalArgumentException.class,()->DeviceConfig.parse(json));}
 }
}
