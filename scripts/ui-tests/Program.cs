using System.Reflection;
using System.Windows;
using System.Windows.Controls;
static class UiTests {
 [STAThread] static void Main(){
  var assembly=Assembly.Load("LocalCam.Desktop");
  var factory=assembly.GetType("LocalCam.MainWindow")!.GetMethod("Button",BindingFlags.NonPublic|BindingFlags.Static)!;
  var button=(Button)factory.Invoke(null,new object[]{"Test",(Action)(()=>{})})!;
  if(!button.ApplyTemplate()||button.Template.FindName("surface",button)==null)throw new Exception("Button template could not instantiate");
  button.IsEnabled=false;button.Measure(new Size(300,80));
  var orientation=assembly.GetType("LocalCam.JpegOrientation")!;
  var read=orientation.GetMethod("Read")!;var compose=orientation.GetMethod("Compose")!;
  var settingsType=assembly.GetType("LocalCam.OutputSettings")!;
  (int X,int Y) Apply(int rotation,bool mirror,int x,int y){var point=rotation switch{90=>(-y,x),180=>(-x,-y),270=>(y,-x),_=>(x,y)};return (mirror?-point.Item1:point.Item1,point.Item2);}
  var sourcePoints=new[]{(1,2),(-1,2),(-1,-2),(1,-2),(2,1),(-2,1),(-2,-1),(2,-1)};
  for(int tag=1;tag<=8;tag++){
   byte[] jpeg=System.IO.File.ReadAllBytes(System.IO.Path.Combine(AppContext.BaseDirectory,"../../../../tests/oriented-"+tag+".jpg"));
   if((int)read.Invoke(null,new object[]{jpeg})! !=tag)throw new Exception("EXIF parser "+tag);
   using var stream=new System.IO.MemoryStream(jpeg);var bitmap=new System.Windows.Media.Imaging.BitmapImage();bitmap.BeginInit();bitmap.CacheOption=System.Windows.Media.Imaging.BitmapCacheOption.OnLoad;bitmap.StreamSource=stream;bitmap.EndInit();
   if(bitmap.PixelWidth<=bitmap.PixelHeight)throw new Exception("WPF unexpectedly auto-oriented raw JPEG dimensions");
   for(int length=0;length<Math.Min(jpeg.Length,80);length++)read.Invoke(null,new object[]{jpeg.Take(length).ToArray()});
   foreach(int rotate in new[]{0,90,180,270})foreach(bool mirror in new[]{false,true}){
    var settings=Activator.CreateInstance(settingsType,new object[]{"Source","Fit",mirror,rotate,1d})!;
    var combined=((int Rotation,bool Mirror))compose.Invoke(null,new[]{(object)tag,settings})!;
    var source=sourcePoints[tag-1];if(Apply(combined.Rotation,combined.Mirror,1,2)!=Apply(rotate,mirror,source.Item1,source.Item2))throw new Exception("EXIF/user transform composition");
   }
  }
  var layout=assembly.GetType("LocalCam.VideoLayout")!.GetMethod("Apply")!;
  byte[] white=Enumerable.Repeat((byte)255,1280*720*4).ToArray();
  var solid=System.Windows.Media.Imaging.BitmapSource.Create(1280,720,96,96,System.Windows.Media.PixelFormats.Bgra32,null,white,1280*4);
  var legacyImage=new Image{Source=solid,Stretch=System.Windows.Media.Stretch.Uniform,LayoutTransform=new System.Windows.Media.RotateTransform(90),RenderTransformOrigin=new Point(.5,.5),RenderTransform=new System.Windows.Media.ScaleTransform(-1,1)};
  var legacyViewport=new Border{Width=281.25,Height=500,Child=legacyImage,ClipToBounds=true,Background=System.Windows.Media.Brushes.Black};
  var legacySize=new Size(281.25,500);legacyViewport.Measure(legacySize);legacyViewport.Arrange(new Rect(legacySize));legacyViewport.UpdateLayout();
  var legacyRaster=new System.Windows.Media.Imaging.RenderTargetBitmap(282,500,96,96,System.Windows.Media.PixelFormats.Pbgra32);legacyRaster.Render(legacyViewport);
  byte[] legacyPixels=new byte[282*500*4];legacyRaster.CopyPixels(legacyPixels,282*4,0);
  if(legacyPixels[(5*282+5)*4]>=240)throw new Exception("Legacy portrait mirror clipping did not reproduce.");
  Console.WriteLine("PASS: previous mixed LayoutTransform/RenderTransform path reproduces portrait mirror clipping.");
  foreach(int tag in Enumerable.Range(1,8))foreach(int rotate in new[]{0,90,180,270})foreach(bool mirror in new[]{false,true})foreach(string aspect in new[]{"Source","16:9"}){
   var image=new Image{Source=solid};var canvas=new Canvas();canvas.Children.Add(image);
   var viewport=new Border{Child=canvas,ClipToBounds=true,Background=System.Windows.Media.Brushes.Black};
   var settings=Activator.CreateInstance(settingsType,new object[]{aspect,aspect=="Source"?"Fit":"Fill",mirror,rotate,1d})!;
   layout.Invoke(null,new object[]{image,viewport,canvas,900d,500d,tag,settings});
   var size=new Size(viewport.Width,viewport.Height);viewport.Measure(size);viewport.Arrange(new Rect(size));viewport.UpdateLayout();
   var rendered=new System.Windows.Media.Imaging.RenderTargetBitmap((int)Math.Ceiling(size.Width),(int)Math.Ceiling(size.Height),96,96,System.Windows.Media.PixelFormats.Pbgra32);rendered.Render(viewport);
   int stride=rendered.PixelWidth*4;byte[] pixels=new byte[stride*rendered.PixelHeight];rendered.CopyPixels(pixels,stride,0);
   foreach(var point in new[]{(5,5),(rendered.PixelWidth-6,5),(5,rendered.PixelHeight-6),(rendered.PixelWidth-6,rendered.PixelHeight-6),(rendered.PixelWidth/2,rendered.PixelHeight/2)}){
    if(pixels[point.Item2*stride+point.Item1*4]<240)throw new Exception($"Rendered video clipped: orientation {tag}, rotation {rotate}, mirror {mirror}, aspect {aspect}");
   }
  }
  Console.WriteLine("PASS: production video layout rasterizes the entire viewport for 128 orientation/rotation/mirror Source Fit and widescreen Fill cases, including portrait edges.");
  Console.WriteLine("PASS: Java EXIF JPEGs decode on WPF, all orientation/mirror compositions and truncated metadata.");
  Console.WriteLine("PASS: production WPF button template loads and instantiates, including disabled state.");
 }
}
