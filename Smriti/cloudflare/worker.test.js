import {test} from 'node:test';
import assert from 'node:assert/strict';
import worker from './worker.js';

const request=()=>new Request('https://example.invalid',{method:'POST',headers:{
 'X-Smriti-Guest-ID':'00000000-0000-4000-8000-000000000000','CF-Connecting-IP':'192.0.2.1','Content-Type':'application/json'
},body:JSON.stringify({message:'What is Bihu?',context:{replyLanguage:'English',region:'Assam'}})});
const env={GEMINI_API_KEY:{get:async()=>'synthetic-secret'},AI_RATE_LIMIT:{limit:async()=>({success:true})}};

test('Worker success, provider failure, timeout, malformed response and quota are sanitized',async()=>{
 const original=globalThis.fetch;
 try {
  let calls=0;
  globalThis.fetch=async(url,options)=>{
   calls++;
   assert.match(url,/generativelanguage.googleapis.com/);
   assert.equal(options.headers['x-goog-api-key'],'synthetic-secret');
   return Response.json({candidates:[{finishReason:'STOP',content:{parts:[{text:'Bihu is celebrated in Assam.'}]}}]});
  };
  const success=await worker.fetch(request(),env);
  assert.equal(success.status,200);
  assert.equal((await success.json()).provider,'gemini');
  for(const behavior of ['status','timeout','malformed']) {
   globalThis.fetch=async()=>{
    if(behavior==='timeout')throw new Error('synthetic-secret should never escape');
    if(behavior==='status')return new Response('synthetic-secret',{status:429});
    return Response.json({candidates:[]});
   };
   const response=await worker.fetch(request(),env);
   assert.equal(response.status,503);
   assert.doesNotMatch(await response.text(),/synthetic-secret/);
  }
  globalThis.fetch=async()=>{throw Error('Must not call provider after quota rejection');};
  const limited=await worker.fetch(request(),{...env,AI_RATE_LIMIT:{limit:async()=>({success:false})}});
  assert.equal(limited.status,429);
  assert.equal(calls,1);
 } finally {globalThis.fetch=original;}
});
