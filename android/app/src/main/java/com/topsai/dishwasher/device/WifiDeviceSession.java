package com.topsai.dishwasher.device;
import android.net.Network;import com.topsai.dishwasher.model.DeviceConfig;import java.net.Socket;import java.io.IOException;
/** Uses the Wi-Fi Network socket factory, even if cellular is the default route. */
public final class WifiDeviceSession extends DeviceSession {
 private final Network network;
 public WifiDeviceSession(DeviceConfig config,Network network){super(config);this.network=network;}
 @Override protected Socket openSocket()throws IOException{if(network==null)throw new IOException("Wi-Fi disconnected");return network.getSocketFactory().createSocket();}
}

