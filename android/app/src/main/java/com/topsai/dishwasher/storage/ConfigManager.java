package com.topsai.dishwasher.storage;
import android.content.Context;import android.os.*;import com.topsai.dishwasher.model.*;import java.io.IOException;
/** Shared across recreation; holds only application context. */
public final class ConfigManager {
 private static ConfigController instance;
 private ConfigManager(){}
 public static synchronized ConfigController get(Context context){
  if(instance==null){ConfigStore store=new ConfigStore(context.getApplicationContext());Handler main=new Handler(Looper.getMainLooper());
   instance=new ConfigController(new ConfigController.Persistence(){public DeviceConfig load()throws IOException{store.consumeDebugBootstrap();return store.load();}public void save(DeviceConfig c)throws IOException{store.save(c);}},r->main.post(r));
  }return instance;
 }
}
