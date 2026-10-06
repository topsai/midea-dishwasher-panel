package com.topsai.dishwasher.storage;
import com.topsai.dishwasher.model.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.*;
/** Process-owned serial configuration operations; listeners follow the current Activity. */
public final class ConfigController implements AutoCloseable {
 public interface Persistence{DeviceConfig load()throws IOException;void save(DeviceConfig config)throws IOException;}
 public interface Listener{void onConfig(DeviceConfig config,String error);}
 public interface InputSource{InputStream open()throws IOException;}
 private final Persistence persistence;private final Executor callbacks;private final ExecutorService files=Executors.newSingleThreadExecutor();private final Set<Listener> listeners=new HashSet<>();
 private DeviceConfig current;private String error;private boolean ready;
 public ConfigController(Persistence persistence,Executor callbacks){this.persistence=persistence;this.callbacks=callbacks;files.execute(()->{try{publish(persistence.load(),null);}catch(Exception e){publish(null,"配置读取失败，请重新导入配对文件");}});}
 public synchronized void subscribe(Listener listener){listeners.add(listener);if(ready)notifyOne(listener,current,error);}
 public synchronized void unsubscribe(Listener listener){listeners.remove(listener);}
 private synchronized void notifyOne(Listener listener,DeviceConfig config,String error){callbacks.execute(()->{synchronized(ConfigController.this){if(listeners.contains(listener))listener.onConfig(config,error);}});}
 private synchronized void publish(DeviceConfig config,String error){current=config;this.error=error;ready=true;for(Listener listener:new ArrayList<>(listeners))notifyOne(listener,config,error);}
 private synchronized void fail(){publish(current,"导入或保存失败，请检查配对文件、IP 和端口");}
 public void importJson(InputSource source){files.execute(()->{try(InputStream input=source.open()){if(input==null)throw new IOException();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=input.read(b))!=-1){if(out.size()+n>32768)throw new IOException();out.write(b,0,n);}DeviceConfig next=DeviceConfig.parse(new String(out.toByteArray(),StandardCharsets.UTF_8));persistence.save(next);publish(next,null);}catch(Exception e){fail();}});}
 public void save(DeviceConfig next){files.execute(()->{try{persistence.save(next);publish(next,null);}catch(Exception e){fail();}});}
 @Override public synchronized void close(){listeners.clear();files.shutdownNow();}
}
