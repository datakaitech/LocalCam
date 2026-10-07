import org.localcam.app.YuvFrames;
import java.nio.ByteBuffer;
import java.util.Arrays;
public class YuvFramesTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void eq(byte[] bytes,int[] expected,String name){check(bytes.length==expected.length,name+" length");for(int i=0;i<bytes.length;i++)check((bytes[i]&255)==expected[i],name+" byte "+i);}
 static byte[] transform(byte[] input,int w,int h,int rotation,boolean mirror,int aspect){var s=YuvFrames.shape(w,h,rotation,aspect);byte[] out=new byte[s.width()*s.height()*3/2];YuvFrames.transform(input,w,h,rotation,mirror,s,out);return out;}
 static byte[] reference(byte[] src,int w,int h,int d,boolean mirror,int aspect){
  var s=YuvFrames.shape(w,h,d,aspect);int rw=s.width(),rh=s.height(),ow=d%180==0?w:h;byte[] out=new byte[rw*rh*3/2];
  for(int plane=0;plane<2;plane++){
   int unit=plane==0?1:2,div=plane==0?1:2,sw=w/div,sh=h/div,offset=plane==0?0:w*h,dest=plane==0?0:rw*rh;
   for(int y=0;y<rh/div;y++)for(int x=0;x<rw/div;x++){
    int ox=s.left()/div+x,oy=s.top()/div+y;if(mirror)ox=ow/div-1-ox;
    int sx=d==90?oy:d==180?sw-1-ox:d==270?sw-1-oy:ox;
    int sy=d==90?sh-1-ox:d==180?sh-1-oy:d==270?ox:oy;
    for(int b=0;b<unit;b++)out[dest+(y*rw/div+x)*unit+b]=src[offset+(sy*sw+sx)*unit+b];
   }
  }return out;
 }
 public static void main(String[]args){
  byte[] frame={1,2,3,4,5,6,7,8,100,110,101,111};
  eq(transform(frame,4,2,90,false,0),new int[]{5,1,6,2,7,3,8,4,100,110,101,111},"90 with VU");
  eq(transform(frame,4,2,270,false,0),new int[]{4,8,3,7,2,6,1,5,101,111,100,110},"270 with VU");
  eq(transform(frame,4,2,180,false,0),new int[]{8,7,6,5,4,3,2,1,101,111,100,110},"180 with VU");
  eq(transform(frame,4,2,0,true,0),new int[]{4,3,2,1,8,7,6,5,101,111,100,110},"mirror with VU");
  check(Arrays.equals(frame,transform(frame,4,2,0,false,0)),"fast path byte identity");
  byte[] square=new byte[24];for(int i=0;i<square.length;i++)square[i]=(byte)(i+1);
  byte[] turned=square;for(int i=0;i<4;i++)turned=transform(turned,4,4,90,false,0);check(Arrays.equals(square,turned),"full YUV four turns");
  check(Arrays.equals(square,transform(transform(square,4,4,0,true,0),4,4,0,true,0)),"double mirror");
  ByteBuffer y=ByteBuffer.allocate(32),u=ByteBuffer.allocate(16),v=ByteBuffer.allocate(16);
  for(int row=0;row<4;row++)for(int col=0;col<6;col++)y.put(row*8+col,(byte)(row*6+col+1));
  for(int row=0;row<2;row++)for(int col=0;col<3;col++){u.put(row*7+col*2,(byte)(80+row*3+col));v.put(row*7+col*2,(byte)(100+row*3+col));}
  byte[] packed=new byte[36];YuvFrames.pack(y,8,1,u,7,2,v,7,2,6,4,packed);
  for(int i=0;i<24;i++)check(packed[i]==i+1,"padded luma "+i);
  for(int i=0;i<6;i++){check((packed[24+i*2]&255)==100+i,"V stride");check((packed[25+i*2]&255)==80+i,"U stride");}
  y.rewind();u.rewind();v.rewind();byte[] crop=new byte[6];
  YuvFrames.packCrop(y,8,1,u,7,2,v,7,2,2,2,4,4,crop);
  eq(crop,new int[]{15,16,21,22,104,84},"shared preview crop honours padded planes");
  var fullPortrait=YuvFrames.shape(1280,720,90,0);
  check(fullPortrait.width()==720&&fullPortrait.height()==1280&&fullPortrait.left()==0&&fullPortrait.top()==0,"full portrait keeps every row and column");
  for(int[] dimensions:new int[][]{{80,46},{128,74},{1280,720}}){
   int w=dimensions[0],h=dimensions[1];byte[] data=new byte[w*h*3/2];new java.util.Random(42).nextBytes(data);
   for(int aspect=0;aspect<=3;aspect++)for(int angle:new int[]{0,90,180,270})for(boolean mirror:new boolean[]{false,true})
    check(Arrays.equals(transform(data,w,h,angle,mirror,aspect),reference(data,w,h,angle,mirror,aspect)),"tiled transform matches reference "+w+"x"+h+" / "+angle+" / "+aspect+" / "+mirror);
  }
  for(int aspect=1;aspect<=3;aspect++)for(int angle:new int[]{0,90,180,270}){
   var s=YuvFrames.shape(720,1280,angle,aspect);int ow=angle%180==0?720:1280,oh=angle%180==0?1280:720;
   check(s.width()<=ow&&s.height()<=oh,"crop never upscales");check((s.width()&1)==0&&(s.height()&1)==0&&(s.left()&1)==0&&(s.top()&1)==0,"chroma aligned crop");
   check(aspect==1?s.width()*9==s.height()*16:aspect==2?s.width()*16==s.height()*9:s.width()*3==s.height()*4,"exact source aspect");
  }
  var wide=YuvFrames.shape(1280,720,0,1);check(wide.width()==1280&&wide.height()==720,"wide sensor data unchanged");
  try{YuvFrames.shape(3,4,0,0);throw new AssertionError("odd dimensions accepted");}catch(IllegalArgumentException expected){}
  System.out.println("PASS: padded YUV planes, luma/chroma rotation and mirror, exact source aspect, no upscaling, even crop bounds and unchanged wide data.");
 }
}
