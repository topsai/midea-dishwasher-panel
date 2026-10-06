package com.topsai.dishwasher;
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
 @Test public void pollsEveryFiveSecondsOnlyWhileStarted()throws Exception{FakeSession s=new FakeSession();init(s);await(()->latest!=null&&latest.connected);assertEquals(1,s.queries.get());await(()->s.queries.get()==2);r.stop();int count=s.queries.get();Thread.sleep(150);assertEquals(count,s.queries.get());assertTrue(s.closed);}
 @Test public void timeoutKeepsStaleState()throws Exception{FakeSession s=new FakeSession();init(s);await(()->latest!=null&&latest.connected);long time=latest.updatedAtMillis;s.failRead=true;r.refresh();await(()->!latest.connected);assertEquals(31,latest.state.values.get("temperature"));assertEquals(time,latest.updatedAtMillis);assertThrows(IllegalStateException.class,()->r.submitSwitch("power",true));}
 @Test public void failedWriteNeverReplays()throws Exception{FakeSession s=new FakeSession();init(s);await(()->latest!=null&&latest.connected);s.failWrite=true;r.submitSwitch("storage",true);await(()->!latest.connected);r.refresh();await(()->latest.connected);assertEquals(1,s.writes.get());assertEquals(31,latest.state.values.get("temperature"));}
 @Test public void duplicateSubmissionRejected()throws Exception{FakeSession s=new FakeSession();init(s);await(()->latest!=null&&latest.connected);s.blockWrite=true;r.submitMode(2);assertTrue(s.entered.await(2,TimeUnit.SECONDS));assertThrows(IllegalStateException.class,()->r.submitMode(2));s.release.countDown();await(()->!latest.busy);assertEquals(1,s.writes.get());}
 @Test public void lateCallbackIgnoredAfterStop()throws Exception{List<Runnable> callbacks=new CopyOnWriteArrayList<>();FakeSession s=new FakeSession();r=new DeviceRepository(callbacks::add,x->latest=x,c->s);r.start(config);await(()->s.queries.get()>0);r.stop();for(Runnable cb:callbacks)cb.run();assertNotNull(latest);assertFalse(latest.connected);assertTrue(s.closed);}
}

