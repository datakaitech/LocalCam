package org.localcam.app;
import java.nio.ByteBuffer;
public final class YuvFrames {
 public record Shape(int width,int height,int left,int top){}
 public static Shape shape(int w,int h,int degrees,int aspect){
  if(w<2||h<2||(w&1)!=0||(h&1)!=0||degrees<0||degrees>=360||degrees%90!=0||aspect<0||aspect>3)throw new IllegalArgumentException("Invalid YUV dimensions");
  int ow=degrees%180==0?w:h,oh=degrees%180==0?h:w,rw=ow,rh=oh;
  if(aspect!=0){int a=aspect==1?16:aspect==2?9:4,b=aspect==1?9:aspect==2?16:3,units=Math.min(ow/a,oh/b)&~1;if(units==0)throw new IllegalArgumentException("Frame too small");rw=a*units;rh=b*units;}
  return new Shape(rw,rh,((ow-rw)/2)&~1,((oh-rh)/2)&~1);
 }
 public static void pack(ByteBuffer y,int ys,int yp,ByteBuffer u,int us,int up,ByteBuffer v,int vs,int vp,int w,int h,byte[] result){
  int yBase=y.position(),uBase=u.position(),vBase=v.position();
  for(int row=0;row<h;row++){if(yp==1){y.position(yBase+row*ys);y.get(result,row*w,w);}else for(int col=0;col<w;col++)result[row*w+col]=y.get(yBase+row*ys+col*yp);}
  int offset=w*h;
  for(int row=0;row<h/2;row++)for(int col=0;col<w/2;col++){result[offset++]=v.get(vBase+row*vs+col*vp);result[offset++]=u.get(uBase+row*us+col*up);}
 }
 public static void packCrop(ByteBuffer y,int ys,int yp,ByteBuffer u,int us,int up,ByteBuffer v,int vs,int vp,int left,int top,int right,int bottom,byte[] result){
  if(left<0||top<0||right<=left||bottom<=top||((left|top|right|bottom)&1)!=0)throw new IllegalArgumentException("Unaligned crop");
  y.position(y.position()+top*ys+left*yp);u.position(u.position()+(top/2)*us+(left/2)*up);v.position(v.position()+(top/2)*vs+(left/2)*vp);
  pack(y,ys,yp,u,us,up,v,vs,vp,right-left,bottom-top,result);
 }
 // Transpose in cache-sized tiles; calculate coordinates once per row, not once per pixel.
 private static void copyRotated(byte[] src,int offset,int width,int height,int left,int top,int rw,int rh,int d,boolean mirror,byte[] dest,int destOffset,int bytes){
  int ow=d%180==0?width:height;
  int step=(d==90?-width:d==180?-1:d==270?width:1)*bytes;if(mirror)step=-step;
  int tile=d%180==0?Math.max(rw,rh):32;
  for(int by=0;by<rh;by+=tile)for(int bx=0;bx<rw;bx+=tile){
   int maxY=Math.min(rh,by+tile),maxX=Math.min(rw,bx+tile);
   for(int y=by;y<maxY;y++){
    int ox=left+bx,oy=top+y;if(mirror)ox=ow-1-ox;
    int sx=d==90?oy:d==180?width-1-ox:d==270?width-1-oy:ox;
    int sy=d==90?height-1-ox:d==180?height-1-oy:d==270?ox:oy;
    int source=offset+(sy*width+sx)*bytes,target=destOffset+(y*rw+bx)*bytes;
    if(bytes==1){for(int x=bx;x<maxX;x++){dest[target++]=src[source];source+=step;}}
    else {for(int x=bx;x<maxX;x++){dest[target++]=src[source];dest[target++]=src[source+1];source+=step;}}
   }
  }
 }
 public static void transform(byte[] src,int w,int h,int d,boolean mirror,Shape s,byte[] dest){
  int ow=d%180==0?w:h,rw=s.width(),rh=s.height();
  if(src.length<w*h*3/2||dest.length<rw*rh*3/2)throw new IllegalArgumentException("Short YUV buffer");
  if(d==0&&!mirror){for(int y=0;y<rh;y++)System.arraycopy(src,(y+s.top())*w+s.left(),dest,y*rw,rw);for(int y=0;y<rh/2;y++)System.arraycopy(src,w*h+(y+s.top()/2)*w+s.left(),dest,rw*rh+y*rw,rw);return;}
  copyRotated(src,0,w,h,s.left(),s.top(),rw,rh,d,mirror,dest,0,1);
  copyRotated(src,w*h,w/2,h/2,s.left()/2,s.top()/2,rw/2,rh/2,d,mirror,dest,rw*rh,2);

 }
}

