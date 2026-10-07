package com.topsai.dishwasher;
import android.test.InstrumentationTestCase;import android.net.*;import android.content.Context;
import com.topsai.dishwasher.pairing.*;import com.topsai.dishwasher.model.*;import com.topsai.dishwasher.storage.*;import com.topsai.dishwasher.device.*;
import java.util.*;
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
}
