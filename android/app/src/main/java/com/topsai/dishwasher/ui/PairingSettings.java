package com.topsai.dishwasher.ui;
import android.app.*;import android.content.*;import android.net.*;import android.os.*;import android.text.InputType;import android.view.View;import android.widget.*;
import com.topsai.dishwasher.device.*;import com.topsai.dishwasher.model.*;import com.topsai.dishwasher.pairing.*;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.*;

/** Transient settings UI. Network operations never run on the UI thread. */
final class PairingSettings {
 interface Host {DeviceConfig current();Network wifi();void save(DeviceConfig c);void importJson();void refresh();}
 interface Work<T>{T run()throws Exception;}
 interface Result<T>{void accept(T result);}
 private final Activity activity;private final Host host;private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService tasks=Executors.newSingleThreadExecutor();private final List<Button> buttons=new ArrayList<>();
 private EditText ip,port,account,password;private TextView summary,status;private LanDiscovery.Found selected;private DeviceConfig exportSnapshot;
 private boolean closed,busy;private long generation;
 PairingSettings(Activity activity,LinearLayout parent,Host host){
  this.activity=activity;this.host=host;
  LinearLayout connection=card(parent);label(connection,"连接与配对",19);summary=label(connection,"",13);
  label(connection,"手机与洗碗机须连接同一 Wi-Fi。当前支持 7600V1E0 / E1 / V3。",13);
  ip=input(connection,"设备 IP",InputType.TYPE_CLASS_TEXT);port=input(connection,"TCP 端口",InputType.TYPE_CLASS_NUMBER);
  button(connection,"搜索局域网设备",this::scan);button(connection,"保存地址并重连",this::saveAddress);button(connection,"验证当前连接",this::verify);
  button(connection,"立即刷新 / 重连",host::refresh);
  button(connection,"导入配对 JSON",host::importJson);button(connection,"导出配置备份",this::export);
  LinearLayout login=card(parent);label(login,"美居账号",19);label(login,"先用官方美居完成配网并绑定设备。密码只用于本次获取密钥，不保存。",13);
  account=input(login,"美居账号 / 手机号",InputType.TYPE_CLASS_TEXT);password=input(login,"美居密码",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
  account.setSaveEnabled(false);password.setSaveEnabled(false);password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
  button(login,"登录并获取 / 更新密钥",this::login);
  label(login,"新密钥通过设备认证和状态读取后才保存。获取失败时保留原配置；验证码登录暂不支持。",12);
  status=label(parent,"配对信息在本机加密保存，界面不显示密钥。",13);update();
 }
 private int dp(int n){return (int)(n*activity.getResources().getDisplayMetrics().density+.5f);}
 private LinearLayout card(LinearLayout parent){LinearLayout c=new LinearLayout(activity);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(12),dp(16),dp(12));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xffffffff);bg.setCornerRadius(dp(16));c.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(12));parent.addView(c,p);return c;}
 private TextView label(LinearLayout p,String s,int size){TextView v=new TextView(activity);v.setText(s);v.setTextSize(size);v.setTextColor(0xff66778a);v.setPadding(0,dp(4),0,dp(4));p.addView(v);return v;}
 private EditText input(LinearLayout p,String hint,int type){EditText v=new EditText(activity);v.setHint(hint);v.setInputType(type);v.setSingleLine(true);p.addView(v);return v;}
 private void button(LinearLayout p,String s,Runnable r){Button b=new Button(activity);b.setText(s);b.setAllCaps(false);b.setTextColor(0xff2266d5);b.setOnClickListener(v->r.run());p.addView(b);buttons.add(b);}
 void update(){generation++;selected=null;DeviceConfig c=host.current();summary.setText(c==null?"尚未配对，请搜索设备或导入配置":"当前设备 · ID "+c.deviceId);ip.setText(c==null?"":c.ipAddress);port.setText(c==null?"6444":String.valueOf(c.port));}
 void close(){closed=true;generation++;tasks.shutdownNow();password.setText("");}
 private <T> void run(Work<T> work,Result<T> result){
  if(busy||closed)return;busy=true;long start=generation;for(Button b:buttons)b.setEnabled(false);status.setText("正在处理…");
  tasks.execute(()->{T value=null;String error=null;try{value=work.run();}catch(Exception e){error=e instanceof IOException||e instanceof IllegalArgumentException?e.getMessage():"操作失败，请检查网络、配对文件或美居接口；原配置已保留";}final T done=value;final String message=error;
   main.post(()->{if(closed)return;busy=false;for(Button b:buttons)b.setEnabled(true);if(start!=generation){status.setText("配置已变化，本次结果未应用，请重试");return;}if(message!=null)status.setText(message);else result.accept(done);});
  });
 }
 private DeviceConfig withAddress(DeviceConfig c){if(c==null||selected!=null&&selected.id!=c.deviceId)throw new IllegalArgumentException("请先登录美居为所选设备获取密钥");try{return new DeviceConfig(c.deviceId,ip.getText().toString().trim(),Integer.parseInt(port.getText().toString()),c.token,c.key);}catch(IllegalArgumentException e){throw new IllegalArgumentException("设备地址或端口无效");}}
 private void verified(DeviceConfig c,Network wifi)throws Exception{DeviceSession s=new WifiDeviceSession(c,wifi);try{s.connect();s.query();}catch(Exception e){throw new IOException("连接验证失败，请检查 Wi-Fi、设备地址和密钥；旧配置已保留");}finally{s.close();}}
 private void saveAddress(){try{DeviceConfig c=withAddress(host.current());Network wifi=host.wifi();run(()->{verified(c,wifi);return c;},this::save);}catch(IllegalArgumentException e){status.setText(e.getMessage());}}
 private void save(DeviceConfig c){status.setText("验证成功，正在保存并重连");host.save(c);}
 private void verify(){DeviceConfig c=host.current();if(c==null){status.setText("请先配对设备");return;}Network wifi=host.wifi();run(()->{verified(c,wifi);return true;},ok->status.setText("当前密钥认证和状态读取成功"));}
 private void scan(){Network wifi=host.wifi();ConnectivityManager cm=(ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);LinkProperties links=wifi==null?null:cm.getLinkProperties(wifi);String hint=ip.getText().toString().trim();run(()->LanDiscovery.scan(wifi,links,hint),devices->{
  if(devices.isEmpty()){status.setText("未发现洗碗机，请检查同一 Wi-Fi 和路由器隔离设置");return;}
  String[] labels=new String[devices.size()];for(int i=0;i<labels.length;i++)labels[i]=devices.get(i).toString();
  new AlertDialog.Builder(activity).setTitle("选择洗碗机").setItems(labels,(d,index)->{LanDiscovery.Found found=devices.get(index);if(!found.supported()){status.setText("当前仅支持 7600V1E0 / E1 / V3");return;}selected=found;ip.setText(found.ip);port.setText(String.valueOf(found.port));DeviceConfig c=host.current();summary.setText("所选设备 · ID "+found.id);status.setText(c!=null&&c.deviceId==found.id?"已找回设备地址，请保存地址并重连":"已选择设备，请登录美居获取密钥");}).setNegativeButton("取消",null).show();
 });}
 private void login(){
  DeviceConfig current=host.current();long id=selected!=null?selected.id:current!=null?current.deviceId:0;
  if(id==0){status.setText("请先搜索并选择洗碗机");return;}
  String address=ip.getText().toString().trim();int tcpPort;
  try{tcpPort=Integer.parseInt(port.getText().toString());new DeviceConfig(id,address,tcpPort,new byte[64],new byte[32]);}catch(IllegalArgumentException e){status.setText("设备地址或端口无效");return;}
  String user=account.getText().toString(),pass=password.getText().toString();password.setText("");Network wifi=host.wifi();
  run(()->{List<String[]> pairs=new MeijuClient(user,pass).fetchKeys(id);for(String[] pair:pairs){DeviceConfig c=new DeviceConfig(id,address,tcpPort,LanDiscovery.unhex(pair[0]),LanDiscovery.unhex(pair[1]));try{verified(c,wifi);return c;}catch(IOException ignored){}}throw new IOException("获取到的密钥未通过设备验证，请检查地址；旧配置已保留");},this::save);
 }
 private void export(){DeviceConfig c=host.current();if(c==null){status.setText("请先配对设备");return;}new AlertDialog.Builder(activity).setTitle("导出配置备份").setMessage("备份包含设备 Token 和 Key，请保存到私人位置，不要公开分享。").setNegativeButton("取消",null).setPositiveButton("选择保存位置",(d,w)->{exportSnapshot=c;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"dishwasher-backup.json");activity.startActivityForResult(i,11);}).show();}
 void exportResult(Uri uri){DeviceConfig c=exportSnapshot;exportSnapshot=null;if(c==null){status.setText("导出已取消，请重新选择保存位置");return;}run(()->{try(OutputStream out=activity.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法写入备份文件");out.write(c.toJson().getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IOException("备份导出失败，请检查保存位置");}return true;},ok->status.setText("备份已导出"));}
 void importResult(Uri uri){Network wifi=host.wifi();run(()->{ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=activity.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("无法读取配对文件");byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){if(out.size()+n>32768)throw new IOException("配对文件过大");out.write(b,0,n);}}String json=out.toString("UTF-8");if(json.startsWith("\ufeff"))json=json.substring(1);DeviceConfig c=DeviceConfig.parse(json);verified(c,wifi);return c;},this::save);}
}
