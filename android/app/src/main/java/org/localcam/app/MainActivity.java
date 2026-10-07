package org.localcam.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.graphics.Rect;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.Camera2Interop;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Size;
import android.view.OrientationEventListener;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.json.JSONObject;

public class MainActivity extends ComponentActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private final ArrayBlockingQueue<byte[]> pendingFrames = new ArrayBlockingQueue<>(1);
    private PreviewView preview;
    private LinearLayout rootView;
    private FrameLayout previewHost;
    private Button fullscreenButton;
    private boolean fullscreen;
    private final java.util.Map<android.view.View,Integer> chromeVisibility=new java.util.IdentityHashMap<>();
    private LinearLayout connectionPanel;
    private ScrollView home;
    private boolean connectionChosen;
    private final AdaptiveQuality adaptiveQuality = new AdaptiveQuality();
    private volatile boolean adaptiveEnabled = true;
    private int effectiveQuality=70;
    private String pairingHost="",pairingFingerprint="",pairingCode="";
    private LinearLayout wifiPanel,usbPanel,landing,cameraTools,streamButtons;
    private volatile int captureAspect=0;
    private boolean cameraReady=false,pendingUsbPair=false;
    private volatile long uploadedFrames=0;
    private long previousUploads=0;
    private byte[] nv21,preparedYuv;
    private double encodeMillis,packMillis,rotateMillis,jpegMillis;
    private volatile double writeMillis;
    private volatile long uploadedBytes;
    private long previousBytes,queueDrops;
    private int boundPreviewWidth,boundPreviewHeight;
    private TextView status;
    private Button connect, flip, rotate;
    private volatile boolean streaming = false;
    private volatile String token = "";
    private String address;
    private SSLSocketFactory sockets;
    private volatile HttpsURLConnection activeUpload;
    private ProcessCameraProvider provider;
    private ImageAnalysis analysis;
    private Preview cameraPreview;
    private OrientationEventListener orientation;
    private boolean front = false;
    private int targetRotation = 0;
    private volatile int outputRotation = 0;
    private volatile int targetFps = 30, jpegQuality = 70;
    private volatile boolean mirrorOutput = false;
    private int resolutionIndex = 0, zoomProgress = 0, exposureIndex = 0;
    private boolean torchOn = false;
    private Camera camera;
    private TextView captureInfo;
    private Button torch;
    private final CaptureCadence cadence = new CaptureCadence();
    private long infoStart;
    private int infoFrames;
    private volatile int generation = 0;
    private final ByteArrayOutputStream encoded = new ByteArrayOutputStream(65536);

    private final ActivityResultLauncher<ScanOptions> qrScanner = registerForActivityResult(new ScanContract(), result -> {
        if (provider != null) bindCamera();
        if (result.getContents() == null) { status.setText("Scan cancelled. Tap Scan PC QR to try again."); return; }
        try {
            PairingPayload details = PairingPayload.parse(result.getContents());
            pairingHost=details.host;pairingFingerprint=details.fingerprint;pairingCode=details.code;
            pair();
        } catch (Exception e) { status.setText("This is not a valid LocalCam pairing QR."); }
    });

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        var savedSettings = getPreferences(MODE_PRIVATE);
        if(savedSettings.getInt("framingVersion",0)<1)savedSettings.edit().putInt("aspect",0).putInt("framingVersion",1).apply();
        resolutionIndex = Math.max(0, Math.min(2, savedSettings.getInt("resolution", 0)));
        targetFps = savedSettings.getInt("fps", 30); if (targetFps != 15 && targetFps != 24 && targetFps != 30) targetFps = 30;
        jpegQuality = savedSettings.getInt("quality", 70); if (jpegQuality != 55 && jpegQuality != 70 && jpegQuality != 85) jpegQuality = 70;
        mirrorOutput = savedSettings.getBoolean("mirror", false);
        adaptiveEnabled = savedSettings.getBoolean("adaptive", true);
        captureAspect=Math.max(0,Math.min(3,savedSettings.getInt("aspect",0)));
        zoomProgress = Math.max(0, Math.min(100, savedSettings.getInt("zoom", 0)));
        LinearLayout root = new LinearLayout(this);rootView=root; root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(12)); root.setBackgroundColor(0xff101512);
        LinearLayout branding = new LinearLayout(this); branding.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this); mark.setImageResource(R.drawable.localcam_logo); int markSize=(int)(44*getResources().getDisplayMetrics().density); branding.addView(mark, new LinearLayout.LayoutParams(markSize,markSize));
        TextView title = new TextView(this); title.setText("LocalCam · Datakai"); title.setTextSize(24); title.setTextColor(0xffc4f570); title.setPadding(12,0,0,0); branding.addView(title); root.addView(branding);
        status = new TextView(this); status.setText("Wi-Fi or USB - encrypted local camera"); status.setTextColor(0xffb6c6bb); root.addView(status);
        home=new ScrollView(this);
        LinearLayout welcomePage=new LinearLayout(this);welcomePage.setOrientation(LinearLayout.VERTICAL);welcomePage.setPadding(0,dp(28),0,dp(20));
        TextView hero=homeText("Your phone.\nYour best camera.",34);welcomePage.addView(hero);
        welcomePage.addView(homeText("A local webcam by DataKai. Connect to your computer with Wi-Fi or USB, without an account or a video cloud.",16));
        welcomePage.addView(homeText("Camera video is sent only to the computer you pair. Streaming stops when you leave the app. No audio, recording or analytics.",14));
        Button begin=new Button(this);begin.setText("Get started  →");welcomePage.addView(begin);
        Button privacy=new Button(this);privacy.setText("Privacy & security");welcomePage.addView(privacy);privacy.setOnClickListener(v->showPrivacy());
        welcomePage.addView(homeText("ON THE ROADMAP",12));
        for(String feature:new String[]{"4K capture · Coming soon","Hardware video encoding · Planned","Background blur · Planned","Direct Windows webcam · Planned","Microphone audio · Planned","Dual-camera Studio mode · Planned"}){Button future=new Button(this);future.setText(feature);future.setEnabled(false);welcomePage.addView(future);}
        welcomePage.addView(homeText("These features are not available yet. No release dates are promised.",12));
        home.addView(welcomePage);root.addView(home,new LinearLayout.LayoutParams(-1,0,1));
        connectionPanel = new LinearLayout(this); connectionPanel.setOrientation(LinearLayout.VERTICAL); root.addView(connectionPanel);connectionPanel.setVisibility(android.view.View.GONE);
        begin.setOnClickListener(v->{home.setVisibility(android.view.View.GONE);connectionPanel.setVisibility(android.view.View.VISIBLE);status.setText("Choose Wi-Fi or USB to continue");});
        landing=new LinearLayout(this);landing.setOrientation(LinearLayout.VERTICAL);connectionPanel.addView(landing);
        TextView welcome=new TextView(this);welcome.setText("Choose how to connect");welcome.setTextSize(20);welcome.setTextColor(0xffedf4ef);welcome.setPadding(0,20,0,12);landing.addView(welcome);
        Button wifi=new Button(this);wifi.setText("Wi-Fi · scan your PC");landing.addView(wifi);
        Button cable=new Button(this);cable.setText("USB cable");landing.addView(cable);
        wifiPanel=new LinearLayout(this);wifiPanel.setOrientation(LinearLayout.VERTICAL);wifiPanel.setVisibility(android.view.View.GONE);connectionPanel.addView(wifiPanel);
        Button scan=new Button(this);scan.setText("Scan PC QR");wifiPanel.addView(scan);
        TextView wifiHelp=new TextView(this);wifiHelp.setText("Choose Wi-Fi on Windows LocalCam. Keep both devices on the same network and scan its QR.");wifiHelp.setTextColor(0xffb6c6bb);wifiPanel.addView(wifiHelp);
        usbPanel=new LinearLayout(this);usbPanel.setOrientation(LinearLayout.VERTICAL);usbPanel.setVisibility(android.view.View.GONE);connectionPanel.addView(usbPanel);
        TextView usbHelp=new TextView(this);usbHelp.setText("Connect a data cable. Choose USB in Windows LocalCam and click Connect Android over USB. Allow USB debugging on your phone if prompted.");usbHelp.setTextColor(0xffb6c6bb);usbPanel.addView(usbHelp);
        Button change=new Button(this);change.setText("Change connection method");connectionPanel.addView(change);
        change.setOnClickListener(v->{setFullscreen(false);if(streaming)stopStream();connectionChosen=false;if(provider!=null)provider.unbindAll();cameraReady=false;landing.setVisibility(android.view.View.VISIBLE);wifiPanel.setVisibility(android.view.View.GONE);usbPanel.setVisibility(android.view.View.GONE);previewHost.setVisibility(android.view.View.GONE);preview.setVisibility(android.view.View.GONE);cameraTools.setVisibility(android.view.View.GONE);streamButtons.setVisibility(android.view.View.GONE);captureInfo.setVisibility(android.view.View.GONE);});
        wifi.setOnClickListener(v->chooseConnection(false));
        cable.setOnClickListener(v->chooseConnection(true));
        scan.setOnClickListener(v->{
            if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},1);return;}
            if(provider!=null)provider.unbindAll();
            qrScanner.launch(new ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan LocalCam on your PC").setBeepEnabled(false).setOrientationLocked(false));
        });
        LinearLayout buttons = new LinearLayout(this);streamButtons=buttons;buttons.setVisibility(android.view.View.GONE);
        connect = new Button(this); connect.setText("Connect");connect.setVisibility(android.view.View.GONE);
        flip = new Button(this); flip.setText("Switch camera");
        rotate = new Button(this); rotate.setText("Rotate output");
        buttons.addView(connect,new LinearLayout.LayoutParams(0,-2,1));
        buttons.addView(flip,new LinearLayout.LayoutParams(0,-2,1));
        buttons.addView(rotate,new LinearLayout.LayoutParams(0,-2,1)); root.addView(buttons);
        LinearLayout tools = new LinearLayout(this);cameraTools=tools;tools.setVisibility(android.view.View.GONE);
        Button cameraSettings = new Button(this); cameraSettings.setText("Settings");
        torch = new Button(this); torch.setText("Torch off");
        tools.addView(cameraSettings, new LinearLayout.LayoutParams(0,-2,1)); tools.addView(torch, new LinearLayout.LayoutParams(0,-2,1));
        Button expandPreview=new Button(this);expandPreview.setText("Fullscreen");
        tools.addView(expandPreview,new LinearLayout.LayoutParams(0,-2,1));expandPreview.setOnClickListener(v->setFullscreen(true));
        root.addView(tools);
        captureInfo = new TextView(this); captureInfo.setLines(3);captureInfo.setTextSize(11); captureInfo.setTextColor(0xffb6c6bb); root.addView(captureInfo);captureInfo.setVisibility(android.view.View.GONE);
        cameraSettings.setOnClickListener(v -> showCameraSettings());
        torch.setOnClickListener(v -> { torchOn = !torchOn; applyCameraControls(); });
        preview = new PreviewView(this);preview.setVisibility(android.view.View.GONE); preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previewHost=new FrameLayout(this);previewHost.setVisibility(android.view.View.GONE);
        previewHost.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        fullscreenButton=new Button(this);fullscreenButton.setText("Fullscreen");
        var fullscreenLayout=new FrameLayout.LayoutParams(-2,-2,android.view.Gravity.TOP|android.view.Gravity.END);fullscreenLayout.setMargins(dp(8),dp(8),dp(8),dp(8));
        previewHost.addView(fullscreenButton,fullscreenLayout);fullscreenButton.setOnClickListener(v->setFullscreen(!fullscreen));
        root.addView(previewHost,new LinearLayout.LayoutParams(-1,0,1));
        preview.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP && camera != null) {
                var point = preview.getMeteringPointFactory().createPoint(event.getX(),event.getY());
                controlResult(camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point).setAutoCancelDuration(3, TimeUnit.SECONDS).build()), "Focus unavailable");
                v.performClick(); return true;
            }
            return true;
        });
        styleButtons(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(fullscreen){v.setPadding(0,0,0,0);return insets;}if(android.os.Build.VERSION.SDK_INT>=30){var bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(dp(20)+bars.left,dp(12)+bars.top,dp(20)+bars.right,dp(12)+bars.bottom);}else{v.setPadding(dp(20)+insets.getSystemWindowInsetLeft(),dp(12)+insets.getSystemWindowInsetTop(),dp(20)+insets.getSystemWindowInsetRight(),dp(12)+insets.getSystemWindowInsetBottom());}return insets;});
        setContentView(root);
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){if(fullscreen)setFullscreen(false);else{setEnabled(false);getOnBackPressedDispatcher().onBackPressed();}}});
        connect.setOnClickListener(v -> { if(streaming)stopStream();else pair(); });
        flip.setOnClickListener(v -> { front=!front; torchOn=false; exposureIndex=0; if(provider!=null)bindCamera(); });
        rotate.setOnClickListener(v -> { outputRotation=(outputRotation+90)%360;rotate.setText(outputRotation==0?"Rotate output":"Output +"+outputRotation+"°"); });
        orientation = new OrientationEventListener(this) {
            @Override public void onOrientationChanged(int degrees) {
                if(degrees == ORIENTATION_UNKNOWN)return;
                int rotation=OrientationMath.surfaceRotationForDegrees(degrees);
                if(rotation!=targetRotation) {
                    targetRotation=rotation;
                    if(analysis!=null)analysis.setTargetRotation(rotation);
                    if(cameraPreview!=null)cameraPreview.setTargetRotation(rotation);
                }
            }
        };
        targetRotation=getWindowManager().getDefaultDisplay().getRotation();
        // Request camera access only after a connection choice.
        readUsbIntent(getIntent());
    }
    private void setFullscreen(boolean enabled){
        if(fullscreen==enabled||previewHost==null)return;
        fullscreen=enabled;
        if(enabled){
            chromeVisibility.clear();
            for(int i=0;i<rootView.getChildCount();i++){var child=rootView.getChildAt(i);if(child!=previewHost){chromeVisibility.put(child,child.getVisibility());child.setVisibility(android.view.View.GONE);}}
        }else{
            for(var entry:chromeVisibility.entrySet())entry.getKey().setVisibility(entry.getValue());chromeVisibility.clear();
        }
        fullscreenButton.setText(enabled?"Exit fullscreen":"Fullscreen");
        var bars=new androidx.core.view.WindowInsetsControllerCompat(getWindow(),rootView);
        if(enabled){bars.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());}
        else bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        androidx.core.view.ViewCompat.requestApplyInsets(rootView);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView homeText(String text,int size){TextView view=new TextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(size>20?0xffedf4ef:0xffb6c6bb);view.setPadding(0,0,0,dp(20));return view;}
    private void showPrivacy(){new android.app.AlertDialog.Builder(this).setTitle("Your camera stays local").setMessage("LocalCam by DataKai uses your camera only after you choose a connection and allow camera permission. QR scanning uses the camera to pair with your PC.\n\nFrames and a device model label travel over an encrypted connection to the paired computer. The PC temporarily holds the latest frame for preview and OBS. LocalCam does not record video or audio, upload to DataKai, create accounts, or use analytics/advertising SDKs. OBS and other apps can record separately.\n\nKeep the app open while streaming. Leaving it stops capture. Use Stop or reset pairing to disconnect. Preferences stay on your device; pairing tokens are kept in memory.\n\nContact DataKai through datakai.in.").setPositiveButton("Got it",null).show();}
    private void styleButtons(android.view.View view){
        if(view instanceof Button button && !(view instanceof CheckBox)){
            boolean primary=button.getText().toString().startsWith("Get started")||button.getText().toString().startsWith("Scan")||button==connect;
            int fill=primary?0xffc4f570:0xff1a221d;
            var background=new android.graphics.drawable.GradientDrawable();background.setColor(fill);background.setCornerRadius(dp(14));background.setStroke(dp(1),0xff36473b);
            button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x447ca647),background,null));
            button.setTextColor(android.content.res.ColorStateList.valueOf(primary?0xff17210c:0xffedf4ef));button.setTextSize(14);button.setAllCaps(false);button.setMinHeight(dp(52));button.setPadding(dp(14),dp(10),dp(14),dp(10));button.setElevation(dp(1));button.setAlpha(button.isEnabled()?1f:.45f);
            if(button.getLayoutParams() instanceof LinearLayout.LayoutParams params){params.setMargins(dp(3),dp(6),dp(3),dp(6));button.setLayoutParams(params);}
        }
        if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++)styleButtons(group.getChildAt(i));
    }
    private void chooseConnection(boolean usb){
        connectionChosen=true;home.setVisibility(android.view.View.GONE);connectionPanel.setVisibility(android.view.View.VISIBLE);
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},1);
        else if(provider==null)startCamera();else if(!cameraReady)bindCamera();
        landing.setVisibility(android.view.View.GONE);wifiPanel.setVisibility(usb?android.view.View.GONE:android.view.View.VISIBLE);usbPanel.setVisibility(usb?android.view.View.VISIBLE:android.view.View.GONE);
        previewHost.setVisibility(android.view.View.VISIBLE);preview.setVisibility(android.view.View.VISIBLE);cameraTools.setVisibility(android.view.View.VISIBLE);streamButtons.setVisibility(android.view.View.VISIBLE);captureInfo.setVisibility(android.view.View.VISIBLE);
    }
    @Override protected void onNewIntent(android.content.Intent intent){super.onNewIntent(intent);setIntent(intent);readUsbIntent(intent);}
    private void readUsbIntent(android.content.Intent intent){
        if(intent==null||!intent.hasExtra("localcam_code"))return;
        String h=intent.getStringExtra("localcam_host"),pin=intent.getStringExtra("localcam_fingerprint"),c=intent.getStringExtra("localcam_code");
        if(!"127.0.0.1".equals(h)||pin==null||!pin.matches("[A-Fa-f0-9]{64}")||c==null||!c.matches("[0-9]{6}"))return;
        intent.removeExtra("localcam_code");intent.removeExtra("localcam_fingerprint");
        if(streaming){status.setText("Stop the current stream before pairing another computer.");return;}
        new android.app.AlertDialog.Builder(this).setTitle("Connect this computer?")
            .setMessage("USB will send your camera to the connected PC. Confirm that you clicked Connect Android over USB in LocalCam on a computer you trust.\n\nPC fingerprint: "+pin)
            .setNegativeButton("Cancel",null).setPositiveButton("Allow connection",(dialog,which)->{
                pairingHost=h;pairingFingerprint=pin.toUpperCase(Locale.ROOT);pairingCode=c;pendingUsbPair=true;chooseConnection(true);
                if(cameraReady){pendingUsbPair=false;pair();}
            }).show();
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if(r==1&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED&&connectionChosen)startCamera();
        else status.setText("Camera permission required. Enable it in Android settings.");
    }
    private void startCamera() {
        var future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{try{provider=future.get();bindCamera();}catch(Exception e){status.setText("Camera unavailable: "+e.getMessage());}},androidx.core.content.ContextCompat.getMainExecutor(this));
    }
    @androidx.annotation.OptIn(markerClass=androidx.camera.camera2.interop.ExperimentalCamera2Interop.class)
    private void bindCamera() {
        if(!connectionChosen){cameraReady=false;return;}
        try {
            provider.unbindAll();
            Size sensorSize = resolutionIndex == 2 ? new Size(1920,1080) : resolutionIndex == 1 ? new Size(1280,720) : new Size(640,360);
            var selector=front?CameraSelector.DEFAULT_FRONT_CAMERA:CameraSelector.DEFAULT_BACK_CAMERA;
            var selected=selector.filter(provider.getAvailableCameraInfos()).get(0);
            boolean portrait=selected.getSensorRotationDegrees(targetRotation)%180!=0;
            Size requested=portrait?new Size(sensorSize.getHeight(),sensorSize.getWidth()):sensorSize;
            boundPreviewWidth=preview.getWidth();boundPreviewHeight=preview.getHeight();
            cameraPreview=new Preview.Builder().setTargetResolution(requested).setTargetRotation(targetRotation).build();
            cameraPreview.setSurfaceProvider(preview.getSurfaceProvider());
            var builder=new ImageAnalysis.Builder().setTargetResolution(requested).setTargetRotation(targetRotation)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);
            try {
                var ranges=Camera2CameraInfo.from(selected).getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                android.util.Range<Integer> preferred=null;
                if(ranges!=null)for(var range:ranges)if(range.contains(targetFps)&&(preferred==null||range.getLower()>preferred.getLower()||(range.getLower().equals(preferred.getLower())&&range.getUpper()<preferred.getUpper())))preferred=range;
                if(preferred!=null)new Camera2Interop.Extender<>(builder).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,preferred);
            }catch(Exception ignored){}
            analysis=builder.build();
            analysis.setAnalyzer(worker,this::captureFrame);
            var viewport=new ViewPort.Builder(new android.util.Rational(requested.getWidth(),requested.getHeight()),targetRotation).setScaleType(ViewPort.FIT).build();
            var group=new UseCaseGroup.Builder().addUseCase(cameraPreview).addUseCase(analysis).setViewPort(viewport).build();
            camera = provider.bindToLifecycle(this,selector,group);
            cameraReady=true;applyCameraControls();
            if(pendingUsbPair){pendingUsbPair=false;pair();}
            captureInfo.setText("Target " + requested.getWidth() + " × " + requested.getHeight() + " · " + targetFps + " fps · JPEG " + jpegQuality + "%");
        }catch(Exception e){status.setText("Could not select camera: "+e.getMessage());}
    }
    private void controlResult(com.google.common.util.concurrent.ListenableFuture<?> future, String error) {
        future.addListener(() -> { try { future.get(); } catch(Exception e) { status.setText(error + ". This camera may not support the selected setting."); } }, androidx.core.content.ContextCompat.getMainExecutor(this));
    }
    private void applyCameraControls() {
        if(camera==null)return;
        // PreviewView mirrors the front camera itself. Match it to the chosen transmitted mirror.
        preview.setScaleX(front ? (mirrorOutput ? 1 : -1) : (mirrorOutput ? -1 : 1));
        var zoom = camera.getCameraInfo().getZoomState().getValue();
        if(zoom!=null && zoom.getMaxZoomRatio()>zoom.getMinZoomRatio())
            controlResult(camera.getCameraControl().setLinearZoom(zoomProgress/100f), "Zoom unavailable");
        var exposure=camera.getCameraInfo().getExposureState();
        if(exposure.isExposureCompensationSupported()){
            exposureIndex=Math.max(exposure.getExposureCompensationRange().getLower(),Math.min(exposure.getExposureCompensationRange().getUpper(),exposureIndex));
            controlResult(camera.getCameraControl().setExposureCompensationIndex(exposureIndex), "Exposure unavailable");
        }
        boolean hasFlash=camera.getCameraInfo().hasFlashUnit();
        if(!hasFlash)torchOn=false;
        torch.setEnabled(hasFlash);torch.setText(torchOn?"Torch on":"Torch off");
        if(hasFlash)controlResult(camera.getCameraControl().enableTorch(torchOn), "Torch unavailable");
    }
    private TextView settingLabel(LinearLayout panel,String value){
        TextView label=new TextView(this);label.setText(value);label.setPadding(8,18,8,4);panel.addView(label);return label;
    }
    private Spinner settingChoice(LinearLayout panel,String label,String[] values,int selected){
        settingLabel(panel,label);Spinner spinner=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);spinner.setSelection(selected);panel.addView(spinner);return spinner;
    }
    private void showCameraSettings(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(24,8,24,16);
        Spinner aspectChoice=settingChoice(panel,"Output framing (full phone view is the default)",new String[]{"Full phone view","Crop to 16:9 landscape","Crop to 9:16 portrait","Crop to 4:3"},captureAspect);
        Spinner resolution=settingChoice(panel,"Capture resolution",new String[]{"360p · 640 × 360","720p · 1280 × 720","1080p · 1920 × 1080","4K · 3840 × 2160 · Coming soon"},resolutionIndex);
        ArrayAdapter<String> resolutionAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,new String[]{"360p · 640 × 360","720p · 1280 × 720","1080p · 1920 × 1080","4K · Coming soon"}){
            @Override public boolean areAllItemsEnabled(){return false;}
            @Override public boolean isEnabled(int position){return position<3;}
            @Override public android.view.View getDropDownView(int position,android.view.View recycled,android.view.ViewGroup parent){var view=super.getDropDownView(position,recycled,parent);view.setEnabled(position<3);view.setAlpha(position<3?1f:.4f);return view;}
        };resolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);resolution.setAdapter(resolutionAdapter);resolution.setSelection(resolutionIndex);
        Spinner fps=settingChoice(panel,"Target frame rate",new String[]{"15 fps","24 fps","30 fps"},targetFps==15?0:targetFps==24?1:2);
        Spinner quality=settingChoice(panel,"JPEG quality",new String[]{"Efficient · 55%","Balanced · 70%","High · 85%"},jpegQuality==55?0:jpegQuality==85?2:1);
        CheckBox audioPlanned=new CheckBox(this);audioPlanned.setText("Microphone audio · Off · Planned");audioPlanned.setChecked(false);audioPlanned.setEnabled(false);panel.addView(audioPlanned);
        settingLabel(panel,"Audio needs synchronized transport/playback. Studio mode needs a supported concurrent front/rear camera pair and more processing. Neither feature is available yet.");
        CheckBox adaptive=new CheckBox(this);adaptive.setText("Adaptive compression · preserve resolution");adaptive.setChecked(adaptiveEnabled);panel.addView(adaptive);
        settingLabel(panel,"Lowers JPEG quality under processing/network pressure, then recovers. Resolution and target FPS stay unchanged. 4K is not available yet.");
        CheckBox mirror=new CheckBox(this);mirror.setText("Mirror transmitted video");mirror.setChecked(mirrorOutput);panel.addView(mirror);
        TextView zoomLabel=settingLabel(panel,"Camera zoom");
        SeekBar zoom=new SeekBar(this);zoom.setMax(100);zoom.setProgress(zoomProgress);panel.addView(zoom);
        var zoomState=camera==null?null:camera.getCameraInfo().getZoomState().getValue();
        zoom.setEnabled(zoomState!=null&&zoomState.getMaxZoomRatio()>zoomState.getMinZoomRatio());
        zoomLabel.setText(zoom.isEnabled()?"Camera zoom · "+zoomProgress+"% of device range":"Zoom unavailable on this camera");
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean user){zoomLabel.setText("Camera zoom · "+value+"% of device range");}
            public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}
        });
        var exposure=camera==null?null:camera.getCameraInfo().getExposureState();
        boolean supported=exposure!=null&&exposure.isExposureCompensationSupported();
        int low=supported?exposure.getExposureCompensationRange().getLower():0;
        int high=supported?exposure.getExposureCompensationRange().getUpper():0;
        TextView exposureLabel=settingLabel(panel,supported?"Exposure compensation · "+exposureIndex:"Exposure unavailable on this camera");
        SeekBar exposureBar=new SeekBar(this);exposureBar.setMax(high-low);exposureBar.setProgress(exposureIndex-low);exposureBar.setEnabled(supported);panel.addView(exposureBar);
        exposureBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean user){exposureLabel.setText("Exposure compensation · "+(value+low));}
            public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}
        });
        CheckBox keepAwake=new CheckBox(this);keepAwake.setText("Keep screen awake while streaming");keepAwake.setChecked(getPreferences(MODE_PRIVATE).getBoolean("awake",true));panel.addView(keepAwake);
        settingLabel(panel,"Settings apply when you tap Apply. Higher resolution and quality use more bandwidth and battery. The camera may choose a nearby supported size. Tap the preview to focus. Torch is off by default.");
        ScrollView scroll=new ScrollView(this);scroll.addView(panel);styleButtons(panel);
        new android.app.AlertDialog.Builder(this).setTitle("Camera settings").setView(scroll)
            .setNegativeButton("Cancel",null)
            .setNeutralButton("Reset", (dialog,which)->{
                resolutionIndex=0;captureAspect=0;targetFps=30;jpegQuality=70;adaptiveEnabled=true;adaptiveQuality.reset();mirrorOutput=false;zoomProgress=0;exposureIndex=0;torchOn=false;
                getPreferences(MODE_PRIVATE).edit().clear().putInt("framingVersion",1).putBoolean("awake",true).apply();
                updateKeepAwake();if(provider!=null)bindCamera();
            })
            .setPositiveButton("Apply",(dialog,which)->{
                boolean rebind=resolutionIndex!=resolution.getSelectedItemPosition()||targetFps!=new int[]{15,24,30}[fps.getSelectedItemPosition()];
                captureAspect=aspectChoice.getSelectedItemPosition();
                resolutionIndex=Math.min(2,resolution.getSelectedItemPosition());
                targetFps=new int[]{15,24,30}[fps.getSelectedItemPosition()];
                jpegQuality=new int[]{55,70,85}[quality.getSelectedItemPosition()];
                adaptiveEnabled=adaptive.isChecked();
                mirrorOutput=mirror.isChecked();zoomProgress=zoom.getProgress();exposureIndex=exposureBar.getProgress()+low;
                getPreferences(MODE_PRIVATE).edit().putInt("aspect",captureAspect).putInt("resolution",resolutionIndex).putInt("fps",targetFps).putInt("quality",jpegQuality).putBoolean("adaptive",adaptiveEnabled).putBoolean("mirror",mirrorOutput).putInt("zoom",zoomProgress).putBoolean("awake",keepAwake.isChecked()).apply();
                updateKeepAwake();if(provider!=null){if(rebind)bindCamera();else applyCameraControls();}
                status.setText((streaming?"Connected":"Ready")+" · "+targetFps+" fps target · JPEG "+jpegQuality+"%");
            }).show();
    }
    private void updateKeepAwake(){
        if(streaming&&getPreferences(MODE_PRIVATE).getBoolean("awake",true))getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void pair() {
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){status.setText("Allow camera access first.");return;}
        String h=pairingHost;
        String pin=pairingFingerprint.toUpperCase(Locale.ROOT);
        String c=pairingCode;
        if(!h.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")||!pin.matches("[A-F0-9]{64}")||!c.matches("[0-9]{6}")){
            status.setText("Choose Wi-Fi and scan the PC QR, or connect from Windows using USB.");return;
        }
        address="https://"+h+":8788";
        final int attempt=++generation;
        connect.setEnabled(false);status.setText("Checking desktop and pairing...");
        worker.execute(()->{
            try {
                sockets=createPinnedSockets(pin);
                byte[] body=new JSONObject().put("code",c).put("device",android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                HttpsURLConnection connection=open("/api/pair");connection.setRequestProperty("Content-Type","application/json");
                try {
                    try(OutputStream output=connection.getOutputStream()){output.write(body);}
                    int response=connection.getResponseCode();
                    if(response!=200)throw new IOException("Pairing rejected ("+response+"). Reset pairing on PC and check the code.");
                    ByteArrayOutputStream reply=new ByteArrayOutputStream();byte[] chunk=new byte[4096];int count;
                    try(InputStream input=connection.getInputStream()){while((count=input.read(chunk))!=-1)reply.write(chunk,0,count);}
                    String newToken=new JSONObject(reply.toString("UTF-8")).getString("token");
                    if(generation!=attempt)return;
                    token=newToken;adaptiveQuality.reset();pendingFrames.clear();cadence.reset();infoStart=0;infoFrames=0;streaming=true;
                    sender.execute(()->uploadStream(attempt));
                }finally{connection.disconnect();}
                runOnUiThread(()->{if(generation==attempt){connect.setEnabled(true);connect.setVisibility(android.view.View.VISIBLE);connect.setText("Stop");updateKeepAwake();connectionPanel.setVisibility(android.view.View.GONE);status.setText("Connected - auto rotation - " + targetFps + " fps target");}});
            }catch(Exception e){runOnUiThread(()->{if(generation==attempt){connect.setEnabled(true);status.setText("Connection failed: "+e.getMessage());}});}
        });
    }
    private SSLSocketFactory createPinnedSockets(String expected) throws Exception {
        TrustManager[] managers={new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] chain,String auth) throws java.security.cert.CertificateException { throw new java.security.cert.CertificateException("Client certificates unsupported"); }
            public void checkServerTrusted(X509Certificate[] chain,String auth) throws java.security.cert.CertificateException {
                try {
                    if(chain.length==0) throw new Exception("Missing server certificate");
                    chain[0].checkValidity();
                    byte[] hash=MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());
                    StringBuilder actual=new StringBuilder(); for(byte b:hash) actual.append(String.format(Locale.ROOT,"%02X",b));
                    if(!MessageDigest.isEqual(actual.toString().getBytes(), expected.getBytes())) throw new Exception("Desktop fingerprint does not match");
                } catch(Exception e){throw new java.security.cert.CertificateException(e);}
            }
        }};
        SSLContext ssl=SSLContext.getInstance("TLS"); ssl.init(null,managers,null); return ssl.getSocketFactory();
    }
    private HttpsURLConnection open(String path) throws Exception {
        HttpsURLConnection c=(HttpsURLConnection)new URL(address+path).openConnection();
        c.setSSLSocketFactory(sockets);
        // Identity is authenticated by the exact user-confirmed certificate fingerprint.
        c.setHostnameVerifier((hostname,session)->true);
        c.setConnectTimeout(3000); c.setReadTimeout(3000); c.setRequestMethod("POST"); c.setDoOutput(true); return c;
    }

    private void uploadStream(int attempt) {
        HttpsURLConnection connection=null;
        try {
            connection=open("/api/stream");activeUpload=connection;
            connection.setRequestProperty("Content-Type","application/x-localcam-frames");
            connection.setRequestProperty("Authorization","Bearer "+token);
            connection.setChunkedStreamingMode(16384);
            try(DataOutputStream output=new DataOutputStream(connection.getOutputStream())){
                while(streaming&&generation==attempt){
                    byte[] jpeg=pendingFrames.poll(250,TimeUnit.MILLISECONDS);
                    if(jpeg!=null){long start=SystemClock.elapsedRealtimeNanos();FrameProtocol.writeFrame(output,jpeg);writeMillis=(SystemClock.elapsedRealtimeNanos()-start)/1_000_000d;uploadedBytes+=jpeg.length;uploadedFrames++;}
                }
            }
        }catch(Exception e){
            if(streaming&&generation==attempt)runOnUiThread(()->{if(generation==attempt){stopStream();status.setText("Stream stopped: "+e.getMessage()+". Reset pairing on PC before reconnecting.");}});
        }finally{if(connection!=null)connection.disconnect();if(activeUpload==connection)activeUpload=null;}
    }
    private void captureFrame(ImageProxy image) {
        try {
            long now=SystemClock.elapsedRealtimeNanos();
            if(!streaming||!cadence.ready(now,targetFps))return;
            Rect crop=image.getCropRect();
            int left=(crop.left+1)&~1,top=(crop.top+1)&~1,right=crop.right&~1,bottom=crop.bottom&~1;
            int width=right-left,height=bottom-top,needed=width*height*3/2;
            if(nv21==null||nv21.length!=needed)nv21=new byte[needed];
            var planes=image.getPlanes();
            YuvFrames.packCrop(planes[0].getBuffer().duplicate(),planes[0].getRowStride(),planes[0].getPixelStride(),
                planes[1].getBuffer().duplicate(),planes[1].getRowStride(),planes[1].getPixelStride(),
                planes[2].getBuffer().duplicate(),planes[2].getRowStride(),planes[2].getPixelStride(),left,top,right,bottom,nv21);
            long packedAt=SystemClock.elapsedRealtimeNanos();packMillis+=(packedAt-now)/1_000_000d;
            int degrees=OrientationMath.outputDegrees(image.getImageInfo().getRotationDegrees(),outputRotation);
            var shape=YuvFrames.shape(width,height,degrees,captureAspect);
            boolean metadataOrientation=captureAspect==0;
            int jpegWidth=metadataOrientation?width:shape.width(),jpegHeight=metadataOrientation?height:shape.height();
            byte[] jpegInput=nv21;
            if(!metadataOrientation&&(degrees!=0||mirrorOutput||shape.left()!=0||shape.top()!=0||shape.width()!=width||shape.height()!=height)){
                int size=shape.width()*shape.height()*3/2;if(preparedYuv==null||preparedYuv.length!=size)preparedYuv=new byte[size];
                YuvFrames.transform(nv21,width,height,degrees,mirrorOutput,shape,preparedYuv);jpegInput=preparedYuv;
            }
            long rotatedAt=SystemClock.elapsedRealtimeNanos();rotateMillis+=(rotatedAt-packedAt)/1_000_000d;
            effectiveQuality=adaptiveQuality.quality(jpegQuality,adaptiveEnabled);
            encoded.reset();
            if(!new YuvImage(jpegInput,ImageFormat.NV21,jpegWidth,jpegHeight,null).compressToJpeg(new Rect(0,0,jpegWidth,jpegHeight),effectiveQuality,encoded))throw new IOException("JPEG encoding failed");
            byte[] jpeg=encoded.toByteArray();if(metadataOrientation)jpeg=JpegOrientation.add(jpeg,degrees,mirrorOutput);
            long finished=SystemClock.elapsedRealtimeNanos();jpegMillis+=(finished-rotatedAt)/1_000_000d;
            encodeMillis+=(finished-now)/1_000_000d;
            if(pendingFrames.poll()!=null)queueDrops++;pendingFrames.offer(jpeg);
            if(infoStart==0){infoStart=now;infoFrames=0;previousUploads=uploadedFrames;previousBytes=uploadedBytes;encodeMillis=packMillis=rotateMillis=jpegMillis=0;queueDrops=0;}infoFrames++;
            if(now-infoStart>=1_000_000_000L){
                double seconds=(now-infoStart)/1_000_000_000d;
                adaptiveQuality.observe(encodeMillis/Math.max(1,infoFrames),writeMillis,queueDrops,targetFps,adaptiveEnabled);
                final String detail=shape.width()+" × "+shape.height()+" · JPEG "+effectiveQuality+"% · "+String.format(Locale.ROOT,"%.1f capture / %.1f sent fps\nPack %.1f / rotate %.1f / JPEG %.1f ms\nSend %.1f ms · %.1f Mbps · %d queued drops",infoFrames/seconds,(uploadedFrames-previousUploads)/seconds,packMillis/infoFrames,rotateMillis/infoFrames,jpegMillis/infoFrames,writeMillis,(uploadedBytes-previousBytes)*8/seconds/1_000_000,queueDrops);
                previousUploads=uploadedFrames;previousBytes=uploadedBytes;infoStart=now;infoFrames=0;encodeMillis=packMillis=rotateMillis=jpegMillis=0;queueDrops=0;runOnUiThread(()->captureInfo.setText(detail));
            }
        }catch(Exception e){runOnUiThread(()->{stopStream();status.setText("Capture failed: "+e.getMessage());});}
        finally{image.close();}
    }
    private void stopStream(){
        setFullscreen(false);
        generation++;streaming=false;token="";pairingCode="";pendingUsbPair=false;pendingFrames.clear();updateKeepAwake();torchOn=false;if(camera!=null&&camera.getCameraInfo().hasFlashUnit())camera.getCameraControl().enableTorch(false);torch.setText("Torch off");connect.setEnabled(true);connect.setText("Connect");connect.setVisibility(android.view.View.GONE);connectionPanel.setVisibility(android.view.View.VISIBLE);
        HttpsURLConnection connection=activeUpload;if(connection!=null)connection.disconnect();
        status.setText("Stopped. Reset pairing on PC before reconnecting.");
    }
    @Override protected void onResume(){super.onResume();if(orientation!=null&&orientation.canDetectOrientation())orientation.enable();if(provider!=null&&connectionChosen&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)bindCamera();}
    @Override protected void onStop(){if(orientation!=null)orientation.disable();stopStream();if(provider!=null)provider.unbindAll();cameraReady=false;super.onStop();}
    @Override protected void onDestroy(){streaming=false;worker.shutdownNow();sender.shutdownNow();super.onDestroy();}
}





