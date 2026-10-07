import org.localcam.app.OrientationMath;
import org.localcam.app.FrameProtocol;
import java.io.*;
import java.util.Arrays;
public class AndroidCoreTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[]args)throws Exception{
        int[][] positions={{0,0},{44,0},{45,3},{90,3},{134,3},{135,2},{180,2},{224,2},{225,1},{270,1},{314,1},{315,0},{359,0}};
        for(int[]position:positions)check(OrientationMath.surfaceRotationForDegrees(position[0])==position[1],"physical orientation boundary "+position[0]);
        for(int degrees=0;degrees<360;degrees++){
            int first=OrientationMath.surfaceRotationForDegrees(degrees);
            int quarterTurn=OrientationMath.surfaceRotationForDegrees((degrees+90)%360);
            check(quarterTurn==(first+3)%4,"quarter-turn consistency "+degrees);
        }
        check(OrientationMath.outputDegrees(90,270)==0,"portrait correction + manual correction restores landscape");
        check(OrientationMath.outputDegrees(270,180)==90,"reverse landscape manual correction");
        try{OrientationMath.surfaceRotationForDegrees(-1);throw new AssertionError("unknown orientation accepted");}catch(IllegalArgumentException expected){}
        byte[] frame={-1,-40,1,2,-1,-39};ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        FrameProtocol.writeFrame(new DataOutputStream(buffer),frame);
        DataInputStream input=new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));
        check(input.readInt()==frame.length,"big endian length");
        byte[] decoded=new byte[frame.length];input.readFully(decoded);check(Arrays.equals(frame,decoded),"JPEG bytes preserved");
        try{FrameProtocol.writeFrame(new DataOutputStream(buffer),new byte[3]);throw new AssertionError("short frame accepted");}catch(IOException expected){}
        try{FrameProtocol.writeFrame(new DataOutputStream(buffer),new byte[2*1024*1024+1]);throw new AssertionError("oversized frame accepted");}catch(IOException expected){}
        System.out.println("PASS: physical rotation boundaries, all-angle quarter-turn consistency, manual corrections, big-endian framing and frame size bounds.");
    }
}
