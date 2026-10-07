package com.topsai.dishwasher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import com.topsai.dishwasher.storage.ConfigController;
import com.topsai.dishwasher.storage.ConfigManager;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.ScrollView;
import com.topsai.dishwasher.ui.MainActivity;
import com.topsai.dishwasher.model.DeviceConfig;
import com.topsai.dishwasher.model.DishwasherState;
import com.topsai.dishwasher.device.DeviceRepository;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Explicit documentation capture. No configuration is saved and no control is sent. */
public class PublicPreviewInstrumentedTest extends InstrumentationTestCase {
    // Opt in by full class#method; not included in the default test scan.
    public void capturePublicPreviews() throws Exception {
        ConfigController demo=new ConfigController(new ConfigController.Persistence(){
            public DeviceConfig load(){return null;}
            public void save(DeviceConfig ignored){throw new AssertionError("Preview must never save configuration");}
        },Runnable::run);
        CountDownLatch ready=new CountDownLatch(1);
        ConfigController.Listener listener=(config,error)->ready.countDown();
        demo.subscribe(listener);assertTrue(ready.await(3,TimeUnit.SECONDS));demo.unsubscribe(listener);
        Field manager=ConfigManager.class.getDeclaredField("instance");manager.setAccessible(true);
        Object previous=manager.get(null);
        getInstrumentation().runOnMainSync(()->{try{manager.set(null,demo);}catch(Exception e){throw new RuntimeException(e);}});
        MainActivity[] running={null};
        try {
            MainActivity activity=(MainActivity)getInstrumentation().startActivitySync(
                    new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            running[0]=activity;
            getInstrumentation().runOnMainSync(()->{
                try {
                    Field repository=MainActivity.class.getDeclaredField("repository");repository.setAccessible(true);
                    ((DeviceRepository)repository.get(activity)).shutdown();
                    Field config=MainActivity.class.getDeclaredField("config");config.setAccessible(true);
                    config.set(activity,new DeviceConfig(123456,"192.0.2.7",6444,new byte[64],new byte[32]));
                    Field settings=MainActivity.class.getDeclaredField("pairingSettings");settings.setAccessible(true);
                    Object pairing=settings.get(activity);Method update=pairing.getClass().getDeclaredMethod("update");update.setAccessible(true);update.invoke(pairing);
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            getInstrumentation().waitForIdleSync();
            Map<String,Object> values=new HashMap<>();for(String field:DishwasherState.FIELDS)values.put(field,0);
            values.put("power",true);values.put("status","running");values.put("mode","strong_wash");
            values.put("door",false);values.put("water_lack",false);values.put("storage",true);
            values.put("storage_status",false);values.put("storage_remaining",72);values.put("temperature",42);
            values.put("humidity",65);values.put("progress","wash");values.put("time_remaining",38);
            for(String f:new String[]{"child_lock","uv","dry","dry_status","rinse_aid","salt"})values.put(f,false);
            long demoTime=new java.util.GregorianCalendar(2026,java.util.Calendar.OCTOBER,7,10,30,0).getTimeInMillis();
            getInstrumentation().runOnMainSync(()->activity.onUpdate(new DeviceRepository.Snapshot(new DishwasherState(values),true,false,demoTime,"已连接 · 演示数据")));
            File folder=new File(getInstrumentation().getTargetContext().getCacheDir(),"public-preview");
            assertTrue("Preview directory must be writable",folder.isDirectory()||folder.mkdirs());
            String[] names={"home","status","control","device"};
            for(int page=0;page<4;page++){
                final int index=page;
                getInstrumentation().runOnMainSync(()->activity.showPage(index));getInstrumentation().waitForIdleSync();
                save(activity,new File(folder,"android-"+names[page]+".png"));
                if(page==3){
                    Field pages=MainActivity.class.getDeclaredField("pages");pages.setAccessible(true);
                    View[] containers=(View[])pages.get(activity);
                    ScrollView scroll=(ScrollView)containers[3].getParent();
                    getInstrumentation().runOnMainSync(()->scroll.scrollTo(0,containers[3].getHeight()));
                    getInstrumentation().waitForIdleSync();save(activity,new File(folder,"android-pairing.png"));
                }
            }
        } finally {
            getInstrumentation().runOnMainSync(()->{
                if(running[0]!=null)running[0].finish();
                try{manager.set(null,previous);}catch(Exception e){throw new RuntimeException(e);}
            });
            getInstrumentation().waitForIdleSync();demo.close();
        }
    }
    private void save(Activity activity,File file)throws Exception {
        Bitmap[] image={null};
        getInstrumentation().runOnMainSync(()->{
            View view=activity.findViewById(android.R.id.content);
            image[0]=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(image[0]));
        });
        try(FileOutputStream out=new FileOutputStream(file)){image[0].compress(Bitmap.CompressFormat.PNG,100,out);}
        image[0].recycle();
    }
}
