import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import org.json.JSONObject;
import org.localcam.app.PairingPayload;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
public class PairingQrTest {
    static void reject(String payload)throws Exception{
        try{PairingPayload.parse(payload);}catch(Exception expected){return;}
        throw new AssertionError("Invalid pairing payload accepted");
    }
    public static void main(String[]args)throws Exception{
        Path root=Path.of(args[0]);
        String expected=Files.readString(root.resolve("scripts/tests/pairing-qr.json"));
        BufferedImage image=ImageIO.read(root.resolve("scripts/tests/pairing-qr.png").toFile());
        int[] pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
        var source=new RGBLuminanceSource(image.getWidth(),image.getHeight(),pixels);
        String decoded=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
        if(!decoded.equals(expected))throw new AssertionError("Desktop QR decodes to a different payload");
        PairingPayload payload=PairingPayload.parse(decoded);
        JSONObject json=new JSONObject(expected);
        if(!payload.host.equals(json.getString("host"))||!payload.code.equals(json.getString("code"))||!payload.fingerprint.equals(json.getString("fingerprint")))throw new AssertionError("Pairing fields changed");
        reject(new JSONObject(expected).put("port",443).toString());
        reject(new JSONObject(expected).put("type","website").toString());
        reject(new JSONObject(expected).put("version",2).toString());
        reject(new JSONObject(expected).put("fingerprint","bad").toString());
        reject(new JSONObject(expected).put("host","999.1.1.1").toString());
        reject("https://example.com");
        System.out.println("PASS: Windows QR decoded by ZXing; Android pairing parser matches host, certificate fingerprint and code; malformed QR payloads rejected.");
    }
}
