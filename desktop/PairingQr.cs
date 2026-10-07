using System.Net;
using System.Text.Json;
using QRCoder;
namespace LocalCam;
internal static class PairingQr {
    public static string Payload(string host, string fingerprint, string code) {
        if (!IPAddress.TryParse(host, out var ip) || ip.AddressFamily != System.Net.Sockets.AddressFamily.InterNetwork) throw new ArgumentException("IPv4 address required");
        return JsonSerializer.Serialize(new { type = "localcam", version = 1, host, port = 8788, fingerprint, code });
    }
    public static byte[] Png(string payload) {
        using var data = QRCodeGenerator.GenerateQrCode(payload, QRCodeGenerator.ECCLevel.M);
        using var qr = new PngByteQRCode(data);
        return qr.GetGraphic(6);
    }
}
