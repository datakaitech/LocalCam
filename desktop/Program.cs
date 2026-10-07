using System.Windows;
namespace LocalCam;
internal static class Program {
    [STAThread]
    public static void Main(string[] args) {
        using var server = LocalCamServer.Create(args);
        if (args.Contains("--headless")) {
            server.RunHeadlessAsync().GetAwaiter().GetResult();
            return;
        }
        var application = new Application { ShutdownMode = ShutdownMode.OnMainWindowClose };
        try {
            server.StartAsync().GetAwaiter().GetResult();
            application.Run(new MainWindow(server));
        } catch (Exception error) {
            MessageBox.Show("LocalCam could not start. Close any other LocalCam instance and try again.\n\n" + error.Message, "LocalCam", MessageBoxButton.OK, MessageBoxImage.Error);
        } finally {
            server.StopAsync().GetAwaiter().GetResult();
        }
    }
}
