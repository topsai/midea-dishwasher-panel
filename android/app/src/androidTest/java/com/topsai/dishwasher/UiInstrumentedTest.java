package com.topsai.dishwasher;
import android.test.InstrumentationTestCase;import android.app.*;import android.content.Intent;import android.view.*;import android.widget.*;import com.topsai.dishwasher.ui.*;import com.topsai.dishwasher.model.*;import com.topsai.dishwasher.device.*;import java.util.*;
public class UiInstrumentedTest extends InstrumentationTestCase{
 private MainActivity activity;
 private void ui(Runnable r) throws Exception {Throwable[] error=new Throwable[1];getInstrumentation().runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});if(error[0] instanceof Error)throw (Error)error[0];if(error[0] instanceof Exception)throw (Exception)error[0];}
 @Override protected void setUp()throws Exception{super.setUp();Intent i=new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=(MainActivity)getInstrumentation().startActivitySync(i);}
 @Override protected void tearDown()throws Exception{if(activity!=null)ui(()->activity.finish());super.tearDown();}
 private boolean textExists(View v,String s){if(v instanceof TextView&&((TextView)v).getText().toString().contains(s))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(textExists(((ViewGroup)v).getChildAt(i),s))return true;return false;}
 public void testRendersTwentyFourFieldsAndMissingValue()throws Exception{
  ui(()->activity.onUpdate(new DeviceRepository.Snapshot(new DishwasherState(Map.of("temperature",31,"time_remaining",0)),true,false,1000,"fixture")));
  ui(()->{ViewGroup table=activity.findViewById(MainActivity.ID_PARAMETER_TABLE);assertNotNull(table);assertEquals(24,table.getChildCount());assertTrue(textExists(table,"humidity"));assertTrue(textExists(table,"未上报"));assertTrue(textExists(table,"31"));});
 }
 public void testStaleDisablesControls()throws Exception{
  ui(()->activity.onUpdate(new DeviceRepository.Snapshot(new DishwasherState(Map.of("temperature",31)),false,false,1000,"断线")));
  ui(()->{assertNotNull(activity.findViewById(MainActivity.ID_START));assertFalse(activity.findViewById(MainActivity.ID_START).isEnabled());assertTrue(textExists(activity.getWindow().getDecorView(),"可能过期"));assertTrue(textExists(activity.getWindow().getDecorView(),"31"));});
 }
 public void testRotationRestoresSelectionWithoutWrite()throws Exception{
  ui(()->{Spinner mode=activity.findViewById(MainActivity.ID_MODE);assertNotNull(mode);mode.setSelection(2);});getInstrumentation().waitForIdleSync();
  Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
  ui(()->activity.recreate());MainActivity next=(MainActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,4000);getInstrumentation().removeMonitor(monitor);assertNotNull(next);activity=next;
  ui(()->{assertEquals(2,((Spinner)activity.findViewById(MainActivity.ID_MODE)).getSelectedItemPosition());assertFalse(activity.findViewById(MainActivity.ID_START).isEnabled());});
 }
}


