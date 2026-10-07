using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
namespace LocalCam;
internal static class VideoLayout {
    public static void Apply(Image image, Border viewport, Canvas canvas, double availableWidth, double availableHeight, int orientation, OutputSettings settings) {
        if(image.Source is not BitmapSource source || availableWidth<=0 || availableHeight<=0)return;
        var transform=JpegOrientation.Compose(orientation,settings);
        bool side=transform.Rotation%180!=0;
        double width=side?source.PixelHeight:source.PixelWidth,height=side?source.PixelWidth:source.PixelHeight;
        double ratio=settings.Aspect switch {"16:9"=>16d/9,"4:3"=>4d/3,"1:1"=>1,"9:16"=>9d/16,_=>width/height};
        double viewWidth=Math.Min(availableWidth,availableHeight*ratio),viewHeight=viewWidth/ratio;
        viewport.Width=canvas.Width=viewWidth;viewport.Height=canvas.Height=viewHeight;
        double scale=settings.Framing=="Fill"?Math.Max(viewWidth/width,viewHeight/height):Math.Min(viewWidth/width,viewHeight/height);
        image.Width=source.PixelWidth*scale;image.Height=source.PixelHeight*scale;
        image.Stretch=Stretch.Fill;image.LayoutTransform=Transform.Identity;
        Canvas.SetLeft(image,(viewWidth-image.Width)/2);Canvas.SetTop(image,(viewHeight-image.Height)/2);
        image.RenderTransformOrigin=new Point(.5,.5);
        image.RenderTransform=new TransformGroup {Children={new RotateTransform(transform.Rotation),new ScaleTransform((transform.Mirror?-1:1)*settings.Zoom,settings.Zoom)}};
    }
}
