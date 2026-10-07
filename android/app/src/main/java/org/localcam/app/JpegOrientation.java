package org.localcam.app;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
/** Fresh CameraX/YuvImage JPEGs have no EXIF. Tag them without transposing millions of pixels. */
public final class JpegOrientation {
 public static int tag(int degrees,boolean mirror){
  return switch(degrees){case 0->mirror?2:1;case 90->mirror?5:6;case 180->mirror?4:3;case 270->mirror?7:8;default->throw new IllegalArgumentException("Invalid rotation");};
 }
 public static byte[] add(byte[] jpeg,int degrees,boolean mirror){
  int orientation=tag(degrees,mirror);if(orientation==1)return jpeg;
  if(jpeg.length<4||(jpeg[0]&255)!=255||(jpeg[1]&255)!=216)throw new IllegalArgumentException("Not JPEG");
  ByteBuffer exif=ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
  exif.put((byte)255).put((byte)225).put((byte)0).put((byte)34);
  exif.put(new byte[]{69,120,105,102,0,0});exif.putShort((short)0x4949).putShort((short)42).putInt(8);
  exif.putShort((short)1).putShort((short)274).putShort((short)3).putInt(1).putShort((short)orientation).putShort((short)0).putInt(0);
  byte[] result=new byte[jpeg.length+36];System.arraycopy(jpeg,0,result,0,2);System.arraycopy(exif.array(),0,result,2,36);System.arraycopy(jpeg,2,result,38,jpeg.length-2);return result;
 }
}
