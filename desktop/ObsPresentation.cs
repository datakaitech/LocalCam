namespace LocalCam;
internal static class ObsPresentation {
    public const string Script = """
const feed=document.getElementById('feed'),viewport=document.getElementById('viewport'),box=document.getElementById('imagebox');
let output={aspect:'Source',framing:'Fit',rotation:0,mirror:false,zoom:1};
function layout(){
 const w=feed.naturalWidth||640,h=feed.naturalHeight||360,side=output.rotation%180!==0;
 const sw=side?h:w,sh=side?w:h,ratios={'16:9':16/9,'4:3':4/3,'1:1':1,'9:16':9/16},ratio=ratios[output.aspect]||sw/sh;
 const vw=Math.min(innerWidth,innerHeight*ratio),vh=vw/ratio;
 viewport.style.width=vw+'px';viewport.style.height=vh+'px';
 const scale=(output.framing==='Fill'?Math.max(vw/sw,vh/sh):Math.min(vw/sw,vh/sh))*output.zoom;
 box.style.width=(w*scale)+'px';box.style.height=(h*scale)+'px';
 feed.style.transform='scaleX('+(output.mirror?-1:1)+') rotate('+output.rotation+'deg)';
}
feed.addEventListener('load',layout);window.addEventListener('resize',layout);
async function status(){try{
 const response=await fetch('/api/status');if(!response.ok)throw new Error('receiver unavailable');
 const state=await response.json();output=state.output||output;layout();
 document.getElementById('blank').style.display=state.streaming?'none':'block';
}catch{document.getElementById('blank').style.display='block'}setTimeout(status,150)}status();
""";
}
