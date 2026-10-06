package com.topsai.dishwasher.device;
import com.topsai.dishwasher.model.*;import com.topsai.dishwasher.protocol.*;import java.net.*;import java.util.concurrent.*;
/** Serial network executor with generation-fenced callbacks and at-most-once writes. */
public class DeviceRepository {
 public interface Listener{void onUpdate(Snapshot snapshot);}public interface SessionFactory{DeviceSession open(DeviceConfig config);}
 public static final class Snapshot{
  public final DishwasherState state;public final boolean connected,busy;public final long updatedAtMillis;public final String message;
  public Snapshot(DishwasherState s,boolean c,boolean b,long t,String m){state=s;connected=c;busy=b;updatedAtMillis=t;message=m;}
 }
 private final Executor callbacks;private final Listener listener;private final SessionFactory factory;
 private final ScheduledExecutorService network=Executors.newSingleThreadScheduledExecutor();
 private ScheduledFuture<?> polling;private DeviceConfig config;private DeviceSession session;private boolean active,connected,busy,refreshPending;private long generation,updatedAt;private DishwasherState state;private String message="未连接";
 public DeviceRepository(Executor callbackExecutor,Listener listener,SessionFactory factory){callbacks=callbackExecutor;this.listener=listener;this.factory=factory;}
 public synchronized void start(DeviceConfig c){
  stop();config=c;active=true;long g=++generation;message="正在连接";emit(g);
  polling=network.scheduleWithFixedDelay(()->read(g),0,5,TimeUnit.SECONDS);
 }
 public synchronized void stop(){stop("已停止连接 · 可能过期");}
 public synchronized void stop(String reason){
  active=false;generation++;if(polling!=null)polling.cancel(false);polling=null;
  if(session!=null)session.close();session=null;connected=false;busy=false;refreshPending=false;message=reason;emit(generation);
 }
 public synchronized void shutdown(){stop();network.shutdownNow();}
 public synchronized void refresh(){
  if(!active||refreshPending||busy)return;refreshPending=true;long g=generation;
  network.execute(()->{try{read(g);}finally{synchronized(this){if(g==generation)refreshPending=false;}}});
 }
 private void read(long g){
  DeviceSession current;
  synchronized(this){if(!active||g!=generation)return;if(session==null)session=factory.open(config);current=session;}
  try{
   synchronized(this){if(!active||g!=generation)return;}
   boolean needsConnect;synchronized(this){needsConnect=!connected;}
   if(needsConnect)current.connect();DishwasherState next=current.query();
   synchronized(this){if(!active||g!=generation)return;state=next;updatedAt=System.currentTimeMillis();connected=true;message="已连接 · 手机直连";emit(g);}
  }catch(Exception e){fail(g,e,false);}
 }
 public synchronized void submitSwitch(String field,boolean value){requireReady();submit(E1Protocol.switchCommand(config.deviceId,field,value));}
 public synchronized void submitMode(int mode){requireReady();submit(E1Protocol.modeCommand(config.deviceId,mode));}
 private void requireReady(){if(!active||!connected||busy)throw new IllegalStateException("设备未连接或操作正在进行");}
 private void submit(byte[] packet){
  long g=generation;DeviceSession current=session;busy=true;message="正在发送";emit(g);
  network.execute(()->{
   try{
    synchronized(this){if(!active||g!=generation)return;}
    current.send(packet);
    synchronized(this){if(!active||g!=generation)return;message="已发送，等待设备状态";emit(g);}
    read(g);
   }catch(Exception e){fail(g,e,true);}
   finally{synchronized(this){if(g==generation){busy=false;emit(g);}}}
  });
 }
 private synchronized void fail(long g,Exception error,boolean write){
  if(!active||g!=generation)return;
  if(session!=null)session.close();session=null;connected=false;
  String reason=error instanceof SocketTimeoutException?"读取超时":error instanceof IllegalArgumentException?"认证或报文校验失败":"连接失败，请检查手机 Wi-Fi、设备 IP 和局域网";
  message=(write?"发送失败，未自动重试 · ":"")+reason+" · 可能过期";emit(g);
 }
 private synchronized void emit(long g){
  Snapshot snapshot=new Snapshot(state,connected,busy,updatedAt,message);
  callbacks.execute(()->{synchronized(DeviceRepository.this){if(g!=generation)return;listener.onUpdate(snapshot);}});
 }
}
