import org.localcam.app.AdaptiveQuality;
public class AdaptiveQualityTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  var controller=new AdaptiveQuality();check(controller.quality(70,true)==70,"start at selected quality");
  for(int i=0;i<20;i++)controller.observe(50,60,2,30,true);
  check(controller.quality(70,true)==40,"bounded pressure reduction");
  for(int i=0;i<100;i++)controller.observe(10,5,0,30,true);
  check(controller.quality(70,true)==70,"gradual recovery stops at selected ceiling");
  controller.observe(10,70,0,30,true);check(controller.quality(70,true)==65,"transport pressure alone reduces payload");
  check(controller.quality(85,true)==85,"user quality change resets controller");
  controller.observe(90,90,9,30,false);check(controller.quality(85,false)==85,"fixed quality stays fixed");
  controller.reset();check(controller.quality(55,true)==55,"new connection resets history");
  System.out.println("PASS: adaptive JPEG bounds, CPU/network pressure, slow recovery, selected ceiling and opt-out.");
 }
}
