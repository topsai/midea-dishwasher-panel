package com.topsai.dishwasher;
import android.test.InstrumentationTestCase;import android.app.*;import android.content.Intent;import android.view.*;import android.widget.*;import com.topsai.dishwasher.ui.*;import com.topsai.dishwasher.model.*;import com.topsai.dishwasher.device.*;import java.util.*;
public class UiInstrumentedTest extends InstrumentationTestCase{
 private MainActivity activity;
 private void ui(Runnable r) throws Exception {Throwable[] error=new Throwable[1];getInstrumentation().runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});if(error[0] instanceof Error)throw (Error)error[0];if(error[0] instanceof Exception)throw (Exception)error[0];}
 @Override protected void setUp()throws Exception{super.setUp();Intent i=new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=(MainActivity)getInstrumentation().startActivitySync(i);getInstrumentation().waitForIdleSync();for(int wait=0;wait<30;wait++){boolean[] ready={false};ui(()->ready[0]=activity.hasWindowFocus());if(ready[0])break;Thread.sleep(100);}}
 @Override protected void tearDown()throws Exception{if(activity!=null)ui(()->activity.finish());super.tearDown();}
 private boolean textExists(View v,String s){if(v instanceof TextView&&((TextView)v).getText().toString().contains(s))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(textExists(((ViewGroup)v).getChildAt(i),s))return true;return false;}
 private Button findButton(View v,String label){if(v instanceof Button&&((Button)v).getText().toString().equals(label))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=findButton(((ViewGroup)v).getChildAt(i),label);if(b!=null)return b;}return null;}
 // Explicit opt-in acceptance method: run only when adb has temporarily disabled Wi-Fi.
 public void verifyOfflineControlsAndReason()throws Exception{
  for(int i=0;i<30;i++){Thread.sleep(100);boolean[] ready={false};ui(()->ready[0]=textExists(activity.getWindow().getDecorView(),"Wi-Fi 未连接"));if(ready[0])break;}
  ui(()->{assertTrue(textExists(activity.getWindow().getDecorView(),"Wi-Fi 未连接"));activity.showPage(MainActivity.PAGE_CONTROL);assertFalse(activity.findViewById(MainActivity.ID_START).isEnabled());for(String label:new String[]{"开启","关闭"}){Button b=findButton(activity.getWindow().getDecorView(),label);assertNotNull(b);assertFalse(b.isEnabled());}});
 }
 public void testRendersTwentyFourFieldsAndMissingValue()throws Exception{
  ui(()->activity.onUpdate(new DeviceRepository.Snapshot(new DishwasherState(Map.of("temperature",31,"time_remaining",0)),true,false,1000,"fixture")));
  ui(()->{ViewGroup table=activity.findViewById(MainActivity.ID_PARAMETER_TABLE);assertNotNull(table);assertEquals(24,table.getChildCount());assertTrue(textExists(table,"humidity"));assertTrue(textExists(table,"未上报"));assertTrue(textExists(table,"31"));});
 }
 public void testStaleDisablesControls()throws Exception{
  ui(()->activity.onUpdate(new DeviceRepository.Snapshot(new DishwasherState(Map.of("temperature",31)),false,false,1000,"断线")));
  ui(()->{assertNotNull(activity.findViewById(MainActivity.ID_START));assertFalse(activity.findViewById(MainActivity.ID_START).isEnabled());assertTrue(textExists(activity.getWindow().getDecorView(),"可能过期"));assertTrue(textExists(activity.getWindow().getDecorView(),"31"));});
 }
 public void testBusyDoesNotDimWashButton()throws Exception{
  ui(()->activity.onUpdate(new DeviceRepository.Snapshot(new DishwasherState(Map.of("storage",true)),true,true,1000,"正在发送")));
  ui(()->{Button start=activity.findViewById(MainActivity.ID_START);assertFalse(start.isEnabled());assertEquals(0xff2266d5,start.getBackgroundTintList().getColorForState(start.getDrawableState(),0));});
  ui(()->activity.onUpdate(new DeviceRepository.Snapshot(new DishwasherState(),false,false,1000,"断线")));
  ui(()->{Button start=activity.findViewById(MainActivity.ID_START);assertFalse(start.isEnabled());assertEquals(0xffa7b6c8,start.getBackgroundTintList().getColorForState(start.getDrawableState(),0));});
 }
 public void testHomeAndSubpages()throws Exception{
  ui(()->{assertTrue("主页启动按钮可见",activity.findViewById(MainActivity.ID_START).isShown());assertFalse("主页参数表隐藏",activity.findViewById(MainActivity.ID_PARAMETER_TABLE).isShown());assertEquals(View.GONE,activity.findViewById(MainActivity.ID_HOME).getVisibility());assertTrue(activity.findViewById(MainActivity.ID_SETTINGS).isShown());
   activity.showPage(MainActivity.PAGE_STATUS);assertTrue(activity.findViewById(MainActivity.ID_PARAMETER_TABLE).isShown());assertFalse(activity.findViewById(MainActivity.ID_START).isShown());activity.onBackPressed();assertTrue(activity.findViewById(MainActivity.ID_START).isShown());
   activity.showPage(MainActivity.PAGE_CONTROL);assertTrue(findButton(activity.getWindow().getDecorView(),"开启").isShown());activity.showPage(MainActivity.PAGE_DEVICE);assertTrue(findButton(activity.getWindow().getDecorView(),"导入配对 JSON").isShown());activity.findViewById(MainActivity.ID_HOME).performClick();assertTrue(activity.findViewById(MainActivity.ID_START).isShown());});
 }
 public void testSystemBackReturnsHome()throws Exception{
  ui(()->activity.showPage(MainActivity.PAGE_STATUS));getInstrumentation().waitForIdleSync();getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);getInstrumentation().waitForIdleSync();ui(()->assertTrue(activity.findViewById(MainActivity.ID_START).isShown()));
 }
 public void testPowerToggleIsGlobalAndUsesReportedState()throws Exception{
  ui(()->{activity.onUpdate(new DeviceRepository.Snapshot(new DishwasherState(Map.of("power",true)),true,false,1000,"fixture"));Button power=activity.findViewById(MainActivity.ID_POWER);assertEquals("关闭电源",power.getText().toString());assertTrue(power.isEnabled());for(int page=0;page<4;page++){activity.showPage(page);assertTrue(power.isShown());}
   activity.onUpdate(new DeviceRepository.Snapshot(new DishwasherState(Map.of("power",false)),true,false,1000,"fixture"));assertEquals("开启电源",power.getText().toString());assertTrue(power.isEnabled());
   activity.onUpdate(new DeviceRepository.Snapshot(new DishwasherState(Map.of("power",false)),true,true,1000,"busy"));assertFalse(power.isEnabled());
   activity.onUpdate(new DeviceRepository.Snapshot(new DishwasherState(),true,false,1000,"missing"));assertFalse(power.isEnabled());
   activity.onUpdate(new DeviceRepository.Snapshot(new DishwasherState(Map.of("power",true)),false,false,1000,"offline"));assertFalse(power.isEnabled());});
 }
 public void testStorageStateColors()throws Exception{
  ui(()->{activity.onUpdate(new DeviceRepository.Snapshot(new DishwasherState(Map.of("storage",true)),true,false,1000,"fixture"));Button storage=activity.findViewById(MainActivity.ID_STORAGE);assertEquals("关闭保管",storage.getText().toString());assertEquals(0xff168365,storage.getBackgroundTintList().getDefaultColor());
   activity.onUpdate(new DeviceRepository.Snapshot(new DishwasherState(Map.of("storage",false)),true,false,1000,"fixture"));assertEquals("开启保管",storage.getText().toString());assertEquals(0xff2266d5,storage.getBackgroundTintList().getDefaultColor());});
 }
 public void testRotationRestoresSelectionWithoutWrite()throws Exception{
  ui(()->{Spinner mode=activity.findViewById(MainActivity.ID_MODE);assertNotNull(mode);mode.setSelection(2);});getInstrumentation().waitForIdleSync();
  Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
  ui(()->activity.recreate());MainActivity next=(MainActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,4000);getInstrumentation().removeMonitor(monitor);assertNotNull(next);activity=next;
  getInstrumentation().waitForIdleSync();ui(()->{assertEquals(2,((Spinner)activity.findViewById(MainActivity.ID_MODE)).getSelectedItemPosition());});
 }
}
