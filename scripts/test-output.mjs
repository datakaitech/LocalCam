import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';import fs from 'node:fs';import path from 'node:path';import https from 'node:https';import http from 'node:http';import vm from 'node:vm';import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),base='http://127.0.0.1:8787';
const child=spawn('dotnet',[path.join(root,'desktop/bin/Debug/net10.0-windows/LocalCam.Desktop.dll'),'--headless'],{cwd:root,stdio:['ignore','pipe','pipe']});let logs='';child.stdout.on('data',d=>logs+=d);child.stderr.on('data',d=>logs+=d);
const defaults={aspect:'Source',framing:'Fit',mirror:false,rotation:0,zoom:1};
const status=async()=>await(await fetch(base+'/api/status')).json();
const set=async(o,header=true)=>await fetch(base+'/api/output',{method:'PUT',headers:{'Content-Type':'application/json',...(header?{'X-LocalCam-Desktop':'1'}:{})},body:JSON.stringify(o)});
const phone=(route,body,type='application/json',token='')=>new Promise((resolve,reject)=>{const request=https.request({host:'127.0.0.1',port:8788,path:route,method:'POST',agent:false,rejectUnauthorized:false,headers:{'Content-Type':type,...(token?{Authorization:'Bearer '+token}:{})}},res=>{let text='';res.on('data',d=>text+=d);res.on('end',()=>resolve({status:res.statusCode,body:text}));});request.on('error',reject);request.end(body);});
const latest=async()=>Buffer.from(await(await fetch(base+'/api/latest')).arrayBuffer());
try{
 let ready=false;for(let i=0;i<100;i++){try{await status();ready=true;break;}catch{}await new Promise(r=>setTimeout(r,100));}assert.ok(ready);
 assert.equal((await set(defaults,false)).status,403);
 for(const invalid of [{aspect:'bad'},{zoom:4},{rotation:45},{framing:'Stretch'}])assert.equal((await set({...defaults,...invalid})).status,400);
 const s=await status(),paired=await phone('/api/pair',JSON.stringify({code:s.pairingCode,device:'zero-reencode test'}));assert.equal(paired.status,200);const token=JSON.parse(paired.body).token;
 const jpeg=fs.readFileSync(path.join(root,'scripts/tests/landscape.jpg'));
 assert.equal((await phone('/api/frame',jpeg,'image/jpeg',token)).status,204);
 for(const aspect of ['Source','16:9','4:3','1:1','9:16'])for(const rotation of [0,90,180,270]){
  assert.equal((await set({aspect,rotation,framing:'Fill',mirror:true,zoom:1.4})).status,200);
  assert.deepEqual(await latest(),jpeg,'view settings must never re-encode the original');
 }assert.equal((await status()).frames,1,'view changes do not count as captures');
 const packet=Buffer.alloc(jpeg.length+4);packet.writeInt32BE(jpeg.length);jpeg.copy(packet,4);
 assert.equal((await phone('/api/stream',packet,'application/x-localcam-frames',token)).status,204);assert.deepEqual(await latest(),jpeg);
 await new Promise((resolve,reject)=>{const req=http.get(base+'/stream.mjpg',res=>{let bytes=Buffer.alloc(0);res.on('data',d=>{bytes=Buffer.concat([bytes,d]);if(bytes.includes(jpeg)){req.destroy();resolve();}});});req.on('error',reject);req.setTimeout(4000,()=>req.destroy(new Error('MJPEG timeout')));});
 const script=await(await fetch(base+'/obs.js')).text();
 assert.match(await(await fetch(base+'/obs')).text(),/image-orientation:from-image/);
 for(let tag=1;tag<=8;tag++){
  const oriented=fs.readFileSync(path.join(root,'scripts/tests/oriented-'+tag+'.jpg'));
  assert.equal((await phone('/api/frame',oriented,'image/jpeg',token)).status,204);
  assert.deepEqual(await latest(),oriented,'EXIF orientation and compressed pixels must be preserved');
 }
 assert.equal((await phone('/api/frame',jpeg,'image/jpeg',token)).status,204);
 let mockState={streaming:true,output:{...defaults,aspect:'16:9',framing:'Fill'}};
 const nodes={feed:{naturalWidth:360,naturalHeight:640,style:{},addEventListener(){}},viewport:{style:{}},imagebox:{style:{}},blank:{style:{}}};
 const context=vm.createContext({document:{getElementById:id=>nodes[id]},innerWidth:1280,innerHeight:720,window:{addEventListener(){}},fetch:async()=>({ok:true,json:async()=>mockState}),setTimeout(){}});
 vm.runInContext(script,context);await new Promise(r=>setImmediate(r));
 assert.equal(nodes.viewport.style.width,'1280px');assert.equal(nodes.viewport.style.height,'720px');
 let bw=parseFloat(nodes.imagebox.style.width),bh=parseFloat(nodes.imagebox.style.height);assert.equal(bw/bh,360/640,'uniform scaling preserves source aspect');assert.ok(bh>720,'Fill crops portrait');
 mockState.output={...defaults,aspect:'16:9',framing:'Fit'};await vm.runInContext('status()',context);assert.ok(parseFloat(nodes.imagebox.style.width)<1280,'Fit keeps full portrait');
 mockState.output={...defaults,rotation:90,mirror:true};await vm.runInContext('status()',context);assert.match(nodes.feed.style.transform,/scaleX\(-1\) rotate\(90deg\)/);
 mockState.streaming=false;await vm.runInContext('status()',context);assert.equal(nodes.blank.style.display,'block');
 await set(defaults);assert.deepEqual(await latest(),jpeg);
 await fetch(base+'/api/revoke',{method:'POST',headers:{'X-LocalCam-Desktop':'1'}});assert.equal((await fetch(base+'/api/latest')).status,204);
 console.log('PASS: original JPEG unchanged across 20 aspect/rotation choices, continuous transport and OBS bytes, protected settings, uniform Fit/Fill layout, mirror/rotation, blank-on-disconnect.');
}catch(error){console.error(logs);throw error;}finally{child.kill();}
