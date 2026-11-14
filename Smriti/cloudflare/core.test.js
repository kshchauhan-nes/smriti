import {test} from 'node:test';
import assert from 'node:assert/strict';
import {requestData,parseReply,geminiKey} from './core.js';
test('Bihu follow-up keeps history and current request',()=>{
 const result=requestData({message:'folklore',context:{region:'Assam'},history:[{role:'user',content:'tell me abt bihu'},{role:'assistant',content:'Bihu is celebrated in Assam.'}]});
 assert.deepEqual(result.contents.map(x=>x.role),['user','model','user']);
 assert.match(result.contents[0].parts[0].text,/bihu/);
 assert.equal(JSON.parse(result.contents[2].parts[0].text).request,'folklore');
 assert.equal(JSON.parse(result.contents[2].parts[0].text).patientData.verifiedFolkloreExcerpt.title,'Tejimola and the Lotus');
 assert.equal(result.generationConfig.thinkingConfig.thinkingLevel,'minimal');
});
test('Response preserves all text, excludes thoughts, rejects truncation',()=>{
 assert.equal(parseReply({candidates:[{finishReason:'STOP',content:{parts:[{thought:true,text:'private'},{text:'Bihu is '},{text:'an Assamese festival.'}]}}]}),'Bihu is an Assamese festival.');
 for(const finishReason of ['MAX_TOKENS','SAFETY']) assert.throws(()=>parseReply({candidates:[{finishReason,content:{parts:[{text:'Bhu'}]}}]}));
 assert.throws(()=>parseReply({candidates:[]}));
});
test('Secrets Store bindings are read asynchronously',async()=>{
 assert.equal(await geminiKey({get:async()=>'test-value'}),'test-value');
 assert.equal(await geminiKey(' test-value '),'test-value');
 await assert.rejects(geminiKey(undefined));
});
test('Invalid context rejected and game output uses JSON',()=>{
 assert.throws(()=>requestData({message:'x',context:{x:'x'.repeat(18001)}}));
 assert.throws(()=>requestData({message:'x',purpose:'unknown',context:{}}));
 assert.equal(requestData({message:'difficulty',purpose:'difficulty',context:{}}).generationConfig.responseMimeType,'application/json');
});

test('Ordinary chat omits folklore overhead and never claims live search',()=>{
 const r=requestData({message:'What is Bihu?',context:{region:'Assam'}});
 assert.equal(JSON.parse(r.contents.at(-1).parts[0].text).patientData.verifiedFolkloreExcerpt,undefined);
 assert.match(r.systemInstruction.parts[0].text,/No live search was performed/);
 assert.equal(r.tools,undefined);
});
test('Current information acknowledges unavailable free-tier search, private data never enters a search tool',()=>{
 const r=requestData({message:'What is the latest news in Assam?',context:{patient:{name:'Synthetic person'}}});
 assert.match(r.systemInstruction.parts[0].text,/Live search is unavailable/);
 assert.equal(r.tools,undefined);
});
test('History is bounded while preserving recent follow-ups',()=>{
 const history=Array.from({length:12},(_,i)=>({role:i%2?'assistant':'user',content:'x'.repeat(2000)}));
 const r=requestData({message:'Tell me more',context:{},history});
 assert.equal(r.contents.length,7);
 assert.ok(r.contents.slice(0,-1).every(m=>m.parts[0].text.length<=700));
});
