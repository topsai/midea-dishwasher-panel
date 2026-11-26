package com.topsai.dishwasher;
import com.topsai.dishwasher.storage.*;import com.topsai.dishwasher.model.*;import java.io.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import org.junit.Test;import static org.junit.Assert.*;
public class ConfigControllerTest{
 @Test public void blockedImportPublishesToReplacementActivity()throws Exception{
  DeviceConfig initial=DeviceConfig.parse(DeviceConfigTest.fixture());AtomicReference<DeviceConfig> disk=new AtomicReference<>(initial);
  ConfigController c=new ConfigController(new ConfigController.Persistence(){public DeviceConfig load(){return disk.get();}public void save(DeviceConfig next){disk.set(next);}},Runnable::run);
  AtomicReference<DeviceConfig> oldView=new AtomicReference<>(),newView=new AtomicReference<>();
  ConfigController.Listener old=(cfg,error)->oldView.set(cfg),next=(cfg,error)->newView.set(cfg);
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  try{
   c.subscribe(old);DeviceRepositoryTest.await(()->oldView.get()!=null);
   String imported=initial.toJson().replace("192.0.2.7","192.168.1.8");
   c.importJson(()->{entered.countDown();try{if(!release.await(2,TimeUnit.SECONDS))throw new IOException();}catch(InterruptedException e){throw new IOException();}return new ByteArrayInputStream(imported.getBytes(java.nio.charset.StandardCharsets.UTF_8));});
   assertTrue(entered.await(2,TimeUnit.SECONDS));c.unsubscribe(old);c.subscribe(next);release.countDown();
   DeviceRepositoryTest.await(()->newView.get()!=null&&newView.get().ipAddress.equals("192.168.1.8"));
   assertEquals("192.168.1.8",disk.get().ipAddress);assertEquals("192.0.2.7",oldView.get().ipAddress);
  }finally{release.countDown();c.close();}
 }

 @Test public void storageFailureKeepsVerifiedAddressForCurrentSession() throws Exception {
  DeviceConfig initial=DeviceConfig.parse(DeviceConfigTest.fixture());
  DeviceConfig recovered=new DeviceConfig(initial.deviceId,"192.0.2.10",6444,initial.token,initial.key);
  AtomicInteger attempts=new AtomicInteger();
  ConfigController.Persistence store=new ConfigController.Persistence() {
   public DeviceConfig load(){return initial;}
   public void save(DeviceConfig cfg) throws IOException {
    attempts.incrementAndGet();throw new IOException("storage unavailable");
   }
  };
  try (ConfigController controller=new ConfigController(store,Runnable::run)) {
   CountDownLatch warning=new CountDownLatch(1);
   controller.subscribe((cfg,error) -> {if (cfg==recovered && error!=null) warning.countDown();});
   controller.saveRecoveredAddress(initial,recovered);
   assertTrue(warning.await(2,TimeUnit.SECONDS));
   assertEquals(1,attempts.get());
  }
 }

 @Test public void staleRecoveryCannotReplaceNewManualConfiguration() throws Exception {
  DeviceConfig previous=DeviceConfig.parse(DeviceConfigTest.fixture());
  DeviceConfig manual=new DeviceConfig(previous.deviceId,"192.0.2.10",6444,previous.token,previous.key);
  DeviceConfig verified=new DeviceConfig(previous.deviceId,"192.0.2.11",6444,previous.token,previous.key);
  AtomicReference<DeviceConfig> disk=new AtomicReference<>(previous);
  AtomicInteger writes=new AtomicInteger();
  ConfigController.Persistence store=new ConfigController.Persistence() {
   public DeviceConfig load(){return disk.get();}
   public void save(DeviceConfig cfg){disk.set(cfg);writes.incrementAndGet();}
  };
  try (ConfigController controller=new ConfigController(store,Runnable::run)) {
   CountDownLatch saved=new CountDownLatch(1);
   controller.subscribe((cfg,error) -> {if (cfg==verified) saved.countDown();});
   controller.save(manual);
   controller.saveRecoveredAddress(previous,previous);
   controller.saveRecoveredAddress(manual,verified);
   assertTrue(saved.await(2,TimeUnit.SECONDS));
   assertSame(verified,disk.get());assertEquals(2,writes.get());
  }
 }
}
