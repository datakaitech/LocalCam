import org.localcam.app.CaptureCadence;
public class CaptureCadenceTest {
    static void check(boolean result,String message){if(!result)throw new AssertionError(message);}
    public static void main(String[]args){
        CaptureCadence cadence=new CaptureCadence();int captured=0;
        for(int i=0;i<300;i++){
            long jitter=(i%3-1)*750_000L;
            if(cadence.ready(1_000_000_000L+i*33_333_333L+jitter,30))captured++;
        }
        check(captured==300,"30 fps jitter must not halve capture rate: "+captured);
        cadence.reset();captured=0;
        for(int i=0;i<300;i++)if(cadence.ready(1_000_000_000L+i*33_333_333L,15))captured++;
        check(captured==150,"15 fps throttles 30 fps input");
        cadence.reset();captured=0;
        for(int i=0;i<300;i++)if(cadence.ready(1_000_000_000L+i*33_333_333L,24))captured++;
        check(captured>=239&&captured<=241,"24 fps from 30 fps input");
        check(cadence.ready(20_000_000_000L,30),"long stall recovers");
        check(cadence.ready(20_001_000_000L,15),"changed target resets deadline");
        try{cadence.ready(0,60);throw new AssertionError("invalid FPS accepted");}catch(IllegalArgumentException expected){}
        System.out.println("PASS: 30 fps jitter, 15/24 fps throttling, stall recovery and target changes.");
    }
}
