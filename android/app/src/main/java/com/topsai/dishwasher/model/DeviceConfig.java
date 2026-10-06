package com.topsai.dishwasher.model;
public final class DeviceConfig { public final long deviceId; public final String ipAddress; public final int port; public final byte[] token,key; public DeviceConfig(long id,String ip,int port,byte[] token,byte[] key){this.deviceId=id;this.ipAddress=ip;this.port=port;this.token=token.clone();this.key=key.clone();} }

