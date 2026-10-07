package com.topsai.dishwasher.protocol;
import java.util.*;
public final class FrameBuffer {
 private byte[] pending=new byte[0];
 public boolean hasIncompleteFrame(){return pending.length!=0;}
 public List<byte[]> feed(byte[] chunk){
  byte[] data=V3Codec.concat(pending,chunk);List<byte[]> frames=new ArrayList<>();int offset=0;
  while(data.length-offset>=6){
   if((data[offset]&255)!=0x83||data[offset+1]!=0x70||data[offset+4]!=0x20)throw new IllegalArgumentException("V3 帧头错误");
   int size=((data[offset+2]&255)<<8)|(data[offset+3]&255),type=data[offset+5]&15,padding=(data[offset+5]&255)>>4;
   if((type!=0&&type!=1&&type!=3&&type!=6)||((type==3||type==6)&&(size<46||(size+2-32)%16!=0))||((type==0||type==1)&&padding!=0))throw new IllegalArgumentException("V3 帧长度或类型错误");
   int length=size+8;if(data.length-offset<length)break;frames.add(Arrays.copyOfRange(data,offset,offset+length));offset+=length;
  }
  pending=Arrays.copyOfRange(data,offset,data.length);if(pending.length>65543)throw new IllegalArgumentException("接收缓冲过大");return frames;
 }
}
