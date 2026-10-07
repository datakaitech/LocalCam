using System.IO;
using System.Buffers.Binary;
using System.Net;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using Microsoft.AspNetCore.Http.Features;
namespace LocalCam;

internal sealed class LocalCamServer : IDisposable {
    private readonly WebApplication app;
    private readonly X509Certificate2 certificate;
    public Session State { get; }
    public string Fingerprint { get; }
    private readonly SemaphoreSlim processing = new(1, 1);
    private OutputSettings output = OutputSettings.Default;
    private OutputSettingsStore settingsStore = new(null);
    public OutputSettings Output => Volatile.Read(ref output);
    public string? SettingsError => settingsStore.Error;
    public async Task SetOutputAsync(OutputSettings settings) {
        if (!settings.IsValid) throw new ArgumentException("Invalid output settings.");
        await processing.WaitAsync();
        try {
            byte[]? raw; lock (State.Gate) raw = State.IsStreaming ? State.RawJpeg : null;
            Volatile.Write(ref output, settings); settingsStore.Save(settings);
            lock (State.Gate) if (raw != null && State.RawJpeg == raw && State.IsStreaming) State.ReplaceOutput(raw);
        } finally { processing.Release(); }
    }
    private async Task<bool> AcceptFrameAsync(byte[] raw, string? token) {
        await processing.WaitAsync();
        try {
            lock (State.Gate) if (State.Token != token) return false;
            var rendered = raw; // View transforms do not decode or re-encode the video transport.
            lock (State.Gate) {
                if (State.Token != token) return false;
                State.Publish(raw, rendered); return true;
            }
        } finally { processing.Release(); }
    }
    public static string[] Addresses => System.Net.NetworkInformation.NetworkInterface.GetAllNetworkInterfaces()
        .Where(n => n.OperationalStatus == System.Net.NetworkInformation.OperationalStatus.Up)
        .OrderByDescending(n => n.GetIPProperties().GatewayAddresses.Any(g => g.Address.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork && !g.Address.Equals(IPAddress.Any)))
        .SelectMany(n => n.GetIPProperties().UnicastAddresses)
        .Where(a => a.Address.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork && !IPAddress.IsLoopback(a.Address))
        .Select(a => a.Address.ToString()).Distinct().ToArray();
    private LocalCamServer(WebApplication app, Session state, X509Certificate2 cert, string fingerprint) {
        this.app = app; State = state; certificate = cert; Fingerprint = fingerprint;
    }
    public Task StartAsync() => app.StartAsync();
    public Task StopAsync() => app.StopAsync();
    public Task RunHeadlessAsync() => app.RunAsync();
    public void Dispose() { app.DisposeAsync().AsTask().GetAwaiter().GetResult(); certificate.Dispose(); }

    public static LocalCamServer Create(string[] args) {
        var builder = WebApplication.CreateBuilder(args.Where(a => a != "--headless").ToArray());
        builder.Host.ConfigureHostOptions(options => options.ShutdownTimeout = TimeSpan.FromSeconds(2));
        builder.Logging.AddFilter("Microsoft.AspNetCore", LogLevel.Warning);
        using var key = RSA.Create(2048);
        var request = new CertificateRequest("CN=LocalCam", key, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, true));
        request.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature, true));
        var san = new SubjectAlternativeNameBuilder(); san.AddDnsName("localhost"); san.AddIpAddress(IPAddress.Loopback);
        request.CertificateExtensions.Add(san.Build());
        request.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(new OidCollection { new Oid("1.3.6.1.5.5.7.3.1") }, false));
        using var generated = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddDays(1));
        var cert = X509CertificateLoader.LoadPkcs12(generated.Export(X509ContentType.Pfx), null, X509KeyStorageFlags.Exportable);
        var fingerprint = Convert.ToHexString(SHA256.HashData(cert.RawData));
        builder.WebHost.ConfigureKestrel(options => {
            options.Limits.MaxRequestBodySize = 2 * 1024 * 1024;
            options.Listen(IPAddress.Loopback, 8787);
            options.ListenAnyIP(8788, listen => listen.UseHttps(cert));
        });
        var app = builder.Build();
        var state = new Session();
        bool Local(HttpContext ctx) => ctx.Connection.RemoteIpAddress is { } ip && IPAddress.IsLoopback(ip);
        bool Authorized(HttpContext ctx, string? token) => ctx.Request.IsHttps && token != null && ctx.Request.Headers.Authorization == "Bearer " + token;
        app.Use(async (ctx, next) => {
            ctx.Response.Headers.CacheControl = "no-store";
            ctx.Response.Headers["X-Content-Type-Options"] = "nosniff";
            ctx.Response.Headers["Content-Security-Policy"] = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'";
            // A loopback socket alone does not prevent browser DNS rebinding.
            if (!ctx.Request.IsHttps && ctx.Request.Host.Host != "localhost" &&
                (!IPAddress.TryParse(ctx.Request.Host.Host, out var hostIp) || !IPAddress.IsLoopback(hostIp))) {
                ctx.Response.StatusCode = 403; return;
            }
            if (ctx.Request.Headers.Origin.Count != 0 &&
                ctx.Request.Headers.Origin.ToString() != $"{ctx.Request.Scheme}://{ctx.Request.Host}") {
                ctx.Response.StatusCode = 403; return;
            }
            if (!ctx.Request.IsHttps && !Local(ctx)) { ctx.Response.StatusCode = 403; return; }
            if (ctx.Request.Path != "/api/pair" && ctx.Request.Path != "/api/frame" && ctx.Request.Path != "/api/stream" && !Local(ctx)) {
                ctx.Response.StatusCode = 403; return;
            }
            await next();
        });
        LocalCamServer? receiver = null;
        app.MapGet("/api/output", () => Results.Json(receiver!.Output));
        app.MapPut("/api/output", async (HttpContext ctx) => {
            if (!Local(ctx) || ctx.Request.Headers["X-LocalCam-Desktop"] != "1") return Results.StatusCode(403);
            OutputSettings? settings;
            try { settings = await ctx.Request.ReadFromJsonAsync<OutputSettings>(); } catch { return Results.BadRequest(); }
            if (settings?.IsValid != true) return Results.BadRequest();
            try { await receiver!.SetOutputAsync(settings); return Results.Ok(); }
            catch (Exception e) when (e is ArgumentException or InvalidDataException or NotSupportedException) { return Results.BadRequest(); }
        });
        app.MapGet("/", () => Results.Text("LocalCam now runs as a native Windows application. Open LocalCam.Desktop.exe. OBS source: http://127.0.0.1:8787/obs"));
        app.MapGet("/obs", () => Results.Content("""
<!doctype html><html><head><meta charset="utf-8"><style>
html,body{margin:0;background:#000;height:100%;overflow:hidden}
#viewport{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);overflow:hidden}
#imagebox{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%)}img{width:100%;height:100%;object-fit:contain;image-orientation:from-image;display:block;transform-origin:center}
#blank{position:fixed;inset:0;background:#000}
</style></head><body><div id="viewport"><div id="imagebox"><img id="feed" src="/stream.mjpg" alt=""></div></div><div id="blank"></div><script src="/obs.js"></script></body></html>
""", "text/html"));
        app.MapGet("/obs.js", () => Results.Content(ObsPresentation.Script, "application/javascript"));
        app.MapGet("/api/status", () => {
            lock (state.Gate) return Results.Json(new {
                pairingCode = state.Code, fingerprint, addresses = Addresses,
                paired = state.Token != null, streaming = state.IsStreaming,
                frames = state.Frames, fps = state.Fps, lastFrame = state.LastFrame, device = state.Device, output = receiver!.Output,
                obsUrl = "http://127.0.0.1:8787/obs"
            });
        });
        app.MapGet("/api/pairing", (string? host) => {
            host ??= Addresses.FirstOrDefault() ?? "127.0.0.1";
            if (!IPAddress.TryParse(host, out var ip) || ip.AddressFamily != System.Net.Sockets.AddressFamily.InterNetwork) return Results.BadRequest();
            string value; lock (state.Gate) value = state.Code;
            var payload = PairingQr.Payload(host, fingerprint, value);
            return Results.Json(new { payload, pngBase64 = Convert.ToBase64String(PairingQr.Png(payload)) });
        });
        app.MapPost("/api/revoke", (HttpContext ctx) => {
            if (!Local(ctx) || ctx.Request.Headers["X-LocalCam-Desktop"] != "1") return Results.StatusCode(403);
            lock (state.Gate) state.Reset();
            return Results.Ok();
        });
        app.MapPost("/api/pair", async (HttpContext ctx) => {
            if (!ctx.Request.IsHttps) return Results.StatusCode(403);
            var ip = ctx.Connection.RemoteIpAddress?.ToString() ?? "";
            lock (state.Gate) {
                var now = DateTimeOffset.UtcNow;
                foreach (var expired in state.Attempts.Where(a => now - a.Value.Start > TimeSpan.FromMinutes(1)).Select(a => a.Key).ToArray()) state.Attempts.Remove(expired);
                if (!state.Attempts.ContainsKey(ip) && state.Attempts.Count >= 256) return Results.StatusCode(429);
                if (!state.Attempts.TryGetValue(ip, out var entry) || now - entry.Start > TimeSpan.FromMinutes(1)) entry = (now, 0);
                state.Attempts[ip] = (entry.Start, entry.Count + 1);
                if (entry.Count >= 5) return Results.StatusCode(429);
            }
            PairRequest? pair;
            try { pair = await ctx.Request.ReadFromJsonAsync<PairRequest>(); } catch { return Results.BadRequest(); }
            lock (state.Gate) {
                if (pair?.Code != state.Code || state.Token != null) return Results.StatusCode(401);
                state.Token = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
                var name = pair.Device ?? "Android"; state.Device = name[..Math.Min(name.Length, 80)];
                return Results.Json(new { token = state.Token });
            }
        });
        app.MapPost("/api/frame", async (HttpContext ctx) => {
            string? token; lock (state.Gate) token = state.Token;
            if (!Authorized(ctx, token)) return Results.StatusCode(401);
            if (ctx.Request.ContentType != "image/jpeg") return Results.StatusCode(415);
            using var buffer = new MemoryStream();
            await ctx.Request.Body.CopyToAsync(buffer, ctx.RequestAborted);
            var bytes = buffer.ToArray();
            if (!ValidJpeg(bytes)) return Results.BadRequest();
            try {
                if (!await receiver!.AcceptFrameAsync(bytes, token)) return Results.StatusCode(401);
            } catch (Exception e) when (e is ArgumentException or InvalidDataException or NotSupportedException) { return Results.BadRequest(); }
            return Results.NoContent();
        });
        // One encrypted, length-prefixed upload stream instead of an HTTPS round trip per image.
        app.MapPost("/api/stream", async (HttpContext ctx) => {
            string? token; lock (state.Gate) token = state.Token;
            if (!Authorized(ctx, token)) { ctx.Response.StatusCode = 401; return; }
            if (ctx.Request.ContentType != "application/x-localcam-frames") { ctx.Response.StatusCode = 415; return; }
            var limit = ctx.Features.Get<IHttpMaxRequestBodySizeFeature>();
            if (limit is { IsReadOnly: false }) limit.MaxRequestBodySize = null;
            var header = new byte[4];
            try {
                while (!ctx.RequestAborted.IsCancellationRequested) {
                    var first = await ctx.Request.Body.ReadAsync(header.AsMemory(0, 1), ctx.RequestAborted);
                    if (first == 0) break;
                    await ctx.Request.Body.ReadExactlyAsync(header.AsMemory(1, 3), ctx.RequestAborted);
                    int size = BinaryPrimitives.ReadInt32BigEndian(header);
                    if (size < 4 || size > 2 * 1024 * 1024) { ctx.Response.StatusCode = 400; return; }
                    var bytes = new byte[size];
                    await ctx.Request.Body.ReadExactlyAsync(bytes, ctx.RequestAborted);
                    if (!ValidJpeg(bytes)) { ctx.Response.StatusCode = 400; return; }
                    if (!await receiver!.AcceptFrameAsync(bytes, token)) { ctx.Response.StatusCode = 401; return; }
                }
                ctx.Response.StatusCode = 204;
            } catch (EndOfStreamException) { ctx.Response.StatusCode = 400; }
              catch (InvalidDataException) { ctx.Response.StatusCode = 400; }
              catch (OperationCanceledException) { }
              catch (IOException) { }
              catch (Exception e) when (e is ArgumentException or NotSupportedException) { ctx.Response.StatusCode = 400; }
        });
        app.MapGet("/api/latest", () => {
            lock (state.Gate) return state.Jpeg != null && state.IsStreaming
                ? Results.Bytes(state.Jpeg, "image/jpeg") : Results.NoContent();
        });
        app.MapGet("/stream.mjpg", async (HttpContext ctx) => {
            ctx.Response.ContentType = "multipart/x-mixed-replace; boundary=localcam";
            ctx.Features.Get<IHttpResponseBodyFeature>()?.DisableBuffering();
            long last = -1;
            try {
                while (!ctx.RequestAborted.IsCancellationRequested) {
                    byte[]? frame; long sequence; Task signal;
                    lock (state.Gate) { frame = state.IsStreaming ? state.Jpeg : null; sequence = state.Sequence; signal = state.Changed.Task; }
                    if (frame != null && sequence != last) {
                        await ctx.Response.WriteAsync("--localcam\r\nContent-Type: image/jpeg\r\nContent-Length: " + frame.Length + "\r\n\r\n", ctx.RequestAborted);
                        await ctx.Response.Body.WriteAsync(frame, ctx.RequestAborted);
                        await ctx.Response.WriteAsync("\r\n", ctx.RequestAborted);
                        await ctx.Response.Body.FlushAsync(ctx.RequestAborted);
                        last = sequence;
                    } else {
                        try { await signal.WaitAsync(TimeSpan.FromSeconds(2), ctx.RequestAborted); } catch (TimeoutException) { }
                    }
                }
            } catch (OperationCanceledException) { } catch (IOException) { }
        });
        receiver = new LocalCamServer(app, state, cert, fingerprint);
        // Tests stay in memory. The installed app saves only beside its executable, inside LocalCam.
        receiver.settingsStore = new(args.Contains("--headless") ? null : Path.Combine(AppContext.BaseDirectory, "output-settings.json"));
        receiver.output = receiver.settingsStore.Load();
        return receiver;
    }
    private static bool ValidJpeg(byte[] bytes) => bytes.Length >= 4 && bytes[0] == 0xff && bytes[1] == 0xd8 && bytes[^2] == 0xff && bytes[^1] == 0xd9;
}

internal record PairRequest(string Code, string? Device);
internal sealed class Session {
    public readonly object Gate = new();
    public string Code = "";
    public string? Token;
    public string Device = "";
    public byte[]? Jpeg, RawJpeg;
    public long Frames, Sequence;
    public DateTimeOffset LastFrame;
    public double Fps;
    private DateTimeOffset fpsStart;
    private long fpsFrames;
    public bool IsStreaming => LastFrame > DateTimeOffset.UtcNow.AddSeconds(-3);
    public TaskCompletionSource Changed = new(TaskCreationOptions.RunContinuationsAsynchronously);
    public Dictionary<string, (DateTimeOffset Start, int Count)> Attempts = new();
    public Session() => Reset();
    public void Publish(byte[] raw, byte[] jpeg) {
        RawJpeg = raw; Jpeg = jpeg; LastFrame = DateTimeOffset.UtcNow; if (Frames == 0) fpsStart = LastFrame; Frames++; Sequence++; fpsFrames++;
        var elapsed = (LastFrame - fpsStart).TotalSeconds;
        if (elapsed >= 1) { Fps = fpsFrames / elapsed; fpsStart = LastFrame; fpsFrames = 0; }
        var previous = Changed; Changed = new(TaskCreationOptions.RunContinuationsAsynchronously); previous.TrySetResult();
    }
    public void ReplaceOutput(byte[] jpeg) {
        Jpeg = jpeg; Sequence++;
        var previous = Changed; Changed = new(TaskCreationOptions.RunContinuationsAsynchronously); previous.TrySetResult();
    }
    public void Reset() {
        Code = RandomNumberGenerator.GetInt32(100000, 1000000).ToString();
        Token = null; Device = ""; Jpeg = null; RawJpeg = null; Frames = 0; LastFrame = default;
        fpsStart = DateTimeOffset.UtcNow; fpsFrames = 0; Fps = 0; Sequence++;
        var previous = Changed; Changed = new(TaskCreationOptions.RunContinuationsAsynchronously); previous.TrySetResult();
    }
}

