package com.topsai.dishwasher;
import com.topsai.dishwasher.protocol.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrameBufferTest {
 @Test public void splitAndJoinedFrames() throws Exception { byte[] f=ProtocolTest.hex(ProtocolTest.vectors().getString("statusFrame")); for(int i=0;i<f.length;i++){ FrameBuffer b=new FrameBuffer(); assertTrue(b.feed(Arrays.copyOf(f,i)).isEmpty()); assertArrayEquals(f,b.feed(Arrays.copyOfRange(f,i,f.length)).get(0)); } byte[] both=new byte[f.length*2]; System.arraycopy(f,0,both,0,f.length);System.arraycopy(f,0,both,f.length,f.length); List<byte[]> frames=new FrameBuffer().feed(both);assertEquals(2,frames.size());assertArrayEquals(f,frames.get(1)); }
 @Test public void malformedHeaderRejected() { assertThrows(IllegalArgumentException.class,()->new FrameBuffer().feed(new byte[]{0,0,0,0,0,0})); assertThrows(IllegalArgumentException.class,()->new FrameBuffer().feed(new byte[]{(byte)0x83,0x70,0,0,0x20,0x36})); }
}

