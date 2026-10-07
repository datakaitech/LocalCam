import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import https from 'node:https';
import http from 'node:http';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const existing=process.argv.includes('--existing');
const child=existing?null:spawn('dotnet',[path.join(root,'desktop/bin/Debug/net10.0-windows/LocalCam.Desktop.dll'),'--headless'],{cwd:path.join(root,'desktop'),stdio:['ignore','pipe','pipe']});
let logs='';child?.stdout.on('data',d=>logs+=d);child?.stderr.on('data',d=>logs+=d);
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const status=async()=>await(await fetch('http://127.0.0.1:8787/api/status')).json();
const reset=async()=>{assert.equal((await fetch('http://127.0.0.1:8787/api/revoke',{method:'POST',headers:{'X-LocalCam-Desktop':'1'}})).status,200);};
function upload(token,type='application/x-localcam-frames',route='/api/stream'){
  let request; let fingerprint;
  const done=new Promise((resolve,reject)=>{
    request=https.request({host:'127.0.0.1',port:8788,path:route,method:'POST',rejectUnauthorized:false,agent:false,headers:{Authorization:'Bearer '+token,'Content-Type':type}},response=>{
      response.resume();response.on('end',()=>resolve({status:response.statusCode,fingerprint}));
    });
    request.on('socket',socket=>socket.on('secureConnect',()=>{fingerprint=socket.getPeerCertificate().fingerprint256?.replaceAll(':','');}));
    request.on('error',reject);request.setTimeout(10000,()=>request.destroy(new Error('Upload timeout')));
  });
  done.catch(()=>{});return{request,done};
}
function packet(jpeg){const head=Buffer.alloc(4);head.writeInt32BE(jpeg.length);return Buffer.concat([head,jpeg]);}
async function waitForFrames(count){for(let i=0;i<100;i++){if((await status()).frames>=count)return;await sleep(20);}throw new Error('Frames did not arrive');}
async function pairOnce(){
  const s=await status();
  return await new Promise((resolve,reject)=>{
    const req=https.request({host:'127.0.0.1',port:8788,path:'/api/pair',method:'POST',rejectUnauthorized:false,agent:false,headers:{'Content-Type':'application/json'}},r=>{
      let body='';r.on('data',d=>body+=d);r.on('end',()=>{try{assert.equal(r.statusCode,200);resolve(JSON.parse(body).token);}catch(e){reject(e);}});
    });req.on('error',reject);req.end(JSON.stringify({code:s.pairingCode,device:'LocalCam test pattern'}));
  });
}
let active;
try{
  let ready=false;for(let i=0;i<100;i++){try{await status();ready=true;break;}catch{}await sleep(100);}
  assert.ok(ready,'receiver ready');await reset();const token=await pairOnce();
  const jpeg=fs.readFileSync(path.join(root,'scripts/tests/landscape.jpg'));
  const s=await status();
  active=upload(token);
  const first=packet(jpeg);
  active.request.write(first.subarray(0,1));await sleep(30);
  active.request.write(first.subarray(1,6));await sleep(30);
  active.request.write(first.subarray(6));await waitForFrames(1);
  assert.deepEqual(Buffer.from(await(await fetch('http://127.0.0.1:8787/api/latest')).arrayBuffer()),jpeg);
  const multipart=await new Promise((resolve,reject)=>{
    const req=http.get('http://127.0.0.1:8787/stream.mjpg',res=>{
      assert.match(res.headers['content-type'],/multipart\/x-mixed-replace/);
      let data=Buffer.alloc(0);
      res.on('data',chunk=>{data=Buffer.concat([data,chunk]);if(data.includes(jpeg)){req.destroy();resolve(data);}});
      res.on('error',e=>{if(e.code!=='ECONNRESET')reject(e);});
    });req.on('error',reject);req.setTimeout(4000,()=>req.destroy(new Error('OBS stream timeout')));
  });
  assert.ok(multipart.includes(Buffer.from('Content-Type: image/jpeg')));
  // Total request > 2 MiB while each individual frame stays bounded.
  const large=Buffer.alloc(65536);large[0]=255;large[1]=216;large[large.length-2]=255;large[large.length-1]=217;
  for(let i=0;i<40;i++)active.request.write(packet(large));
  active.request.end();let result=await active.done;active=null;
  assert.equal(result.status,204);assert.equal(result.fingerprint,s.fingerprint);
  assert.equal((await status()).frames,41);
  const oversized=upload(token);const bad=Buffer.alloc(4);bad.writeInt32BE(2*1024*1024+1);oversized.request.end(bad);assert.equal((await oversized.done).status,400);
  const truncated=upload(token);truncated.request.end(Buffer.from([0,0]));assert.equal((await truncated.done).status,400);
  active=upload(token);active.request.write(packet(jpeg));await waitForFrames(42);await reset();
  active.request.end(packet(jpeg));assert.equal((await active.done).status,401);active=null;
  assert.equal((await fetch('http://127.0.0.1:8787/api/latest')).status,204);
  console.log('PASS: split frame headers, persistent TLS stream, cumulative uploads above 2 MiB, per-frame bounds, truncated headers, real JPEG transport, multipart OBS output, revocation during upload, certificate fingerprint.');
  if(process.argv.includes('--preview')){
    const previewToken=await pairOnce();active=upload(previewToken);
    console.log('Displaying a landscape test image in native LocalCam for up to 60 seconds.');
    for(let i=0;i<1800;i++){active.request.write(packet(jpeg));await sleep(33);}
    active.request.end();await active.done;active=null;await reset();
  }
}catch(error){console.error(logs);throw error;}
finally{active?.request.destroy();child?.kill();}
