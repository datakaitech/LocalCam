import assert from 'node:assert/strict';import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';import {spawn} from 'node:child_process';import {fileURLToPath} from 'node:url';import vm from 'node:vm';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),site=path.resolve(process.env.LOCALCAM_WEBSITE_ROOT||path.join(root,'../localcam-website/website')),base='http://127.0.0.1:8900';
const child=spawn('node',[path.join(site,'server.mjs')],{cwd:root,stdio:['ignore','pipe','pipe']});let logs='';child.stdout.on('data',d=>logs+=d);child.stderr.on('data',d=>logs+=d);
try{
 let ready=false;for(let i=0;i<80;i++){try{if((await fetch(base)).ok){ready=true;break;}}catch{}await new Promise(r=>setTimeout(r,100));}assert.ok(ready);
 const html=await(await fetch(base)).text();assert.match(html,/LocalCam by DataKai/);assert.match(html,/application\/ld\+json/);
 for(const target of ['styles.css','app.js','privacy.html','assets/localcam.svg','assets/localcam.png','robots.txt','sitemap.xml','downloads/LocalCam-Windows-0.8.zip','downloads/LocalCam-Android-0.8.apk','downloads/LocalCam-Source-0.8.zip','downloads/SHA256SUMS.txt']){
  const response=await fetch(base+'/'+target,{method:'HEAD'});assert.equal(response.status,200,target);assert.ok(Number(response.headers.get('content-length'))>0,target+' nonempty');
 }
 assert.equal((await fetch(base+'/missing.apk')).status,404);
 const sums=fs.readFileSync(path.join(site,'downloads/SHA256SUMS.txt'),'utf8').trim().split(/\r?\n/);
 for(const line of sums){const[expected,name]=line.split(/\s+/);const actual=crypto.createHash('sha256').update(fs.readFileSync(path.join(site,'downloads',name))).digest('hex');assert.equal(actual,expected,name+' integrity');}
 const tabs=['wifi','usb'].map(route=>({id:route+'-tab',dataset:{route},attributes:{},listeners:{},setAttribute(k,v){this.attributes[k]=v;},addEventListener(k,v){this.listeners[k]=v;},focus(){this.focused=true;}}));
 function element(tag){return{tag,children:[],append(...items){this.children.push(...items);},setAttribute(k,v){this[k]=v;},replaceChildren(){this.children=[];}};}
 const panel=element('div'),note=element('p'),document={querySelectorAll:()=>tabs,getElementById:id=>id==='steps'?panel:note,createElement:element};
 vm.runInNewContext(fs.readFileSync(path.join(site,'app.js'),'utf8'),{document});
 tabs[1].listeners.click();assert.equal(tabs[1].attributes['aria-selected'],'true');assert.equal(panel.children.length,3);assert.match(panel.children[1].children[2].textContent,/No IP address or code/);
 tabs[1].listeners.keydown({key:'ArrowLeft',preventDefault(){}});assert.equal(tabs[0].attributes['aria-selected'],'true');assert.equal(tabs[0].focused,true);assert.match(note.textContent,/private network/);
 console.log('PASS: static site served, all assets/downloads reachable, SHA-256 integrity, missing-file handling, Wi-Fi/USB tabs and keyboard navigation.');
}catch(error){console.error(logs);throw error;}finally{child.kill();}
