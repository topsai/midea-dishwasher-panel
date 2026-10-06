package com.topsai.dishwasher.ui;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.Color;import android.graphics.Typeface;import android.graphics.drawable.GradientDrawable;import android.net.*;import android.view.*;import android.widget.*;
import com.topsai.dishwasher.device.*;import com.topsai.dishwasher.model.*;import com.topsai.dishwasher.storage.*;import java.io.*;import java.text.SimpleDateFormat;import java.util.*;import java.util.concurrent.*;
/** Foreground-only native Chinese LAN panel. No pairing secrets are displayed. */
public class MainActivity extends Activity implements DeviceRepository.Listener {
 public static final int ID_PARAMETER_TABLE=2000,ID_MODE=3000,ID_START=3001;
 private static final int BG=0xfff2f5f8,INK=0xff172b43,MUTED=0xff66778a,BLUE=0xff2266d5,GREEN=0xff168365;
 private final Handler main=new Handler(Looper.getMainLooper());
 private ConfigController configController;private final ConfigController.Listener configListener=this::configChanged;private DeviceConfig config;private DeviceRepository repository;private ControlActions actions;private DeviceRepository.Snapshot snapshot;
 private TextView connection,updated,configSummary;private final TextView[] metrics=new TextView[4],rawRows=new TextView[24],priorityValues=new TextView[3];private final LinearLayout[] priorityCards=new LinearLayout[3];private LinearLayout[] pages;private final List<View> controls=new ArrayList<>();private final Map<String,TextView> switchValues=new LinkedHashMap<>();
 private Spinner modes;private LinearLayout washSpot;private List<Integer> modeCodes;private EditText address,port;private int page=0,selectedMode=2;private boolean started,destroyed;private String configError;
 private static final String[] FIELD_LABELS={"电源","运行状态","洗涤模式","附加功能码","紫外功能","烘干功能","烘干状态","机门","亮碟剂","洗碗盐","童锁","保管功能","保管当前运行","剩余时间（分钟）","洗涤阶段","保管剩余（小时）","温度（℃）","湿度","水开关","缺水状态","故障码","软水档位","操作错误码","亮碟剂档位"};
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  if(saved!=null){page=saved.getInt("page",0);selectedMode=saved.getInt("mode",2);}
  configController=ConfigManager.get(this);
  repository=new DeviceRepository(r->main.post(r),this,c->new WifiDeviceSession(c,findWifi()));actions=new ControlActions(repository);build();configController.subscribe(configListener);
 }
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
 private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(4));return t;}
 private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
 private GradientDrawable background(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
 private LinearLayout card(LinearLayout parent){LinearLayout c=column();c.setPadding(dp(16),dp(12),dp(16),dp(12));c.setBackground(background(Color.WHITE));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(12));parent.addView(c,p);return c;}
 private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(BLUE);b.setOnClickListener(v->action.run());return b;}
 private void build(){
  LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(18),dp(12),dp(18),0);
  // Respect Android 15/16 edge-to-edge insets while remaining compatible with this Android 12 phone.
  root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(18),dp(12)+insets.getSystemWindowInsetTop(),dp(18),insets.getSystemWindowInsetBottom());return insets;});
  TextView title=text("洗碗机",30,INK);title.setTypeface(null,Typeface.BOLD);root.addView(title);root.addView(text("7600V1E0 · 局域网直连",13,MUTED));
  connection=text("尚未连接",14,MUTED);root.addView(connection);updated=text("等待设备数据",12,MUTED);root.addView(updated);
  LinearLayout tabs=new LinearLayout(this);String[] names={"状态","控制","设备"};for(int i=0;i<3;i++){final int index=i;tabs.addView(button(names[i],()->showPage(index)),new LinearLayout.LayoutParams(0,dp(48),1));}root.addView(tabs);
  FrameLayout content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));pages=new LinearLayout[3];
  for(int i=0;i<3;i++){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);pages[i]=column();pages[i].setPadding(0,dp(12),0,dp(16));scroll.addView(pages[i]);content.addView(scroll);}
  buildStatus();buildControls();buildConfig();setContentView(root);showPage(page);render();root.requestApplyInsets();
 }
 private void showPage(int index){page=index;if(pages==null)return;for(int i=0;i<3;i++)((View)pages[i].getParent()).setVisibility(i==index?View.VISIBLE:View.GONE);}
 private void buildStatus(){
  LinearLayout priority=card(pages[0]);LinearLayout highlights=new LinearLayout(this);priority.addView(highlights);String[] priorityLabels={"缺水状态","保管功能","保管剩余时间"};
  for(int i=0;i<3;i++){LinearLayout cell=column();cell.setPadding(dp(6),dp(8),dp(6),dp(8));cell.addView(text(priorityLabels[i],11,MUTED));priorityValues[i]=text("未上报",20,INK);priorityValues[i].setTypeface(null,Typeface.BOLD);cell.addView(priorityValues[i]);priorityCards[i]=cell;LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,-1,1);if(i>0)layout.setMargins(dp(4),0,0,0);highlights.addView(cell,layout);}
  washSpot=column();pages[0].addView(washSpot);
  LinearLayout panel=card(pages[0]);String[] labels={"温度","运行状态","洗涤阶段","剩余时间"};
  for(int row=0;row<2;row++){LinearLayout line=new LinearLayout(this);for(int col=0;col<2;col++){int i=row*2+col;LinearLayout cell=column();cell.addView(text(labels[i],12,MUTED));metrics[i]=text("未上报",23,INK);metrics[i].setTypeface(null,Typeface.BOLD);cell.addView(metrics[i]);line.addView(cell,new LinearLayout.LayoutParams(0,-2,1));}panel.addView(line);}
  panel.addView(button("立即刷新 / 重连",this::refresh));
  LinearLayout values=card(pages[0]);values.addView(text("全部参数",19,INK));values.addView(text("保留原始字段和值。未上报不等于 0；字段存在不代表机型支持该功能。",12,MUTED));
  LinearLayout table=column();table.setId(ID_PARAMETER_TABLE);values.addView(table);
  for(int i=0;i<24;i++){LinearLayout item=column();item.setPadding(0,dp(10),0,dp(10));item.addView(text(FIELD_LABELS[i]+" · "+DishwasherState.FIELDS[i],14,INK));rawRows[i]=text("未上报",13,MUTED);item.addView(rawRows[i]);table.addView(item);}
 }
 private void buildControls(){
  String[] fields={"power","child_lock","storage"},names={"电源","童锁","保管"};
  for(int i=0;i<3;i++){String field=fields[i],name=names[i];LinearLayout c=card(pages[1]);c.addView(text(name,19,INK));TextView value=text("未上报",13,MUTED);c.addView(value);switchValues.put(field,value);LinearLayout row=new LinearLayout(this);
   Button on=button("开启",()->switchAction(field,true)),off=button("关闭",()->switchAction(field,false));on.setTag(field);off.setTag(field);controls.add(on);controls.add(off);row.addView(on,new LinearLayout.LayoutParams(0,-2,1));row.addView(off,new LinearLayout.LayoutParams(0,-2,1));c.addView(row);}
  LinearLayout wash=card(washSpot);wash.addView(text("启动洗涤",19,INK));wash.addView(text("选择本机支持的模式，确认后启动。",12,MUTED));
  modeCodes=new ArrayList<>();List<String> labels=new ArrayList<>();for(Map.Entry<Integer,String> e:DishwasherState.MODES.entrySet())if(e.getKey()!=0){modeCodes.add(e.getKey());labels.add(modeName(e.getKey())+" · "+e.getValue());}
  modes=new Spinner(this);modes.setId(ID_MODE);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);modes.setAdapter(adapter);modes.setSelection(Math.max(0,modeCodes.indexOf(selectedMode)));modes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){selectedMode=modeCodes.get(position);actions.selectMode(selectedMode);}public void onNothingSelected(AdapterView<?> p){}});wash.addView(modes);
  Button start=button("启动所选程序",()->new AlertDialog.Builder(this).setTitle("确认启动洗涤").setMessage("将立即启动 "+modeName(selectedMode)+"。请确认机门与餐具已准备好。").setNegativeButton("取消",null).setPositiveButton("启动",(d,w)->safe(()->actions.startMode(selectedMode,true))).show());start.setId(ID_START);start.setTextColor(Color.WHITE);start.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BLUE));controls.add(start);wash.addView(start);
  pages[1].addView(button("选择模式 / 启动洗涤",()->{showPage(0);((ScrollView)pages[0].getParent()).smoothScrollTo(0,0);}));
  LinearLayout note=card(pages[1]);note.addView(text("功能范围",17,INK));note.addView(text("目前支持电源、童锁、保管和洗涤模式启动。独立暂停、预约、独立烘干/紫外控制及耗材档位设置尚未实现。\n\n指令发送完成不代表设备已执行；界面以设备返回状态为准。",13,MUTED));
 }
 private void switchAction(String field,boolean value){
  if(field.equals("power")&&!value)new AlertDialog.Builder(this).setTitle("确认关闭电源").setMessage("关闭电源可能中断当前洗涤程序。").setNegativeButton("取消",null).setPositiveButton("关闭",(d,w)->safe(()->actions.setSwitch(field,false,true))).show();
  else safe(()->actions.setSwitch(field,value,true));
 }
 private void buildConfig(){
  LinearLayout c=card(pages[2]);c.addView(text("设备配置",19,INK));configSummary=text("",13,MUTED);c.addView(configSummary);c.addView(text("手机与洗碗机须连接同一局域网。配对信息使用 Android Keystore 加密保存，不显示 Token/Key 明文。",13,MUTED));
  address=new EditText(this);address.setHint("设备 IP，例如 192.0.2.7");address.setSingleLine(true);address.setInputType(android.text.InputType.TYPE_CLASS_TEXT);c.addView(address);
  port=new EditText(this);port.setHint("TCP 端口 6444");port.setSingleLine(true);port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);c.addView(port);
  c.addView(button("保存地址并重连",this::saveAddress));c.addView(button("导入配对 JSON",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(i,10);}));
  c.addView(text("导入现有 dishwasher.json。APK 不包含配对凭据；其他 E1 型号或协议版本暂不支持。",12,MUTED));updateConfigFields();
 }
 private void updateConfigFields(){if(configSummary==null)return;configSummary.setText(config==null?"尚未导入配对配置":("已配置 · E1/V3 · ID "+config.deviceId));address.setText(config==null?"":config.ipAddress);port.setText(config==null?"6444":String.valueOf(config.port));if(configError!=null)configSummary.setText(configError);}
 private Network findWifi(){ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return n;}return null;}
 private void connectIfReady(){
  if(config==null){repository.stop(configError!=null?configError:"请在「设备」页导入配对文件");return;}
  if(findWifi()==null){repository.stop("Wi-Fi 未连接，请连接洗碗机所在局域网");return;}
  repository.start(config);
 }
 private void refresh(){if(!started)return;if(snapshot!=null&&snapshot.connected)repository.refresh();else connectIfReady();}
 @Override protected void onStart(){super.onStart();started=true;connectIfReady();}
 @Override protected void onStop(){started=false;repository.stop();super.onStop();}
 @Override protected void onDestroy(){destroyed=true;configController.unsubscribe(configListener);repository.shutdown();super.onDestroy();}
 @Override protected void onSaveInstanceState(Bundle out){int position=modes.getSelectedItemPosition();if(position>=0&&position<modeCodes.size())selectedMode=modeCodes.get(position);out.putInt("page",page);out.putInt("mode",selectedMode);super.onSaveInstanceState(out);}
 @Override public void onUpdate(DeviceRepository.Snapshot s){if(destroyed)return;snapshot=s;render();}
 private String value(String field){Object v=snapshot!=null&&snapshot.state!=null?snapshot.state.values.get(field):null;return field.equals("storage_status")?DishwasherState.storageActivityText(v):v==null?"未上报":translate(v);}
 private void render(){
  if(connection==null)return;boolean connected=snapshot!=null&&snapshot.connected;connection.setText(snapshot==null?"等待连接":snapshot.message);connection.setTextColor(connected?GREEN:MUTED);
  updated.setText(snapshot==null||snapshot.updatedAtMillis==0?"等待设备数据":("更新于 "+new SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(new Date(snapshot.updatedAtMillis))+(connected?"":" · 可能过期")));
  String[] priorityFields={"water_lack","storage","storage_remaining"};for(int i=0;i<3;i++){Object v=snapshot!=null&&snapshot.state!=null?snapshot.state.values.get(priorityFields[i]):null;String label=v==null?"未上报":i==0?(Boolean.TRUE.equals(v)?"缺水":"不缺水"):i==1?(Boolean.TRUE.equals(v)?"开启":"关闭"):v+" 小时";int color=MUTED,bg=0xfff2f5f8;if(connected&&v!=null){if(i==0&&Boolean.TRUE.equals(v)){color=0xffc0392b;bg=0xffffefed;}else if(i==0||i==1&&Boolean.TRUE.equals(v)){color=GREEN;bg=0xffeaf7f1;}else{color=BLUE;bg=0xffedf4ff;}}priorityValues[i].setText(label);priorityValues[i].setTextColor(color);priorityCards[i].setBackground(background(bg));}
  String[] metricFields={"temperature","status","progress","time_remaining"};for(int i=0;i<4;i++){String val=value(metricFields[i]);metrics[i].setText(val+(val.equals("未上报")?"":i==0?" ℃":i==3?" 分钟":""));}
  for(int i=0;i<24;i++){String field=DishwasherState.FIELDS[i];Object v=snapshot!=null&&snapshot.state!=null?snapshot.state.values.get(field):null;rawRows[i].setText(v==null?"未上报":value(field)+"  |  原始值: "+v);}
  for(Map.Entry<String,TextView> e:switchValues.entrySet()){String label="设备状态："+value(e.getKey());if(e.getKey().equals("storage")){String remaining=value("storage_remaining");label+="\n当前动作："+value("storage_status")+"\n剩余："+remaining+(remaining.equals("未上报")?"":" 小时");}e.getValue().setText(label);}
  for(View view:controls){boolean available=connected&&!snapshot.busy;if(view.getTag() instanceof String)available=available&&snapshot.state!=null&&snapshot.state.values.get(view.getTag())!=null;view.setEnabled(available);}
 }
 private static String translate(Object v){if(v instanceof Boolean)return (Boolean)v?"开启 / 是":"关闭 / 否";String s=String.valueOf(v);switch(s){case "power_off":return "已关机";case "cancel":return "待机 / 取消";case "delay":return "预约等待";case "running":return "运行中";case "error":return "故障";case "soft_gear":return "软水程序";case "idle":return "空闲";case "pre_wash":return "预洗";case "wash":return "主洗";case "rinse":return "漂洗";case "dry":return "烘干";case "complete":return "完成";default:for(Map.Entry<Integer,String> e:DishwasherState.MODES.entrySet())if(e.getValue().equals(s))return modeName(e.getKey());return s;}}
 private static String modeName(int mode){switch(mode){case 0:return "未选择";case 1:return "智能洗";case 2:return "强力洗";case 3:return "标准洗";case 4:return "节能洗";case 5:return "玻璃洗";case 6:return "一小时洗";case 7:return "快速洗";case 8:return "浸泡洗";case 9:return "90 分钟洗";case 10:return "自清洁";case 11:return "水果洗";case 12:return "自定义";case 13:return "除菌";case 14:return "碗具洗";case 15:return "杀菌洗";case 16:return "海鲜洗";case 18:return "火锅洗";case 19:return "夜间静音洗";case 20:return "少量洗";case 22:return "油网洗";case 25:return "云洗";default:return "模式 "+mode;}}
 private void safe(Runnable action){try{action.run();}catch(IllegalArgumentException|IllegalStateException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
 private void saveAddress(){
  if(config==null){Toast.makeText(this,"请先导入配对文件",Toast.LENGTH_LONG).show();return;}
  try{DeviceConfig next=new DeviceConfig(config.deviceId,address.getText().toString().trim(),Integer.parseInt(port.getText().toString()),config.token,config.key);persist(next);}
  catch(IllegalArgumentException e){Toast.makeText(this,"IP 或端口无效",Toast.LENGTH_LONG).show();}
 }
 private void persist(DeviceConfig next){configController.save(next);}
 private void configChanged(DeviceConfig next,String error){if(destroyed)return;config=next;configError=error;updateConfigFields();if(started)connectIfReady();if(error!=null)showError(error);}
 private void showError(String message){if(!destroyed)Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==10&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();ContentResolver resolver=getApplicationContext().getContentResolver();configController.importJson(()->resolver.openInputStream(uri));}}
}
