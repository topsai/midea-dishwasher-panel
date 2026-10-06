package com.topsai.dishwasher;
import com.topsai.dishwasher.model.DishwasherState;
import com.topsai.dishwasher.protocol.E1Protocol;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;
public class StorageDisplayTest {
 @Test public void toggleRequiresReportedBooleanState() {
  assertEquals(Boolean.FALSE,DishwasherState.storageToggleTarget(true));
  assertEquals(Boolean.TRUE,DishwasherState.storageToggleTarget(false));
  assertNull(DishwasherState.storageToggleTarget(null));
  assertNull(DishwasherState.storageToggleTarget("unknown"));
 }
 @Test public void capturedEnabledStorageCanBeIdle() throws Exception {
  JSONObject packets=new JSONObject(new String(getClass().getResourceAsStream("/storage-status.json").readAllBytes(),StandardCharsets.UTF_8));
  DishwasherState off=E1Protocol.parse(ProtocolTest.hex(packets.getString("off")));
  DishwasherState on=E1Protocol.parse(ProtocolTest.hex(packets.getString("on")));
  assertEquals(false,off.values.get("storage"));assertEquals(0,off.values.get("storage_remaining"));
  assertEquals(true,on.values.get("storage"));assertEquals(false,on.values.get("storage_status"));assertEquals(72,on.values.get("storage_remaining"));
 }
 @Test public void inactiveStorageDoesNotSayDisabled() {
  assertEquals("当前未运行",DishwasherState.storageActivityText(false));
  assertEquals("运行中",DishwasherState.storageActivityText(true));
  assertEquals("未上报",DishwasherState.storageActivityText(null));
 }
}
