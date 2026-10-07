package org.localcam.app;
import java.io.DataOutputStream;
import java.io.IOException;
public final class FrameProtocol {
    private FrameProtocol(){}
    public static void writeFrame(DataOutputStream output,byte[] jpeg)throws IOException{
        if(jpeg.length<4||jpeg.length>2*1024*1024)throw new IOException("Frame size outside protocol limit");
        output.writeInt(jpeg.length);output.write(jpeg);output.flush();
    }
}
