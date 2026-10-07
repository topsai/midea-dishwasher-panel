package com.topsai.dishwasher;
import android.test.InstrumentationTestCase;import android.content.Context;import android.content.ContextWrapper;import com.topsai.dishwasher.model.*;import com.topsai.dishwasher.storage.*;import java.io.*;import java.nio.charset.StandardCharsets;
public class ConfigStoreInstrumentedTest extends InstrumentationTestCase{
 private Context context;
 @Override protected void setUp()throws Exception{super.setUp();context=new ContextWrapper(getInstrumentation().getTargetContext()) {public Context getApplicationContext(){return this;}public File getFilesDir(){File f=new File(super.getCacheDir(),"configstore-tests");f.mkdirs();return f;}};new File(context.getFilesDir(),"config.enc").delete();new File(context.getFilesDir(),"bootstrap.json").delete();}
 @Override protected void tearDown()throws Exception{new File(context.getFilesDir(),"config.enc").delete();new File(context.getFilesDir(),"bootstrap.json").delete();super.tearDown();}
 private String fixture(){return "{\"device_id\":123456,\"ip_address\":\"192.0.2.7\",\"port\":6444,\"token\":\""+repeat("ab",64)+"\",\"key\":\""+repeat("cd",32)+"\",\"device_protocol\":3,\"subtype\":3}";}
 private String repeat(String s,int n){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append(s);return b.toString();}
 private byte[] read(File f)throws IOException{try(FileInputStream in=new FileInputStream(f)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=in.read(b))>=0)out.write(b,0,n);return out.toByteArray();}}
 public void testEncryptedRoundTripAndBootstrapDeletion()throws Exception{
  File boot=new File(context.getFilesDir(),"bootstrap.json");try(FileOutputStream out=new FileOutputStream(boot)){out.write(fixture().getBytes(StandardCharsets.UTF_8));}
  ConfigStore store=new ConfigStore(context);store.consumeDebugBootstrap();assertFalse(boot.exists());DeviceConfig c=store.load();assertNotNull(c);assertEquals(123456L,c.deviceId);
  byte[] encrypted=read(new File(context.getFilesDir(),"config.enc"));String text=new String(encrypted,StandardCharsets.ISO_8859_1);assertFalse(text.contains(repeat("ab",64)));assertFalse(text.contains(repeat("cd",32)));assertFalse(text.contains(repeat("\u00cd",32)));
 }
 public void testCorruptCiphertextFailsClosed()throws Exception{
  ConfigStore s=new ConfigStore(context);s.importJson(new ByteArrayInputStream(fixture().getBytes(StandardCharsets.UTF_8)));File f=new File(context.getFilesDir(),"config.enc");byte[] b=read(f);b[b.length-1]^=1;try(FileOutputStream out=new FileOutputStream(f)){out.write(b);}
  try{s.load();fail("corrupt ciphertext accepted");}catch(IOException expected){assertFalse(expected.getMessage().contains("cdcd"));}
 }
}
