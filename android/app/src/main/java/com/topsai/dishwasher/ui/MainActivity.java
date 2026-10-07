package com.topsai.dishwasher.ui;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.Color;import android.graphics.Typeface;import android.graphics.drawable.GradientDrawable;import android.net.*;import android.view.*;import android.widget.*;
import com.topsai.dishwasher.device.*;import com.topsai.dishwasher.model.*;import com.topsai.dishwasher.storage.*;import java.io.*;import java.text.SimpleDateFormat;import java.util.*;import java.util.concurrent.*;
/** Foreground-only native Chinese LAN panel. No pairing secrets are displayed. */
public class MainActivity extends Activity implements DeviceRepository.Listener {
 public static final int ID_PARAMETER_TABLE=2000,ID_MODE=3000,ID_START=3001,ID_STORAGE=3002,ID_SETTINGS=4000,ID_HOME=4001,ID_POWER=4002;
 public static final int PAGE_HOME=0,PAGE_STATUS=1,PAGE_CONTROL=2,PAGE_DEVICE=3;
 private static final int BG=0xfff2f5f8,INK=0xff172b43,MUTED=0xff66778a,BLUE=0xff2266d5,GREEN=0xff168365;
 private final Handler main=new Handler(Looper.getMainLooper());
 private ConfigController configController;private final ConfigController.Listener configListener=this::configChanged;private DeviceConfig config;private DeviceRepository repository;private ControlActions actions;private DeviceRepository.Snapshot snapshot;
 private TextView connection,updated,configSummary,storageRemainingLabel,runningStatusLabel;private final TextView[] metrics=new TextView[4],rawRows=new TextView[24],priorityValues=new TextView[4];private final LinearLayout[] priorityCards=new LinearLayout[4];private LinearLayout[] pages;private final List<View> controls=new ArrayList<>();private final Map<String,TextView> switchValues=new LinkedHashMap<>();
 private LinearLayout connectionBar;private TextView pageTitle;private Button homeButton;private Button washStart,storageToggle,powerToggle;private Spinner modes;private LinearLayout washSpot;private List<Integer> modeCodes;private EditText address,port;private int page=0,selectedMode=2;private boolean started,destroyed;private String configError;
 private android.window.OnBackInvokedCallback backToHome;private boolean backRegistered;
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
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
  homeButton=button("‹",()->showPage(PAGE_HOME));homeButton.setId(ID_HOME);homeButton.setContentDescription("返回主页");homeButton.setTextSize(28);header.addView(homeButton,new LinearLayout.LayoutParams(dp(48),dp(48)));
  pageTitle=text("洗碗机",30,INK);pageTitle.setTypeface(null,Typeface.BOLD);header.addView(pageTitle,new LinearLayout.LayoutParams(0,-2,1));
  ImageButton settings=new ImageButton(this);settings.setId(ID_SETTINGS);settings.setContentDescription("设置菜单");settings.setImageResource(com.topsai.dishwasher.R.drawable.ic_settings);settings.setBackground(background(Color.TRANSPARENT));settings.setPadding(dp(12),dp(12),dp(12),dp(12));settings.setOnClickListener(v->{PopupMenu menu=new PopupMenu(this,settings);menu.getMenu().add(0,PAGE_STATUS,0,"状态");menu.getMenu().add(0,PAGE_CONTROL,1,"控制");menu.getMenu().add(0,PAGE_DEVICE,2,"设备");menu.setOnMenuItemClickListener(item->{showPage(item.getItemId());return true;});menu.show();});header.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header);root.addView(text("7600V1E0",13,MUTED));
  LinearLayout connectionRow=connectionBar=new LinearLayout(this);connectionRow.setGravity(Gravity.CENTER_VERTICAL);connectionRow.setBackground(background(Color.WHITE));connectionRow.setPadding(dp(12),dp(8),dp(12),dp(8));
  LinearLayout connectionDetails=column();connectionRow.addView(connectionDetails,new LinearLayout.LayoutParams(0,-2,1));connection=text("尚未连接",12,MUTED);connection.setGravity(Gravity.START);connectionDetails.addView(connection);updated=text("等待设备数据",11,MUTED);updated.setGravity(Gravity.START);connectionDetails.addView(updated);
  powerToggle=button("电源未上报",()->{Object current=snapshot!=null&&snapshot.state!=null?snapshot.state.values.get("power"):null;if(current instanceof Boolean)switchAction("power",!((Boolean)current));});powerToggle.setId(ID_POWER);powerToggle.setTag("power");powerToggle.setTextSize(13);powerToggle.setTypeface(null,Typeface.BOLD);powerToggle.setTextColor(Color.WHITE);powerToggle.setBackground(background(BLUE));powerToggle.setPadding(dp(8),0,dp(8),0);powerToggle.setElevation(dp(2));controls.add(powerToggle);connectionRow.addView(powerToggle,new LinearLayout.LayoutParams(dp(112),dp(48)));
  FrameLayout content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));pages=new LinearLayout[4];
  for(int i=0;i<4;i++){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);pages[i]=column();pages[i].setPadding(0,dp(12),0,dp(16));scroll.addView(pages[i]);content.addView(scroll);}
  buildStatus();buildControls();buildConfig();setContentView(root);root.setFocusableInTouchMode(true);root.requestFocus();showPage(page);render();root.requestApplyInsets();
 }
 public void showPage(int index){if(index<0||index>3)throw new IllegalArgumentException("未知页面");page=index;if(pages==null)return;for(int i=0;i<4;i++)((View)pages[i].getParent()).setVisibility(i==index?View.VISIBLE:View.GONE);pageTitle.setText(new String[]{"洗碗机","状态","控制","设备"}[index]);homeButton.setVisibility(index==PAGE_HOME?View.GONE:View.VISIBLE);if(Build.VERSION.SDK_INT>=33){if(backToHome==null)backToHome=()->showPage(PAGE_HOME);if(index!=PAGE_HOME&&!backRegistered){getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,backToHome);backRegistered=true;}else if(index==PAGE_HOME&&backRegistered){getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backToHome);backRegistered=false;}}}
 // API 33+ uses the registered platform callback; retain this fallback for older phones.
 @android.annotation.SuppressLint("GestureBackNavigation")
 @Override public void onBackPressed(){if(page!=PAGE_HOME)showPage(PAGE_HOME);else super.onBackPressed();}
 private void buildStatus(){
  LinearLayout.LayoutParams connectionLayout=new LinearLayout.LayoutParams(-1,-2);connectionLayout.setMargins(0,0,0,dp(12));pages[PAGE_HOME].addView(connectionBar,connectionLayout);LinearLayout priority=card(pages[0]);String[] priorityLabels={"缺水状态","保管","电源状态","门状态"};
  for(int row=0;row<2;row++){LinearLayout line=new LinearLayout(this);LinearLayout.LayoutParams lineLayout=new LinearLayout.LayoutParams(-1,-2);if(row>0)lineLayout.setMargins(0,dp(6),0,0);priority.addView(line,lineLayout);for(int col=0;col<2;col++){int i=row*2+col;LinearLayout cell=column();cell.setGravity(Gravity.CENTER);cell.setPadding(dp(6),dp(8),dp(6),dp(8));TextView title=text(priorityLabels[i],11,MUTED);title.setGravity(Gravity.CENTER);cell.addView(title,new LinearLayout.LayoutParams(-1,-2));priorityValues[i]=text("未上报",20,INK);priorityValues[i].setGravity(Gravity.CENTER);priorityValues[i].setTypeface(null,Typeface.BOLD);cell.addView(priorityValues[i],new LinearLayout.LayoutParams(-1,-2));if(i==1){storageRemainingLabel=text("剩余时间未上报",12,MUTED);storageRemainingLabel.setGravity(Gravity.CENTER);cell.addView(storageRemainingLabel,new LinearLayout.LayoutParams(-1,-2));}if(i==2){runningStatusLabel=text("运行状态未上报",12,MUTED);runningStatusLabel.setGravity(Gravity.CENTER);cell.addView(runningStatusLabel,new LinearLayout.LayoutParams(-1,-2));}priorityCards[i]=cell;LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,-1,1);if(col>0)layout.setMargins(dp(6),0,0,0);line.addView(cell,layout);}}
  washSpot=column();pages[0].addView(washSpot);
  LinearLayout panel=card(pages[0]);String[] labels={"温度","运行状态","洗涤阶段","剩余时间"};
  for(int row=0;row<2;row++){LinearLayout line=new LinearLayout(this);for(int col=0;col<2;col++){int i=row*2+col;LinearLayout cell=column();cell.addView(text(labels[i],12,MUTED));metrics[i]=text("未上报",23,INK);metrics[i].setTypeface(null,Typeface.BOLD);cell.addView(metrics[i]);line.addView(cell,new LinearLayout.LayoutParams(0,-2,1));}panel.addView(line);}
  panel.addView(button("立即刷新 / 重连",this::refresh));
  LinearLayout values=card(pages[PAGE_STATUS]);values.addView(text("全部参数",19,INK));values.addView(text("保留原始字段和值。未上报不等于 0；字段存在不代表机型支持该功能。",12,MUTED));
  LinearLayout table=column();table.setId(ID_PARAMETER_TABLE);values.addView(table);
  for(int i=0;i<24;i++){LinearLayout item=column();item.setPadding(0,dp(10),0,dp(10));item.addView(text(FIELD_LABELS[i]+" · "+DishwasherState.FIELDS[i],14,INK));rawRows[i]=text("未上报",13,MUTED);item.addView(rawRows[i]);table.addView(item);}
 }
 private void buildControls(){
  String[] fields={"child_lock"},names={"童锁"};
  for(int i=0;i<fields.length;i++){String field=fields[i],name=names[i];LinearLayout c=card(pages[PAGE_CONTROL]);c.addView(text(name,19,INK));TextView value=text("未上报",13,MUTED);c.addView(value);switchValues.put(field,value);LinearLayout row=new LinearLayout(this);
   Button on=button("开启",()->switchAction(field,true)),off=button("关闭",()->switchAction(field,false));on.setTag(field);off.setTag(field);controls.add(on);controls.add(off);row.addView(on,new LinearLayout.LayoutParams(0,-2,1));row.addView(off,new LinearLayout.LayoutParams(0,-2,1));c.addView(row);}
  LinearLayout actionCards=new LinearLayout(this);washSpot.addView(actionCards);LinearLayout wash=card(actionCards);LinearLayout.LayoutParams washLayout=new LinearLayout.LayoutParams(0,-1,1);washLayout.setMargins(0,0,dp(4),dp(12));wash.setLayoutParams(washLayout);wash.setPadding(dp(12),dp(12),dp(12),dp(12));wash.setBackground(background(0xffedf4ff));TextView washTitle=text("启动洗涤",20,BLUE);washTitle.setTypeface(null,Typeface.BOLD);wash.addView(washTitle);wash.addView(text("选择本机支持的模式",12,MUTED));
  modeCodes=new ArrayList<>();List<String> labels=new ArrayList<>();for(Map.Entry<Integer,String> e:DishwasherState.MODES.entrySet())if(e.getKey()!=0){modeCodes.add(e.getKey());labels.add(modeName(e.getKey()));}
  modes=new Spinner(this);modes.setId(ID_MODE);ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,labels){@Override public View getView(int position,View recycled,ViewGroup parent){TextView selected=(TextView)super.getView(position,recycled,parent);selected.setTextSize(17);selected.setTextColor(INK);selected.setTypeface(null,Typeface.BOLD);selected.setPadding(dp(12),dp(10),dp(12),dp(10));return selected;}};adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);modes.setAdapter(adapter);modes.setSelection(Math.max(0,modeCodes.indexOf(selectedMode)));modes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){selectedMode=modeCodes.get(position);actions.selectMode(selectedMode);}public void onNothingSelected(AdapterView<?> p){}});LinearLayout picker=column();GradientDrawable pickerBackground=background(Color.WHITE);pickerBackground.setStroke(dp(1),0xffcbdcf4);picker.setBackground(pickerBackground);picker.setPadding(dp(4),dp(2),dp(4),dp(2));picker.addView(modes,new LinearLayout.LayoutParams(-1,dp(54)));LinearLayout.LayoutParams pickerLayout=new LinearLayout.LayoutParams(-1,-2);pickerLayout.setMargins(0,dp(8),0,dp(12));wash.addView(picker,pickerLayout);
  Button start=washStart=button("▶  启动洗涤",()->new AlertDialog.Builder(this).setTitle("确认启动洗涤").setMessage("将立即启动 "+modeName(selectedMode)+"。请确认机门与餐具已准备好。").setNegativeButton("取消",null).setPositiveButton("启动",(d,w)->safe(()->actions.startMode(selectedMode,true))).show());start.setId(ID_START);start.setTextSize(16);start.setTypeface(null,Typeface.BOLD);start.setTextColor(Color.WHITE);start.setMinHeight(dp(52));start.setBackground(background(BLUE));start.setBackgroundTintList(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xffa7b6c8,BLUE}));start.setElevation(dp(2));controls.add(start);wash.addView(start,new LinearLayout.LayoutParams(-1,dp(52)));
  LinearLayout storageCard=card(actionCards);LinearLayout.LayoutParams storageLayout=new LinearLayout.LayoutParams(0,-1,1);storageLayout.setMargins(dp(4),0,0,dp(12));storageCard.setLayoutParams(storageLayout);storageCard.setPadding(dp(12),dp(12),dp(12),dp(12));storageCard.setBackground(background(0xffeaf7f1));TextView storageTitle=text("保管控制",20,GREEN);storageTitle.setTypeface(null,Typeface.BOLD);storageCard.addView(storageTitle);TextView storageSummary=text("保管状态未上报",12,MUTED);storageCard.addView(storageSummary);switchValues.put("storage",storageSummary);
  storageToggle=button("保管状态未上报",()->{Object current=snapshot!=null&&snapshot.state!=null?snapshot.state.values.get("storage"):null;Boolean target=DishwasherState.storageToggleTarget(current);if(target!=null)switchAction("storage",target);});storageToggle.setId(ID_STORAGE);storageToggle.setTag("storage");storageToggle.setTextSize(16);storageToggle.setTypeface(null,Typeface.BOLD);storageToggle.setTextColor(Color.WHITE);storageToggle.setBackground(background(GREEN));storageToggle.setBackgroundTintList(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xffa7b6c8,GREEN}));controls.add(storageToggle);storageCard.addView(new View(this),new LinearLayout.LayoutParams(-1,0,1));storageCard.addView(storageToggle,new LinearLayout.LayoutParams(-1,dp(52)));
  pages[PAGE_CONTROL].addView(button("选择模式 / 启动洗涤",()->{showPage(0);((ScrollView)pages[0].getParent()).smoothScrollTo(0,0);}));
  LinearLayout note=card(pages[PAGE_CONTROL]);note.addView(text("功能范围",17,INK));note.addView(text("目前支持电源、童锁、保管和洗涤模式启动。独立暂停、预约、独立烘干/紫外控制及耗材档位设置尚未实现。\n\n指令发送完成不代表设备已执行；界面以设备返回状态为准。",13,MUTED));
 }
 private void switchAction(String field,boolean value){
  if(field.equals("power")&&!value)new AlertDialog.Builder(this).setTitle("确认关闭电源").setMessage("关闭电源可能中断当前洗涤程序。").setNegativeButton("取消",null).setPositiveButton("关闭",(d,w)->safe(()->actions.setSwitch(field,false,true))).show();
  else safe(()->actions.setSwitch(field,value,true));
 }
 private void buildConfig(){
  LinearLayout c=card(pages[PAGE_DEVICE]);c.addView(text("设备配置",19,INK));configSummary=text("",13,MUTED);c.addView(configSummary);c.addView(text("手机与洗碗机须连接同一局域网。配对信息使用 Android Keystore 加密保存，不显示 Token/Key 明文。",13,MUTED));
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
  String[] priorityFields={"water_lack","storage","power","door"};for(int i=0;i<priorityFields.length;i++){Object v=snapshot!=null&&snapshot.state!=null?snapshot.state.values.get(priorityFields[i]):null;String label=v==null?"未上报":i==0?(Boolean.TRUE.equals(v)?"缺水":"不缺水"):i==2?(Boolean.TRUE.equals(v)?"已开机":"已关机"):i==3?(Boolean.TRUE.equals(v)?"打开":"关闭"):(Boolean.TRUE.equals(v)?"开启":"关闭");int color=MUTED,bg=0xfff2f5f8;if(connected&&v!=null){if(i==0&&Boolean.TRUE.equals(v)){color=0xffc0392b;bg=0xffffefed;}else if(i==3&&Boolean.TRUE.equals(v)){color=0xffb86e0a;bg=0xfffff4df;}else if(i==0||i==1&&Boolean.TRUE.equals(v)||i==2&&Boolean.TRUE.equals(v)||i==3){color=GREEN;bg=0xffeaf7f1;}else{color=BLUE;bg=0xffedf4ff;}}priorityValues[i].setText(label);priorityValues[i].setTextColor(color);priorityCards[i].setBackground(background(bg));}
  String storageRemaining=value("storage_remaining");storageRemainingLabel.setText(storageRemaining.equals("未上报")?"剩余时间未上报":"剩余 "+storageRemaining+" 小时");
  String runningStatus=value("status");runningStatusLabel.setText(runningStatus.equals("未上报")?"运行状态未上报":"运行："+runningStatus);
  String[] metricFields={"temperature","status","progress","time_remaining"};for(int i=0;i<4;i++){String val=value(metricFields[i]);metrics[i].setText(val+(val.equals("未上报")?"":i==0?" ℃":i==3?" 分钟":""));}
  for(int i=0;i<24;i++){String field=DishwasherState.FIELDS[i];Object v=snapshot!=null&&snapshot.state!=null?snapshot.state.values.get(field):null;rawRows[i].setText(v==null?"未上报":value(field)+"  |  原始值: "+v);}
  for(Map.Entry<String,TextView> e:switchValues.entrySet()){String label="设备状态："+value(e.getKey());if(e.getKey().equals("storage")){String remaining=value("storage_remaining");label="状态："+value("storage")+"\n剩余："+remaining+(remaining.equals("未上报")?"":" 小时")+"\n"+value("storage_status");}e.getValue().setText(label);}
  Object storageCurrent=snapshot!=null&&snapshot.state!=null?snapshot.state.values.get("storage"):null;storageToggle.setText(storageCurrent instanceof Boolean?(Boolean.TRUE.equals(storageCurrent)?"关闭保管":"开启保管"):"保管状态未上报");
  for(View view:controls){boolean available=connected&&!snapshot.busy;if(view.getTag() instanceof String)available=available&&snapshot.state!=null&&snapshot.state.values.get(view.getTag())!=null;view.setEnabled(available);}
  if(!(storageCurrent instanceof Boolean))storageToggle.setEnabled(false);
  Object powerCurrent=snapshot!=null&&snapshot.state!=null?snapshot.state.values.get("power"):null;
  powerToggle.setText(powerCurrent instanceof Boolean?(Boolean.TRUE.equals(powerCurrent)?"关闭电源":"开启电源"):"电源未上报");
  if(!(powerCurrent instanceof Boolean))powerToggle.setEnabled(false);
  int powerColor=connected&&powerCurrent instanceof Boolean?(Boolean.TRUE.equals(powerCurrent)?GREEN:BLUE):0xffa7b6c8;
  powerToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(powerColor));
  washStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(connected?BLUE:0xffa7b6c8));
  int storageColor=connected&&storageCurrent instanceof Boolean?(Boolean.TRUE.equals(storageCurrent)?GREEN:BLUE):0xffa7b6c8;
  storageToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(storageColor));
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
