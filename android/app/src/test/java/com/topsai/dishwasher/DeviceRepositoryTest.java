package com.topsai.dishwasher;
import com.topsai.dishwasher.pairing.LanDiscovery;
import com.topsai.dishwasher.device.*;import com.topsai.dishwasher.model.*;import org.junit.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.util.function.*;import static org.junit.Assert.*;
public class DeviceRepositoryTest {
 DeviceRepository r;DeviceConfig config=new DeviceConfig(123456,"127.0.0.1",6444,new byte[64],new byte[32]);volatile DeviceRepository.Snapshot latest;
 static void await(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(7);while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(10);assertTrue("condition reached",condition.getAsBoolean());}
 class FakeSession extends DeviceSession{
  AtomicInteger queries=new AtomicInteger(),writes=new AtomicInteger();volatile boolean failRead,failWrite,closed,blockWrite;final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  FakeSession(){super(config);}public void connect(){}
  public DishwasherState query()throws IOException{queries.incrementAndGet();if(failRead)throw new IOException("offline");return new DishwasherState(Map.of("temperature",31,"time_remaining",0));}
  public void send(byte[] p)throws IOException{writes.incrementAndGet();entered.countDown();if(blockWrite)try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IOException();}if(failWrite)throw new IOException("write failed");}
  public void close(){closed=true;release.countDown();}
 }
 void init(FakeSession session){r=new DeviceRepository(Runnable::run,s->latest=s,c->session);r.start(config);}
 @After public void cleanup(){if(r!=null)r.shutdown();}
 @Test public void specificStopReasonSurvivesQueuedCallback()throws Exception{List<Runnable> callbacks=new CopyOnWriteArrayList<>();FakeSession s=new FakeSession();r=new DeviceRepository(callbacks::add,x->latest=x,c->s);r.start(config);await(()->s.queries.get()>0);r.stop("Wi-Fi 未连接，请连接家庭局域网");for(Runnable cb:callbacks)cb.run();assertFalse(latest.connected);assertEquals("Wi-Fi 未连接，请连接家庭局域网",latest.message);}
 @Test public void pollsEveryFiveSecondsOnlyWhileStarted()throws Exception{FakeSession s=new FakeSession();init(s);await(()->latest!=null&&latest.connected);assertEquals(1,s.queries.get());await(()->s.queries.get()==2);r.stop();int count=s.queries.get();Thread.sleep(150);assertEquals(count,s.queries.get());assertTrue(s.closed);}
 @Test public void timeoutKeepsStaleState()throws Exception{FakeSession s=new FakeSession();init(s);await(()->latest!=null&&latest.connected);long time=latest.updatedAtMillis;s.failRead=true;r.refresh();await(()->!latest.connected);assertEquals(31,latest.state.values.get("temperature"));assertEquals(time,latest.updatedAtMillis);assertThrows(IllegalStateException.class,()->r.submitSwitch("power",true));}
 @Test public void failedWriteNeverReplays()throws Exception{FakeSession s=new FakeSession();init(s);await(()->latest!=null&&latest.connected);s.failWrite=true;r.submitSwitch("storage",true);await(()->!latest.connected);r.refresh();await(()->latest.connected);assertEquals(1,s.writes.get());assertEquals(31,latest.state.values.get("temperature"));}
 @Test public void duplicateSubmissionRejected()throws Exception{FakeSession s=new FakeSession();init(s);await(()->latest!=null&&latest.connected);s.blockWrite=true;r.submitMode(2);assertTrue(s.entered.await(2,TimeUnit.SECONDS));assertThrows(IllegalStateException.class,()->r.submitMode(2));s.release.countDown();await(()->!latest.busy);assertEquals(1,s.writes.get());}
 @Test public void lateCallbackIgnoredAfterStop()throws Exception{List<Runnable> callbacks=new CopyOnWriteArrayList<>();FakeSession s=new FakeSession();r=new DeviceRepository(callbacks::add,x->latest=x,c->s);r.start(config);await(()->s.queries.get()>0);r.stop();for(Runnable cb:callbacks)cb.run();assertNotNull(latest);assertFalse(latest.connected);assertTrue(s.closed);}

 private LanDiscovery.Found found(long id, String ip, String model) {
  return new LanDiscovery.Found(id,ip,6444,model,225,3);
 }
 @Test public void staleAddressRecoversOnlyMatchingPairedModelAndSavesAfterQuery() throws Exception {
  FakeSession old=new FakeSession(), recovered=new FakeSession();
  old.failRead=true;
  List<DeviceConfig> opened=new CopyOnWriteArrayList<>();
  AtomicReference<DeviceConfig> persisted=new AtomicReference<>();
  r=new DeviceRepository(Runnable::run, s -> latest=s, c -> {
   opened.add(c);
   return c == config ? old : recovered;
  }, c -> Arrays.asList(found(42,"192.0.2.42","7600V1E0"),
                        found(c.deviceId,"192.0.2.51","other"),
                        found(c.deviceId,"192.0.2.7","7600V1E0")),
     (previous,next) -> {
      assertSame(config,previous);
      assertEquals(1,recovered.queries.get());
      persisted.set(next);
     });
  r.start(config);
  await(() -> latest != null && latest.connected);
  assertEquals(2,opened.size());
  assertSame(opened.get(1),persisted.get());
  assertEquals("192.0.2.7",persisted.get().ipAddress);
  assertArrayEquals(config.token,persisted.get().token);
  assertArrayEquals(config.key,persisted.get().key);
  assertTrue(old.closed);
  assertEquals(0,recovered.writes.get());
 }

 @Test public void unverifiedEndpointIsNotSavedAndDiscoveryIsRateLimited() throws Exception {
  FakeSession session=new FakeSession();session.failRead=true;
  AtomicInteger scans=new AtomicInteger(), saved=new AtomicInteger();
  r=new DeviceRepository(Runnable::run, s -> latest=s, c -> session, c -> {
   scans.incrementAndGet();
   return Collections.singletonList(found(c.deviceId,"192.0.2.7","7600V1E0"));
  }, (old,next) -> saved.incrementAndGet());
  r.start(config);
  await(() -> session.queries.get() >= 2);
  r.refresh();
  await(() -> session.queries.get() >= 3);
  assertEquals(0,saved.get());assertEquals(1,scans.get());assertFalse(latest.connected);
 }

 @Test public void stoppingDuringLookupDiscardsLateEndpoint() throws Exception {
  FakeSession old=new FakeSession();old.failRead=true;
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);
  AtomicInteger opens=new AtomicInteger(), saves=new AtomicInteger();
  r=new DeviceRepository(Runnable::run, s -> latest=s, c -> {opens.incrementAndGet();return old;},
      c -> {entered.countDown();release.await(2,TimeUnit.SECONDS);done.countDown();
            return Collections.singletonList(found(c.deviceId,"192.0.2.7","7600V1E0"));},
      (previous,next) -> saves.incrementAndGet());
  r.start(config);
  assertTrue(entered.await(3,TimeUnit.SECONDS));r.stop();release.countDown();
  assertTrue(done.await(2,TimeUnit.SECONDS));
  // Starting another generation queues work behind the completed lookup on the serial network thread.
  old.failRead=false;r.start(config);
  await(() -> latest.connected);
  assertEquals(2,opens.get());assertEquals(0,saves.get());
 }

 @Test public void foregroundPollingResumesWhenWifiBecomesAvailable() throws Exception {
  AtomicBoolean wifiAvailable=new AtomicBoolean(false);
  AtomicInteger scans=new AtomicInteger();
  FakeSession working=new FakeSession();
  r=new DeviceRepository(Runnable::run, s -> latest=s,
      c -> wifiAvailable.get() ? working : new WifiDeviceSession(c,null),
      c -> {scans.incrementAndGet();return Collections.emptyList();},
      (previous,next) -> fail("unexpected address change"));
  r.start(config);
  await(() -> latest != null && latest.message.contains("Wi-Fi 未连接"));
  wifiAvailable.set(true);
  await(() -> latest.connected);
  assertEquals(0,scans.get());assertEquals(0,working.writes.get());
 }
}
