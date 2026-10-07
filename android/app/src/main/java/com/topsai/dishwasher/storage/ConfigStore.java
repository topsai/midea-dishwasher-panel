package com.topsai.dishwasher.storage;
import android.content.Context;import android.security.keystore.*;import android.util.AtomicFile;import com.topsai.dishwasher.BuildConfig;import com.topsai.dishwasher.model.*;import javax.crypto.*;import javax.crypto.spec.GCMParameterSpec;import java.security.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Private AES-GCM config, key held by Android Keystore, no plaintext logs. */
public class ConfigStore {
 private final Context context;private final AtomicFile file;private static final String ALIAS="dishwasher_config_v1";
 public ConfigStore(Context context){this.context=context.getApplicationContext();file=new AtomicFile(new File(context.getFilesDir(),"config.enc"));}
 private SecretKey key()throws GeneralSecurityException,IOException{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
  KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
  gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());return gen.generateKey();
 }
 private static byte[] readLimited(InputStream in)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;
  while((n=in.read(b))!=-1){if(out.size()+n>32768)throw new IOException("配置文件过大");out.write(b,0,n);}return out.toByteArray();
 }
 public synchronized DeviceConfig load()throws IOException{
  if(!file.getBaseFile().exists())return null;
  try(InputStream in=file.openRead()){
   byte[] bytes=readLimited(in);if(bytes.length<29||bytes[0]!=1)throw new IOException();
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(bytes,1,13)));cipher.updateAAD(context.getPackageName().getBytes(StandardCharsets.UTF_8));
   return DeviceConfig.parse(new String(cipher.doFinal(Arrays.copyOfRange(bytes,13,bytes.length)),StandardCharsets.UTF_8));
  }catch(Exception e){throw new IOException("已保存配置无法解密，请重新导入配对文件");}
 }
 public synchronized void save(DeviceConfig config)throws IOException{
  FileOutputStream out=null;
  try{
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(context.getPackageName().getBytes(StandardCharsets.UTF_8));byte[] encrypted=cipher.doFinal(config.toJson().getBytes(StandardCharsets.UTF_8));
   out=file.startWrite();out.write(1);out.write(cipher.getIV());out.write(encrypted);file.finishWrite(out);
  }catch(Exception e){if(out!=null)file.failWrite(out);throw new IOException("配置加密保存失败");}
 }
 public DeviceConfig importJson(InputStream in)throws IOException{try{DeviceConfig c=DeviceConfig.parse(new String(readLimited(in),StandardCharsets.UTF_8));save(c);return c;}catch(IllegalArgumentException e){throw new IOException(e.getMessage());}}
 public void consumeDebugBootstrap()throws IOException{
  if(!BuildConfig.DEBUG)return;File boot=new File(context.getFilesDir(),"bootstrap.json");if(!boot.exists())return;
  try(InputStream in=new FileInputStream(boot)){importJson(in);}
  finally{if(!boot.delete())throw new IOException("无法删除临时配对文件");}
 }
}
