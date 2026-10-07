package com.topsai.dishwasher.pairing;

import org.json.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/** Meiju protocol from midea-lan 2026.9.2 (MIT). Account/session kept in memory only. */
public final class MeijuClient {
 public interface Transport {JSONObject call(String endpoint,JSONObject data,Map<String,String> headers)throws Exception;}
 private final String account,password,deviceId;private String accessToken;private final Transport transport;
 private static final String BASE="https://mp-prod.smartmidea.net/mas/v5/app/proxy?alias=";
 public MeijuClient(String account,String password){this(account,password,MeijuClient::https);}
 public MeijuClient(String account,String password,Transport transport){
  if(account==null||account.trim().isEmpty()||password==null||password.isEmpty())throw new IllegalArgumentException("请输入美居账号和密码");
  this.account=account.trim();this.password=password;this.transport=transport;deviceId=hash("SHA-256","Hello, "+this.account+"!").substring(0,16);
 }
 private static String stamp(){SimpleDateFormat f=new SimpleDateFormat("yyyyMMddHHmmss",Locale.ROOT);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date());}
 private static String reqId(){return UUID.randomUUID().toString().replace("-","");}
 private JSONObject general()throws JSONException{return new JSONObject().put("src","900").put("format","2").put("stamp",stamp()).put("platformId","1").put("deviceId",deviceId).put("reqId",reqId()).put("uid",JSONObject.NULL).put("clientType","1").put("appId","900").put("language","en_US");}
 private JSONObject request(String endpoint,JSONObject data)throws Exception{
  if(!data.has("reqId"))data.put("reqId",reqId());if(!data.has("stamp"))data.put("stamp",stamp());
  String random=String.valueOf(System.currentTimeMillis()/1000);Map<String,String> headers=new HashMap<>();
  headers.put("Content-Type","application/json; charset=utf-8");headers.put("secretVersion","1");headers.put("random",random);headers.put("sign",sign(data.toString(),random));
  if(accessToken!=null)headers.put("accessToken",accessToken);
  try{return transport.call(endpoint,data,headers);}catch(Exception e){throw new IOException("美居接口请求失败，请检查网络、账号验证或接口限制");}
 }
 public List<String[]> fetchKeys(long id)throws Exception{
  if(id<=0||id>0xffffffffffffL)throw new IllegalArgumentException("请先搜索并选择洗碗机");
  JSONObject login=request("/v1/user/login/id/get",general().put("loginAccount",account));
  if(login==null||!login.has("loginId"))throw new IOException("美居登录失败，请检查账号或账号验证要求");
  String date=stamp();JSONObject iot=new JSONObject().put("clientType",1).put("deviceId",deviceId).put("iampwd",hash("MD5",hash("MD5",password))).put("iotAppId","900").put("loginAccount",account).put("password",hash("SHA-256",login.getString("loginId")+hash("SHA-256",password)+"ad0ee21d48a64bf49f4fb583ab76e799")).put("reqId",reqId()).put("stamp",date);
  JSONObject result=request("/mj/user/login",new JSONObject().put("iotData",iot).put("data",new JSONObject().put("appKey","46579c15").put("deviceId",deviceId).put("platform",2)).put("timestamp",date).put("stamp",date));
  if(result==null||result.optJSONObject("mdata")==null||result.getJSONObject("mdata").optString("accessToken").isEmpty())throw new IOException("美居登录失败，请检查账号密码；验证码登录暂不支持");
  accessToken=result.getJSONObject("mdata").getString("accessToken");
  try{
   List<String[]> keys=new ArrayList<>();JSONObject homes=request("/v1/homegroup/list/get",new JSONObject());
   JSONArray list=homes==null?new JSONArray():homes.optJSONArray("homeList");
   if(list!=null)for(int i=0;i<list.length();i++){
    String home=list.getJSONObject(i).get("homegroupId").toString();
    for(int method=1;method<=2;method++){
     String udp=udpId(id,method);JSONObject response=request("/v2/iot/secure/getToken",general().put("homegroupId",home).put("udpid",udp).put("applianceCodes",new JSONArray().put(String.valueOf(id))));
     collect(response,udp,keys);
    }
    if(!keys.isEmpty())break;
   }
   if(keys.isEmpty())for(int method=1;method<=2;method++){
    String udp=udpId(id,method);collect(request("/v1/iot/secure/getToken",general().put("udpid",udp).put("applianceCodes",String.valueOf(id))),udp,keys);
   }
   if(keys.isEmpty())throw new IOException("未获取到密钥，请确认设备绑定在该账号下；接口也可能受限");
   return keys;
  }finally{accessToken=null;}
 }
 private static void collect(JSONObject response,String udp,List<String[]> out)throws JSONException{
  JSONArray tokens=response==null?null:response.optJSONArray("tokenlist");if(tokens==null)return;
  for(int i=0;i<tokens.length();i++){JSONObject t=tokens.getJSONObject(i);String token=t.optString("token"),key=t.optString("key");if(udp.equals(t.optString("udpId"))&&token.matches("[0-9a-fA-F]{128}")&&key.matches("[0-9a-fA-F]{64}"))out.add(new String[]{token,key});}
 }
 public static String udpId(long id,int method){byte[] b=new byte[6];for(int i=0;i<6;i++)b[method==1?5-i:i]=(byte)(id>>(8*i));byte[] h=digest("SHA-256",b);for(int i=0;i<16;i++)h[i]^=h[i+16];return hex(Arrays.copyOf(h,16));}
 public static String sign(String payload,String random){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec("PROD_VnoClJI9aikS8dyy".getBytes(StandardCharsets.US_ASCII),"HmacSHA256"));return hex(m.doFinal(("prod_secret123@muc"+payload+random).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("签名失败");}}
 private static String hash(String alg,String s){return hex(digest(alg,s.getBytes(StandardCharsets.UTF_8)));}
 private static byte[] digest(String alg,byte[] b){try{return MessageDigest.getInstance(alg).digest(b);}catch(Exception e){throw new IllegalStateException("摘要失败");}}
 private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
 private static JSONObject https(String endpoint,JSONObject data,Map<String,String> headers)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL(BASE+endpoint).openConnection();
  try{
   c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setDoOutput(true);for(Map.Entry<String,String> e:headers.entrySet())c.setRequestProperty(e.getKey(),e.getValue());
   byte[] body=data.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}
   if(c.getResponseCode()!=200)throw new IOException();
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>1048576)throw new IOException();out.write(b,0,n);}}
   JSONObject response=new JSONObject(out.toString("UTF-8"));return response.optInt("code",-1)==0?response.optJSONObject("data"):null;
  }finally{c.disconnect();}
 }
}
