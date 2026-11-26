package com.topsai.dishwasher;
import android.test.InstrumentationTestCase;import android.net.*;import android.content.Context;
import com.topsai.dishwasher.pairing.*;import com.topsai.dishwasher.model.*;import com.topsai.dishwasher.storage.*;import com.topsai.dishwasher.device.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
/** Explicit read-only live verification, never sends physical control commands. */
public class PairingInstrumentedTest extends InstrumentationTestCase {
 public void verifyLiveDiscoveryAndSavedCredentials()throws Exception {
  Context context=getInstrumentation().getTargetContext();ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);Network wifi=null;
  for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){wifi=n;break;}}
  assertNotNull("Wi-Fi required",wifi);DeviceConfig saved=new ConfigStore(context).load();assertNotNull("Existing pairing required",saved);
  List<LanDiscovery.Found> found=LanDiscovery.scan(wifi,cm.getLinkProperties(wifi));LanDiscovery.Found selected=null;for(LanDiscovery.Found f:found)if(f.id==saved.deviceId)selected=f;
  assertNotNull("Saved device must be found by ID",selected);assertTrue(selected.supported());
  DeviceConfig current=new DeviceConfig(saved.deviceId,selected.ip,selected.port,saved.token,saved.key);DeviceSession s=new WifiDeviceSession(current,wifi);
  try{s.connect();assertNotNull(s.query());}finally{s.close();}
 }

 /** Opt-in test. Only the in-memory configuration can be changed by recovery. */
 public void verifyRecoveryFromStaleEndpoint() throws Exception {
  Context context = getInstrumentation().getTargetContext();
  ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
  Network selectedWifi = null;
  for (Network candidate : cm.getAllNetworks()) {
   NetworkCapabilities caps = cm.getNetworkCapabilities(candidate);
   if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
    selectedWifi = candidate;
    break;
   }
  }
  assertNotNull("Wi-Fi required", selectedWifi);
  final Network wifi = selectedWifi;
  ConfigStore existing = new ConfigStore(context);
  DeviceConfig original = existing.load();
  assertNotNull("Existing pairing required", original);
  DeviceConfig stale = new DeviceConfig(original.deviceId, "127.0.0.1", 1, original.token, original.key);

  CountDownLatch loaded = new CountDownLatch(1);
  CountDownLatch saved = new CountDownLatch(1);
  AtomicReference<DeviceConfig> memory = new AtomicReference<>(stale);
  ConfigController settings = new ConfigController(new ConfigController.Persistence() {
   public DeviceConfig load() { return memory.get(); }
   public void save(DeviceConfig updated) {
    memory.set(updated);
    saved.countDown();
   }
  }, Runnable::run);
  settings.subscribe((config, error) -> { if (config == stale) loaded.countDown(); });

  CountDownLatch firstRead = new CountDownLatch(1);
  DeviceRepository first = new DeviceRepository(Runnable::run, status -> {
   if (status.connected && status.state != null) firstRead.countDown();
  }, config -> new WifiDeviceSession(config, wifi),
   config -> LanDiscovery.scan(wifi, cm.getLinkProperties(wifi), config.ipAddress),
   settings::saveRecoveredAddress);
  try {
   assertTrue("Test config must be loaded", loaded.await(5, TimeUnit.SECONDS));
   first.start(stale);
   assertTrue("Status must recover from stale endpoint", firstRead.await(70, TimeUnit.SECONDS));
   assertTrue("Verified endpoint must be saved", saved.await(5, TimeUnit.SECONDS));
  } finally {
   first.shutdown();
   settings.close();
  }

  DeviceConfig recovered = memory.get();
  assertFalse("New IP required", stale.ipAddress.equals(recovered.ipAddress));
  assertTrue("Device identity must be preserved", original.deviceId == recovered.deviceId);
  assertTrue("Pairing material must be preserved",
    Arrays.equals(original.token, recovered.token) && Arrays.equals(original.key, recovered.key));
  CountDownLatch nextRead = new CountDownLatch(1);
  DeviceRepository restart = new DeviceRepository(Runnable::run, status -> {
   if (status.connected && status.state != null) nextRead.countDown();
  }, config -> new WifiDeviceSession(config, wifi));
  try {
   restart.start(recovered);
   assertTrue("Recovered endpoint must work on restart without rediscovery", nextRead.await(15, TimeUnit.SECONDS));
  } finally {
   restart.shutdown();
  }
  DeviceConfig after = existing.load();
  assertTrue("Phone pairing must stay untouched", after != null && original.toJson().equals(after.toJson()));
 }
}
