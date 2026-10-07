using System.Diagnostics;
using System.IO;
namespace LocalCam;
internal static class UsbConnector {
    private static string FindAdb() {
        var paths=new[]{Path.GetFullPath(Path.Combine(AppContext.BaseDirectory,"../../tools/android-platform-tools/adb.exe")),Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Android/Sdk/platform-tools/adb.exe")};
        return paths.FirstOrDefault(File.Exists)??throw new IOException("Android platform-tools are needed for prototype USB setup. Use Wi-Fi or install platform-tools.");
    }
    private static async Task<string> Run(string adb,params string[] args) {
        var start=new ProcessStartInfo(adb){UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true};
        foreach(var value in args)start.ArgumentList.Add(value);
        using var process=Process.Start(start)??throw new IOException("Could not start USB setup.");
        var stdout=process.StandardOutput.ReadToEndAsync();var stderr=process.StandardError.ReadToEndAsync();
        using var timeout=new CancellationTokenSource(TimeSpan.FromSeconds(12));
        try{await process.WaitForExitAsync(timeout.Token);}catch(OperationCanceledException){process.Kill();throw new IOException("USB setup timed out. Allow USB debugging on Android and try again.");}
        string text=await stdout,error=await stderr;if(process.ExitCode!=0)throw new IOException(error.Trim());return text;
    }
    public static async Task ConnectAsync(string fingerprint,string code){
        string adb=FindAdb(),devices=await Run(adb,"devices");
        var ready=devices.Split('\n').Select(x=>x.Trim().Split('\t')).Where(x=>x.Length==2&&x[1]=="device").ToArray();
        if(ready.Length!=1)throw new IOException(ready.Length==0?"Connect a data cable and allow USB debugging on your phone.":"Connect one Android phone at a time.");
        string serial=ready[0][0];await Run(adb,"-s",serial,"reverse","tcp:8788","tcp:8788");
        await Run(adb,"-s",serial,"shell","am","start","-n","org.localcam.app/.MainActivity","--es","localcam_host","127.0.0.1","--es","localcam_fingerprint",fingerprint,"--es","localcam_code",code);
    }
}
