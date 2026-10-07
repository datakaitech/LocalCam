import org.localcam.app.JpegOrientation;
import java.nio.file.*;
import java.util.Arrays;
public class JpegOrientationTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[]args)throws Exception{
  byte[] jpeg=Files.readAllBytes(Path.of("scripts/tests/landscape.jpg"));
  int[] rotations={0,0,180,180,90,90,270,270};boolean[] mirrors={false,true,false,true,true,false,true,false};
  for(int tag=1;tag<=8;tag++){
   byte[] oriented=JpegOrientation.add(jpeg,rotations[tag-1],mirrors[tag-1]);
   check(JpegOrientation.tag(rotations[tag-1],mirrors[tag-1])==tag,"EXIF mapping "+tag);
   if(tag==1)check(oriented==jpeg,"landscape identity has no copy");
   else{check(oriented.length==jpeg.length+36,"constant 36-byte overhead");check((oriented[30]&255)==tag,"TIFF orientation value");check(Arrays.equals(Arrays.copyOfRange(oriented,38,oriented.length),Arrays.copyOfRange(jpeg,2,jpeg.length)),"compressed camera bytes unchanged");}
   Files.write(Path.of("scripts/tests/oriented-"+tag+".jpg"),oriented);
  }
  try{JpegOrientation.tag(45,false);throw new AssertionError("invalid rotation accepted");}catch(IllegalArgumentException expected){}
  System.out.println("PASS: eight EXIF orientations, unchanged compressed pixels, zero-copy landscape and constant metadata overhead.");
 }
}
