import assert from 'node:assert/strict';
import fs from 'node:fs';
import {spawn} from 'node:child_process';
import https from 'node:https';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const child=spawn('dotnet',[path.join(root,'desktop/bin/Debug/net10.0-windows/LocalCam.Desktop.dll'),'--headless'],{cwd:path.join(root,'desktop'),stdio:['ignore','pipe','pipe'],env:{...process.env,DOTNET_CLI_TELEMETRY_OPTOUT:'1'}});
let output='';child.stdout.on('data',d=>output+=d);child.stderr.on('data',d=>output+=d);
function phone(route,body,type='application/json',token=''){
  return new Promise((resolve,reject)=>{
    const req=https.request({host:'127.0.0.1',port:8788,path:route,method:'POST',rejectUnauthorized:false,
      headers:{'Content-Type':type,...(token?{Authorization:'Bearer '+token}:{})}},res=>{
      let content='';res.on('data',d=>content+=d);res.on('end',()=>resolve({status:res.statusCode,body:content,cert:res.socket?.getPeerCertificate()?.fingerprint256}));
    });req.on('error',reject);req.end(body);
  });
}
try{
  let ready=false;
  for(let i=0;i<80;i++){
    if(child.exitCode!==null)throw new Error(output);
    try{const r=await fetch('http://127.0.0.1:8787/api/status');if(r.ok){ready=true;break;}}catch{}
    await new Promise(r=>setTimeout(r,100));
  }
  assert.ok(ready,'receiver starts');
  const status=await (await fetch('http://127.0.0.1:8787/api/status')).json();
  assert.match(status.fingerprint,/^[A-F0-9]{64}$/);
  assert.match(status.pairingCode,/^[0-9]{6}$/);
  const qr=await(await fetch('http://127.0.0.1:8787/api/pairing?host=127.0.0.1')).json();
  const details=JSON.parse(qr.payload);
  assert.deepEqual(details,{type:'localcam',version:1,host:'127.0.0.1',port:8788,fingerprint:status.fingerprint,code:status.pairingCode});
  assert.equal((await fetch('http://127.0.0.1:8787/api/pairing?host=example.com')).status,400);
  fs.writeFileSync(path.join(root,'scripts/tests/pairing-qr.png'),Buffer.from(qr.pngBase64,'base64'));
  fs.writeFileSync(path.join(root,'scripts/tests/pairing-qr.json'),qr.payload);
  assert.equal((await fetch('http://127.0.0.1:8787/')).status,200);
  assert.equal((await fetch('http://127.0.0.1:8787/obs')).status,200);
  assert.equal((await fetch('http://127.0.0.1:8787/api/latest')).status,204);
  assert.equal((await phone('/api/pair',JSON.stringify({code:'wrong'}))).status,401);
  assert.equal((await phone('/api/frame',Buffer.from([255,216,255,217]),'image/jpeg','fake')).status,401);
  const paired=await phone('/api/pair',JSON.stringify({code:status.pairingCode,device:'Test phone'}));
  assert.equal(paired.status,200);
  const token=JSON.parse(paired.body).token;
  assert.equal((await phone('/api/pair',JSON.stringify({code:status.pairingCode}))).status,401);
  assert.equal((await phone('/api/frame','bad','image/jpeg',token)).status,400);
  assert.equal((await phone('/api/frame','bad','text/plain',token)).status,415);
  // Minimal marker fixture checks transport bytes; physical camera JPEG decoding is a separate test.
  const jpeg=Buffer.from([255,216,255,217]);
  assert.equal((await phone('/api/frame',jpeg,'image/jpeg',token)).status,204);
  const latest=await fetch('http://127.0.0.1:8787/api/latest');
  assert.deepEqual(Buffer.from(await latest.arrayBuffer()),jpeg);
  const live=await (await fetch('http://127.0.0.1:8787/api/status')).json();
  assert.equal(live.streaming,true);assert.equal(live.frames,1);
  assert.equal((await fetch('http://127.0.0.1:8787/api/revoke',{method:'POST'})).status,403);
  assert.equal((await fetch('http://127.0.0.1:8787/api/revoke',{method:'POST',headers:{'X-LocalCam-Desktop':'1'}})).status,200);
  assert.equal((await phone('/api/frame',jpeg,'image/jpeg',token)).status,401);
  assert.equal((await fetch('http://127.0.0.1:8787/api/latest')).status,204);
  console.log('PASS: startup, dashboard, OBS page, pairing rejection, one-phone pairing, unauthorized uploads, JPEG validation, preview bytes, live status, revoke protection, token invalidation.');
}catch(error){console.error(output);throw error;}finally{child.kill();}
