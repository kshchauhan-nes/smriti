import {createRemoteJWKSet,jwtVerify} from 'jose';
import {requestData,parseReply,geminiKey} from './core.js';
const keys=createRemoteJWKSet(new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'));
const json=(value,status=200)=>Response.json(value,{status,headers:{'Cache-Control':'no-store'}});
export default {
 async fetch(req,env) {
  if(req.method!=='POST') return json({error:'POST required'},405);
  let identity;
  const authorization=req.headers.get('Authorization');
  const guest=req.headers.get('X-Smriti-Guest-ID');
  if (!authorization && /^[a-f0-9-]{36}$/i.test(guest||'')) {
   // Guests supply their own local context. This endpoint never reads Firebase patient documents.
   // Rate limit by Cloudflare's client IP, not the caller-controlled installation identifier.
   const ip=req.headers.get('CF-Connecting-IP');
   if(!ip)return json({error:'Client identity unavailable'},401);
   identity={sub:`guest:${ip}`};
  } else {
  try {
   const token=/^Bearer (.+)$/.exec(req.headers.get('Authorization')||'')?.[1];
   if(!token || token.length>8192) throw Error();
   const {payload}=await jwtVerify(token,keys,{algorithms:['RS256'],issuer:`https://securetoken.google.com/${env.FIREBASE_PROJECT}`,audience:env.FIREBASE_PROJECT});
   if(!payload.sub || payload.sub.length>128 || !payload.auth_time || payload.auth_time>Date.now()/1000 || payload.firebase?.sign_in_provider==='anonymous') throw Error();
   identity=payload;
  } catch {return json({error:'Sign in required'},401);}
  }
  let body;
  try {
   const reader=req.body?.getReader(); if(!reader) throw Error();
   let size=0;const chunks=[];
   while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>96000){await reader.cancel();return json({error:'Too large'},413);}chunks.push(value);}
   const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
   body=requestData(JSON.parse(new TextDecoder().decode(bytes)));
  } catch {return json({error:'Invalid request'},400);}
  const {success}=await env.AI_RATE_LIMIT.limit({key:identity.sub});
  if(!success)return json({error:'Try again shortly'},429);
  try {
   const key=await geminiKey(env.GEMINI_API_KEY);
   const response=await fetch('https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent',{method:'POST',headers:{'x-goog-api-key':key,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(body.generationConfig.responseMimeType ? 25000 : 12000)});
   if(!response.ok)return json({error:'Online AI unavailable',providerStatus:response.status},503);
   const reply=parseReply(await response.json());
   return json({reply,provider:'gemini',model:'gemini-3.1-flash-lite'});
  }catch{return json({error:'Online AI unavailable'},503);}
 }
};

