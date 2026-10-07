package org.localcam.app;
import org.json.JSONObject;
import java.util.Locale;
public final class PairingPayload {
    public final String host, fingerprint, code;
    private PairingPayload(String host,String fingerprint,String code){this.host=host;this.fingerprint=fingerprint;this.code=code;}
    public static PairingPayload parse(String text)throws Exception{
        if(text==null||text.length()>2048)throw new IllegalArgumentException("Invalid QR length");
        JSONObject json=new JSONObject(text);
        if(!"localcam".equals(json.optString("type"))||json.optInt("version")!=1||json.optInt("port")!=8788)throw new IllegalArgumentException("Unsupported pairing QR");
        String host=json.getString("host"),pin=json.getString("fingerprint").toUpperCase(Locale.ROOT),code=json.getString("code");
        if(!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")||!pin.matches("[A-F0-9]{64}")||!code.matches("[0-9]{6}"))throw new IllegalArgumentException("Invalid pairing fields");
        for(String part:host.split("\\."))if(Integer.parseInt(part)>255)throw new IllegalArgumentException("Invalid IP");
        return new PairingPayload(host,pin,code);
    }
}
