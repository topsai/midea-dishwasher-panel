package com.topsai.dishwasher.ui;
import com.topsai.dishwasher.device.*;import com.topsai.dishwasher.model.*;
/** Keeps confirmation and mere mode selection separate from command submission. */
public final class ControlActions {
 private final DeviceRepository repository;
 public ControlActions(DeviceRepository repository){this.repository=repository;}
 private void validateMode(int mode){if(mode==0||!DishwasherState.MODES.containsKey(mode))throw new IllegalArgumentException("请选择有效的洗涤模式");}
 public void selectMode(int mode){validateMode(mode);}
 public void setSwitch(String field,boolean value,boolean confirmed){
  if(!field.equals("power")&&!field.equals("child_lock")&&!field.equals("storage"))throw new IllegalArgumentException("不支持的控制");
  if(field.equals("power")&&!value&&!confirmed)return;repository.submitSwitch(field,value);
 }
 public void startMode(int mode,boolean confirmed){validateMode(mode);if(confirmed)repository.submitMode(mode);}
}

