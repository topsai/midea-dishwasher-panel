package com.topsai.dishwasher.model;
import java.util.*;
public final class DishwasherState {
 public static final String[] FIELDS={"power","status","mode","additional","uv","dry","dry_status","door","rinse_aid","salt","child_lock","storage","storage_status","time_remaining","progress","storage_remaining","temperature","humidity","waterswitch","water_lack","error_code","softwater","wrong_operation","bright"};
 public static final Map<Integer,String> MODES;
 static{Map<Integer,String> m=new LinkedHashMap<>();int[] ids={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,18,19,20,22,25};String[] names={"neutral_gear","auto_wash","strong_wash","standard_wash","eco_wash","glass_wash","hour_wash","fast_wash","soak_wash","90min","self_clean","fruit_wash","self_define","germ","bowl_wash","kill_germ","sea_food_wash","hot_pot_wash","quiet_night_wash","less_wash","oil_net_wash","cloud_wash"};for(int i=0;i<ids.length;i++)m.put(ids[i],names[i]);MODES=Collections.unmodifiableMap(m);}
 public final Map<String,Object> values;
 public DishwasherState(){this(Collections.emptyMap());}
 public DishwasherState(Map<String,Object> data){Map<String,Object> m=new LinkedHashMap<>();for(String f:FIELDS)m.put(f,data.get(f));values=Collections.unmodifiableMap(m);}
}
