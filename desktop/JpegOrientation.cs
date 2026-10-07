using System.Buffers.Binary;
namespace LocalCam;
internal static class JpegOrientation {
    // Parse metadata only; this never decodes or re-encodes the JPEG.
    public static int Read(byte[] data) {
        try {
            if(data.Length<4 || data[0]!=255 || data[1]!=216)return 1;
            int p=2;
            while(p+4<=data.Length){
                if(data[p++]!=255)return 1;
                while(p<data.Length&&data[p]==255)p++;
                if(p>=data.Length)return 1;
                int marker=data[p++];if(marker is 218 or 217)return 1;
                if(marker==1||marker is >=208 and <=215)continue;
                int length=BinaryPrimitives.ReadUInt16BigEndian(data.AsSpan(p,2));
                if(length<2||p+length>data.Length)return 1;
                if(marker==225&&length>=16&&data.AsSpan(p+2,6).SequenceEqual("Exif\0\0"u8)){
                    var tiff=data.AsSpan(p+8,length-8);bool little=tiff[0]==73&&tiff[1]==73;
                    if(!little&&!(tiff[0]==77&&tiff[1]==77))return 1;
                    ushort U16(int offset)=>little?BinaryPrimitives.ReadUInt16LittleEndian(data.AsSpan(p+8+offset,2)):BinaryPrimitives.ReadUInt16BigEndian(data.AsSpan(p+8+offset,2));
                    uint U32(int offset)=>little?BinaryPrimitives.ReadUInt32LittleEndian(data.AsSpan(p+8+offset,4)):BinaryPrimitives.ReadUInt32BigEndian(data.AsSpan(p+8+offset,4));
                    if(U16(2)!=42)return 1;
                    uint offset=U32(4);if(offset>tiff.Length-2)return 1;
                    int count=U16((int)offset),start=(int)offset+2;
                    for(int i=0;i<count;i++){
                        int entry=start+i*12;if(entry+12>tiff.Length)return 1;
                        if(U16(entry)==274&&U16(entry+2)==3&&U32(entry+4)==1){int value=U16(entry+8);return value is >=1 and <=8?value:1;}
                    }
                }
                p+=length;
            }
        }catch(ArgumentOutOfRangeException){}catch(OverflowException){}
        return 1;
    }
    public static (int Rotation,bool Mirror) Compose(int tag,OutputSettings settings){
        int rotation=tag switch{3 or 4=>180,5 or 6=>90,7 or 8=>270,_=>0};
        bool mirror=tag is 2 or 4 or 5 or 7;
        return ((rotation+(mirror?-settings.Rotation:settings.Rotation)+360)%360,mirror^settings.Mirror);
    }
}
