using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
namespace LocalCam;

internal sealed class MainWindow : Window {
    private readonly LocalCamServer server;
    private readonly Image preview = new() { Stretch = Stretch.Uniform };
    private readonly TextBlock empty = Text("Waiting for your phone", 22);
    private readonly TextBlock status = Text("Ready to connect", 15);
    private readonly TextBlock metrics = Text("0 fps", 15);
    private readonly TextBox code = Field(32);
    private readonly TextBox fingerprint = Field(13);
    private readonly TextBlock device = Text("One phone per session", 14);
    private readonly Image qrImage = new() { Width = 220, Height = 220, Stretch = Stretch.Uniform };
    private readonly ComboBox network = new() { Margin = new Thickness(0, 8, 0, 12), FontSize = 14 };
    private string qrIdentity = "";
    private readonly DispatcherTimer timer;
    private long rendered = -1;
    private readonly Border viewport = new() { ClipToBounds=true, Background=Brushes.Black, HorizontalAlignment=HorizontalAlignment.Center, VerticalAlignment=VerticalAlignment.Center };
    private Grid? videoArea;
    private readonly Canvas imageCanvas = new();
    private OutputSettings? presentedSettings;
    private int presentedWidth, presentedHeight;
    private int currentOrientation=1,presentedOrientation=1;
    private long previewCount; private DateTime previewStart=DateTime.UtcNow; private double previewFps;
    private void LayoutVideo() {
        if(videoArea==null || preview.Source is not BitmapSource source || videoArea.ActualWidth<=0 || videoArea.ActualHeight<=0)return;
        var s=server.Output;presentedSettings=s;presentedWidth=source.PixelWidth;presentedHeight=source.PixelHeight;presentedOrientation=currentOrientation;
        VideoLayout.Apply(preview,viewport,imageCanvas,videoArea.ActualWidth,videoArea.ActualHeight,currentOrientation,s);
    }
    private bool decoding;
    private bool closing;
    private readonly ComboBox aspect = new(), framing = new(), outputRotation = new();
    private readonly CheckBox mirror = new() { Content = "Mirror output horizontally", Foreground = Brush("#DCE8DF"), Margin = new Thickness(0, 14, 0, 0) };
    private readonly Slider cropZoom = new() { Minimum = 1, Maximum = 3, TickFrequency = .1, IsSnapToTickEnabled = true, Margin = new Thickness(0, 8, 0, 0) };
    private readonly TextBlock zoomLabel = Text("Crop zoom · 1.0×", 13);
    private readonly TextBlock settingsNotice = Text("", 12);
    private readonly TextBlock dimensions = Text("", 12);
    private readonly DispatcherTimer settingsTimer = new() { Interval = TimeSpan.FromMilliseconds(200) };
    private bool loadingSettings;
    private long settingsRevision;
    private void QueueSettings() {
        if (loadingSettings) return;
        settingsRevision++; settingsTimer.Stop(); settingsTimer.Start();
        zoomLabel.Text = "Crop zoom · " + cropZoom.Value.ToString("0.0") + "×";
        settingsNotice.Text = "Applying…";
    }
    private async Task ApplySettingsAsync() {
        long revision = settingsRevision;
        var value = new OutputSettings((string)aspect.SelectedItem, (string)framing.SelectedItem,
            mirror.IsChecked == true, (int)outputRotation.SelectedItem, Math.Round(cropZoom.Value, 1));
        try {
            await server.SetOutputAsync(value);
            if (revision == settingsRevision) settingsNotice.Text = server.SettingsError ?? "Saved · applies to Windows and OBS";
        } catch {
            if (revision == settingsRevision) settingsNotice.Text = "Could not apply to this frame. Try again with live video.";
        }
    }
    private void LoadSettings(OutputSettings value) {
        loadingSettings = true;
        aspect.SelectedItem = value.Aspect; framing.SelectedItem = value.Framing;
        mirror.IsChecked = value.Mirror; outputRotation.SelectedItem = value.Rotation; cropZoom.Value = value.Zoom;
        zoomLabel.Text = "Crop zoom · " + value.Zoom.ToString("0.0") + "×"; loadingSettings = false;
    }
    private static void Choice(Panel panel, ComboBox choice, string label, object[] choices) {
        Label(panel, label); choice.ItemsSource = choices;
        choice.FontSize = 14; choice.Padding = new Thickness(8); choice.Margin = new Thickness(0, 0, 0, 2);
        panel.Children.Add(choice);
    }
    private static SolidColorBrush Brush(string value) => new((Color)ColorConverter.ConvertFromString(value));
    private static TextBlock Text(string value, double size) => new() { Text = value, FontSize = size, Foreground = Brush("#DCE8DF"), TextWrapping = TextWrapping.Wrap };
    private static TextBox Field(double size) => new() { IsReadOnly = true, FontSize = size, Foreground = Brush("#C4F570"), Background = Brush("#101812"), BorderThickness = new Thickness(0), Padding = new Thickness(10), TextWrapping = TextWrapping.Wrap, FontFamily = new FontFamily("Consolas") };
    private static Border Card(UIElement child) => new() { Child = child, Background = Brush("#1A221D"), BorderBrush = Brush("#2D3B31"), BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(14), Padding = new Thickness(22) };
    private static void Label(Panel parent, string label) {
        var text = Text(label.ToUpperInvariant(), 11); text.Foreground = Brush("#98AA9E"); text.Margin = new Thickness(0, 20, 0, 7); parent.Children.Add(text);
    }
    private static Button Button(string text, Action click) {
        var button = new Button { Content = text, Background = Brush("#C4F570"), Foreground = Brush("#18220C"), BorderThickness = new Thickness(0), Padding = new Thickness(14, 10, 14, 10), Margin = new Thickness(0, 10, 10, 0), Cursor = System.Windows.Input.Cursors.Hand };
        button.MinHeight=44; button.FontSize=14;
        button.Template=(ControlTemplate)System.Windows.Markup.XamlReader.Parse("""
<ControlTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" TargetType="Button">
 <Border x:Name="surface" Background="{TemplateBinding Background}" BorderBrush="#36473B" BorderThickness="1" CornerRadius="12" Padding="{TemplateBinding Padding}">
  <ContentPresenter HorizontalAlignment="Center" VerticalAlignment="Center" RecognizesAccessKey="True"/>
 </Border>
 <ControlTemplate.Triggers>
  <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="surface" Property="Opacity" Value="0.88"/></Trigger>
  <Trigger Property="IsPressed" Value="True"><Setter TargetName="surface" Property="Opacity" Value="0.7"/></Trigger>
  <Trigger Property="IsKeyboardFocused" Value="True"><Setter TargetName="surface" Property="BorderBrush" Value="White"/><Setter TargetName="surface" Property="BorderThickness" Value="2"/></Trigger>
  <Trigger Property="IsEnabled" Value="False"><Setter TargetName="surface" Property="Opacity" Value="0.4"/></Trigger>
 </ControlTemplate.Triggers>
</ControlTemplate>
""");
        button.Click += (_, _) => click(); return button;
    }
    public MainWindow(LocalCamServer server) {
        this.server = server;
        Icon = BitmapFrame.Create(new Uri("pack://application:,,,/assets/localcam.ico"));
        Title = "LocalCam"; Width = 1180; Height = 780; MinWidth = 870; MinHeight = 660;
        Background = Brush("#101512"); Foreground = Brush("#DCE8DF"); FontFamily = new FontFamily("Segoe UI");
        WindowStartupLocation = WindowStartupLocation.CenterScreen;
        var outer = new Grid { Margin = new Thickness(28), Visibility=Visibility.Collapsed };
        var startupOverlay=new Grid();
        outer.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        outer.RowDefinitions.Add(new RowDefinition());
        var title = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 24) };
        title.Children.Add(new Image { Source = BitmapFrame.Create(new Uri("pack://application:,,,/assets/localcam.png")), Width = 54, Height = 54, Margin = new Thickness(0, 0, 14, 0) });
        var wordmark = new StackPanel();
        wordmark.Children.Add(Text("LocalCam · by Datakai", 30)); wordmark.Children.Add(Text("Your phone camera, directly on your PC. Wi-Fi or USB. No cloud.", 14));
        title.Children.Add(wordmark);
        outer.Children.Add(title);
        var body = new Grid();
        body.ColumnDefinitions.Add(new ColumnDefinition()); body.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(24) }); body.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(360) });
        Grid.SetRow(body, 1); outer.Children.Add(body);
        var left = new Grid();
        left.RowDefinitions.Add(new RowDefinition()); left.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        var video = new Grid { Background = Brush("#080D0A"), ClipToBounds = true };
        empty.HorizontalAlignment = HorizontalAlignment.Center; empty.VerticalAlignment = VerticalAlignment.Center;
        videoArea=video;imageCanvas.Children.Add(preview);viewport.Child=imageCanvas;video.Children.Add(empty);video.Children.Add(viewport);video.SizeChanged+=(_,_)=>LayoutVideo();
        var videoCard = Card(video); left.Children.Add(videoCard);
        var controls = new StackPanel { Margin = new Thickness(0, 20, 0, 0) };
        var stats = new DockPanel(); metrics.HorizontalAlignment = HorizontalAlignment.Right;
        DockPanel.SetDock(metrics, Dock.Right); stats.Children.Add(metrics); stats.Children.Add(status); controls.Children.Add(stats); dimensions.Margin = new Thickness(0, 6, 0, 0); controls.Children.Add(dimensions);
        Label(controls, "OBS Browser Source");
        var obs = Field(14); obs.Text = "http://127.0.0.1:8787/obs"; controls.Children.Add(obs);
        controls.Children.Add(Button("Copy OBS URL", () => Clipboard.SetText(obs.Text)));
        var note = Text("Use this URL in OBS. For meeting apps, start OBS Virtual Camera. Independent LocalCam webcam output is a later milestone.", 12); note.Margin = new Thickness(0, 12, 0, 0); controls.Children.Add(note);
        Grid.SetRow(controls, 1); left.Children.Add(controls); body.Children.Add(left);
        var connection = new StackPanel();
        var heading=Text("Choose how to connect",22);connection.Children.Add(heading);
        var choices=new StackPanel();var wifiSetup=new StackPanel{Visibility=Visibility.Collapsed};var usbSetup=new StackPanel{Visibility=Visibility.Collapsed};
        choices.Children.Add(Text("Select a connection. Your camera stays on your own devices.",13));
                Action chooseWifi=()=>{choices.Visibility=Visibility.Collapsed;wifiSetup.Visibility=Visibility.Visible;usbSetup.Visibility=Visibility.Collapsed;heading.Text="Connect over Wi-Fi";outer.Visibility=Visibility.Visible;startupOverlay.Visibility=Visibility.Collapsed;};
        choices.Children.Add(Button("Wi-Fi · scan to connect",chooseWifi));
                Action chooseUsb=()=>{choices.Visibility=Visibility.Collapsed;wifiSetup.Visibility=Visibility.Collapsed;usbSetup.Visibility=Visibility.Visible;heading.Text="Connect by USB";outer.Visibility=Visibility.Visible;startupOverlay.Visibility=Visibility.Collapsed;};
        choices.Children.Add(Button("USB cable · connect phone",chooseUsb));
        connection.Children.Add(choices);connection.Children.Add(wifiSetup);connection.Children.Add(usbSetup);
        var directions = Text("Put your phone and PC on the same network. In Android, tap Scan PC QR and scan this code.", 13);
        directions.Margin = new Thickness(0, 10, 0, 0); wifiSetup.Children.Add(directions);
        Label(wifiSetup, "PC network address");
        network.ItemsSource = LocalCamServer.Addresses;
        if (network.Items.Count > 0) network.SelectedIndex = 0;
        network.SelectionChanged += (_, _) => UpdateQr();
        wifiSetup.Children.Add(network);
        RenderOptions.SetBitmapScalingMode(qrImage, BitmapScalingMode.NearestNeighbor);
        wifiSetup.Children.Add(new Border { Child = qrImage, Background = Brushes.White, Padding = new Thickness(8), HorizontalAlignment = HorizontalAlignment.Center, CornerRadius = new CornerRadius(8) });
        var qrHint = Text("The QR fills in the address, fingerprint and pairing code. No cable or internet needed.", 12);
        qrHint.Margin = new Thickness(0, 10, 0, 0); wifiSetup.Children.Add(qrHint);
        device.Margin = new Thickness(0, 12, 0, 4); connection.Children.Add(device);
        connection.Children.Add(Button("Disconnect & reset pairing", () => { lock (server.State.Gate) server.State.Reset(); UpdateQr(); }));
        usbSetup.Children.Add(Text("Connect a data cable and enable USB debugging on Android. LocalCam sets up the connection and pairing.",13));
        var usbStatus=Text("",12);usbStatus.Margin=new Thickness(0,12,0,0);
        usbSetup.Children.Add(Button("Connect Android over USB",async ()=>{
            usbStatus.Text="Looking for an authorized phone…";
            try { string pairCode;lock(server.State.Gate)pairCode=server.State.Code;
                await UsbConnector.ConnectAsync(server.Fingerprint,pairCode);usbStatus.Text="Pairing opened on your phone.";
            }catch(Exception error){usbStatus.Text=error.Message;}
        }));usbSetup.Children.Add(usbStatus);
        connection.Children.Add(Button("Change connection method",()=>{choices.Visibility=Visibility.Visible;wifiSetup.Visibility=Visibility.Collapsed;usbSetup.Visibility=Visibility.Collapsed;heading.Text="Choose how to connect";}));
        var help = Text("Choose the address on your shared network. Windows may ask you to allow LocalCam on private networks. Guest Wi-Fi can prevent devices from connecting.", 12);
        help.Margin = new Thickness(0, 16, 0, 0); wifiSetup.Children.Add(help);
        UpdateQr();
        var settings = new StackPanel();
        settings.Children.Add(Text("Video settings", 22));
        var settingsHelp = Text("View framing for Windows and OBS. The original stream stays untouched. Choose source aspect in Android for wide video.", 13);
        settingsHelp.Margin = new Thickness(0, 10, 0, 0); settings.Children.Add(settingsHelp);
        Choice(settings, aspect, "Output aspect ratio", new object[] { "Source", "16:9", "4:3", "1:1", "9:16" });
        Choice(settings, framing, "Framing", new object[] { "Fit", "Fill" });
        settings.Children.Add(mirror);
        Choice(settings, outputRotation, "Clockwise correction (degrees)", new object[] { 0, 90, 180, 270 });
        zoomLabel.Margin = new Thickness(0, 18, 0, 0); settings.Children.Add(zoomLabel); settings.Children.Add(cropZoom);
        var cropNote = Text("Source + Fit + 1× shows the full phone frame. Fill and crop zoom remove edges. Turn the phone horizontally for a landscape shot; Android crops are optional.", 12);
        cropNote.Margin = new Thickness(0, 14, 0, 0); settings.Children.Add(cropNote);
        settings.Children.Add(Button("Reset video settings", () => { LoadSettings(OutputSettings.Default); QueueSettings(); }));
        var phoneNote = Text("On Android, open Camera settings for resolution, target FPS, JPEG quality, camera zoom, exposure, mirror and torch. Tap its preview to focus.", 12);
        phoneNote.Margin = new Thickness(0, 20, 0, 0); settings.Children.Add(phoneNote);
        settingsNotice.Margin = new Thickness(0, 14, 0, 0); settings.Children.Add(settingsNotice);
        Label(settings,"Coming soon · not available yet");
        foreach(var feature in new[]{"4K capture · Coming soon","Hardware video encoding · Planned","Background blur · Planned","Direct Windows webcam · Planned","Microphone audio · Planned","Dual-camera Studio mode · Planned"}){
            var planned=Button(feature,()=>{});planned.IsEnabled=false;planned.Background=Brush("#1A221D");planned.Foreground=Brush("#DCE8DF");settings.Children.Add(planned);
        }
        settings.Children.Add(Text("Resolution is selected on Android. These roadmap options are disabled; no release dates are promised.",12));
        LoadSettings(server.Output); settingsNotice.Text = server.SettingsError ?? "Settings are saved on this computer.";
        aspect.SelectionChanged += (_, _) => QueueSettings(); framing.SelectionChanged += (_, _) => QueueSettings();
        outputRotation.SelectionChanged += (_, _) => QueueSettings();
        mirror.Checked += (_, _) => QueueSettings(); mirror.Unchecked += (_, _) => QueueSettings();
        cropZoom.ValueChanged += (_, _) => QueueSettings();
        settingsTimer.Tick += async (_, _) => { settingsTimer.Stop(); await ApplySettingsAsync(); };
        var connectScroll = new ScrollViewer { Content = connection, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
        var settingsScroll = new ScrollViewer { Content = settings, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Visibility = Visibility.Collapsed };
        var pages = new Grid(); pages.Children.Add(connectScroll); pages.Children.Add(settingsScroll);
        var side = new Grid(); side.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); side.RowDefinitions.Add(new RowDefinition());
        var tabs = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 16) };
        tabs.Children.Add(Button("Connect", () => { connectScroll.Visibility = Visibility.Visible; settingsScroll.Visibility = Visibility.Collapsed; }));
        tabs.Children.Add(Button("Video settings", () => { connectScroll.Visibility = Visibility.Collapsed; settingsScroll.Visibility = Visibility.Visible; }));
        side.Children.Add(tabs); Grid.SetRow(pages, 1); side.Children.Add(pages);
        var right = Card(side); Grid.SetColumn(right, 2); body.Children.Add(right);
        var welcome=new StackPanel();
        welcome.Children.Add(new Image{Source=BitmapFrame.Create(new Uri("pack://application:,,,/assets/localcam.png")),Width=72,Height=72,HorizontalAlignment=HorizontalAlignment.Left,Margin=new Thickness(0,0,0,20)});
        welcome.Children.Add(Text("Your camera. Your connection.",32));
        var intro=Text("LocalCam by Datakai. Choose how your Android phone connects to this computer.",15);intro.Margin=new Thickness(0,12,0,20);welcome.Children.Add(intro);
        welcome.Children.Add(Button("Connect over Wi-Fi  →",chooseWifi));
        var wifiNote=Text("Same network. One QR scan. No cable needed.",12);wifiNote.Margin=new Thickness(0,6,0,16);welcome.Children.Add(wifiNote);
        welcome.Children.Add(Button("Connect with a USB cable  →",chooseUsb));
        var usbNote=Text("A data cable and Android USB debugging are needed in this preview.",12);usbNote.Margin=new Thickness(0,6,0,16);welcome.Children.Add(usbNote);
        welcome.Children.Add(Text("No account. No video servers. No watermark.",12));
        var welcomeCard=Card(welcome);welcomeCard.Width=580;welcomeCard.HorizontalAlignment=HorizontalAlignment.Center;welcomeCard.VerticalAlignment=VerticalAlignment.Center;startupOverlay.Children.Add(welcomeCard);
        var host=new Grid();host.Children.Add(outer);host.Children.Add(startupOverlay);Content=host;
        timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(16) };
        timer.Tick += async (_, _) => await RefreshAsync(); timer.Start();
        Closed += (_, _) => { closing = true; timer.Stop(); settingsTimer.Stop(); };
    }
    private void UpdateQr() {
        string value; lock (server.State.Gate) value = server.State.Code;
        var host = network.SelectedItem as string;
        if (string.IsNullOrEmpty(host)) { qrImage.Source = null; return; }
        string identity = host + value;
        if (identity == qrIdentity) return;
        var payload = PairingQr.Payload(host, server.Fingerprint, value);
        using var input = new MemoryStream(PairingQr.Png(payload));
        var image = new BitmapImage(); image.BeginInit(); image.CacheOption = BitmapCacheOption.OnLoad; image.StreamSource = input; image.EndInit(); image.Freeze();
        qrImage.Source = image; qrIdentity = identity;
    }
    private async Task RefreshAsync() {
        if (decoding || closing) return;
        byte[]? bytes; long sequence; bool live;
        lock (server.State.Gate) {
            var state = server.State; live = state.IsStreaming; sequence = state.Sequence; bytes = live ? state.Jpeg : null;
            code.Text = state.Code;
            device.Text = state.Token == null ? "One phone per session" : "Paired: " + state.Device;
            status.Text = live ? "Live · encrypted local stream" : state.Token == null ? "Ready to connect" : "Paired · waiting for video";
            metrics.Text = live ? state.Fps.ToString("0.0") + " received fps · " + previewFps.ToString("0.0") + " preview fps" : "0 fps";
        }
        UpdateQr();
        if(presentedSettings!=server.Output)LayoutVideo();
        if (!live) { previewCount=0; previewStart=DateTime.UtcNow; previewFps=0; dimensions.Text = ""; preview.Source = null; empty.Visibility = Visibility.Visible; return; }
        if (bytes == null || rendered == sequence) return;
        decoding = true;
        try {
            var bitmap = await Task.Run(() => {
                using var input = new MemoryStream(bytes);
                var image = new BitmapImage(); image.BeginInit(); image.CacheOption = BitmapCacheOption.OnLoad; image.StreamSource = input; image.EndInit(); image.Freeze(); return image;
            });
            if (!closing) { preview.Source = bitmap; currentOrientation=JpegOrientation.Read(bytes); if(presentedOrientation!=currentOrientation||presentedWidth!=bitmap.PixelWidth||presentedHeight!=bitmap.PixelHeight||presentedSettings!=server.Output)LayoutVideo(); previewCount++; var elapsed=(DateTime.UtcNow-previewStart).TotalSeconds; if(elapsed>=1){previewFps=previewCount/elapsed;previewCount=0;previewStart=DateTime.UtcNow;} dimensions.Text=(currentOrientation>=5?bitmap.PixelHeight:bitmap.PixelWidth)+" × "+(currentOrientation>=5?bitmap.PixelWidth:bitmap.PixelHeight)+" source · view "+server.Output.Aspect+" · "+server.Output.Framing; empty.Visibility = Visibility.Collapsed; rendered = sequence; }
        } catch { status.Text = "Frame could not be decoded"; }
        finally { decoding = false; }
    }
}


