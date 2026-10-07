package com.topsai.dishwasher;
import com.topsai.dishwasher.pairing.MeijuClient;
import com.topsai.dishwasher.pairing.LanDiscovery;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PairingTest {
 @Test public void cryptoAndDiscoveryMatchPythonVectors() throws Exception {
  JSONObject v=new JSONObject(new String(getClass().getResourceAsStream("/pairing-vectors.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
  assertEquals(v.getString("sign"),MeijuClient.sign("hello","1700000000"));
  assertEquals(v.getString("udp1"),MeijuClient.udpId(123456,1));assertEquals(v.getString("udp2"),MeijuClient.udpId(123456,2));
  byte[] packet=LanDiscovery.unhex(v.getString("discovery"));LanDiscovery.Found f=LanDiscovery.parse(packet,"192.0.2.7");
  assertNotNull(f);assertEquals(123456,f.id);assertEquals(6444,f.port);assertTrue(f.supported());
  assertNull(LanDiscovery.parse(Arrays.copyOf(packet,60),"192.0.2.7"));
  assertFalse(new LanDiscovery.Found(123456,"192.0.2.7",6444,"OTHER",225,3).supported());
 }
 @Test public void loginAndV2RequestAreAccountScoped() throws Exception {
  List<String> endpoints=new ArrayList<>();
  MeijuClient c=new MeijuClient("test-user","test-password",(endpoint,data,headers)->{
   endpoints.add(endpoint);
   assertTrue(headers.containsKey("sign"));
   if(endpoint.contains("login/id"))return new JSONObject().put("loginId","123");
   if(endpoint.equals("/mj/user/login")){
    assertFalse(data.toString().contains("test-password"));
    return new JSONObject().put("mdata",new JSONObject().put("accessToken","session"));
   }
   if(endpoint.contains("homegroup"))return new JSONObject().put("homeList",new JSONArray().put(new JSONObject().put("homegroupId","1")));
   assertEquals("/v2/iot/secure/getToken",endpoint);
   assertEquals("1",data.getString("homegroupId"));
   assertEquals("123456",data.getJSONArray("applianceCodes").getString(0));
   assertEquals("session",headers.get("accessToken"));
   return new JSONObject().put("tokenlist",new JSONArray().put(new JSONObject().put("udpId",data.getString("udpid")).put("token","ab".repeat(64)).put("key","cd".repeat(32))));
  });
  assertEquals(2,c.fetchKeys(123456).size());
  assertEquals("/v1/user/login/id/get",endpoints.get(0));
 }
 @Test public void cloudErrorDoesNotExposePayload() {
  MeijuClient c=new MeijuClient("account","password",(e,d,h)->{throw new Exception("secret-password-token");});
  Exception failure=assertThrows(Exception.class,()->c.fetchKeys(123456));
  assertFalse(failure.toString().contains("secret-password-token"));
 }
 @Test public void rejectsEmptyAccountWithoutNetwork() {
  assertThrows(IllegalArgumentException.class,()->new MeijuClient("","p",(e,d,h)->{fail();return null;}));
 }
}
