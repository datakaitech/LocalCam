import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';import http from 'node:http';import path from 'node:path';import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const child=spawn('dotnet',[path.join(root,'desktop/bin/Debug/net10.0-windows/LocalCam.Desktop.dll'),'--headless'],{cwd:root,stdio:'ignore'});
const request=(headers={})=>new Promise((resolve,reject)=>{http.get({hostname:'127.0.0.1',port:8787,path:'/api/status',headers},res=>{res.resume();res.on('end',()=>resolve({code:res.statusCode,headers:res.headers}));}).on('error',reject);});
try{
 let ready=false;for(let i=0;i<100;i++){try{if((await request()).code===200){ready=true;break;}}catch{}await new Promise(r=>setTimeout(r,100));}assert.ok(ready);
 assert.equal((await request({Host:'attacker.example:8787'})).code,403,'reject DNS rebinding');
 assert.equal((await request({Origin:'https://attacker.example'})).code,403,'reject cross-origin browser requests');
 const response=await request({Origin:'http://127.0.0.1:8787'});assert.equal(response.code,200);assert.match(response.headers['content-security-policy'],/frame-ancestors 'none'/);assert.equal(response.headers['cache-control'],'no-store');
 console.log('PASS: loopback host validation, cross-origin rejection, same-origin access, CSP and no-store.');
}finally{child.kill();}
