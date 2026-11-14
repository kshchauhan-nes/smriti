"use strict";
const {onRequest} = require('firebase-functions/v2/https');
const {defineSecret} = require('firebase-functions/params');
const {initializeApp} = require('firebase-admin/app');
const {getAuth} = require('firebase-admin/auth');
const {getFirestore} = require('firebase-admin/firestore');
const {requestData} = require('./core');
initializeApp();
const groqKey = defineSecret('GROQ_API_KEY');
exports.smritiAi = onRequest({region:'asia-south1',secrets:[groqKey],timeoutSeconds:45,memory:'256MiB',maxInstances:2,invoker:'public'}, async (req,res) => {
  res.set('Cache-Control','no-store');
  if (req.method !== 'POST') return res.status(405).json({error:'POST required'});
  if (Number(req.headers['content-length'] || 0) > 32000) return res.status(413).json({error:'Request too large'});
  let identity;
  try {
    const token = /^Bearer (.+)$/.exec(req.headers.authorization || '')?.[1];
    if (!token) throw Error('No token');
    identity = await getAuth().verifyIdToken(token, true);
  } catch (_) {return res.status(401).json({error:'Sign in required'});}
  if (identity.firebase?.sign_in_provider === 'anonymous') return res.status(403).json({error:'Account login required'});
  let payload;
  try {payload=requestData(req.body);} catch (_) {return res.status(400).json({error:'Invalid request'});}
  try {
    // Server-only collection: clients cannot reset their quota using patient write permissions.
    const quota = getFirestore().collection('aiQuotas').doc(identity.uid);
    const now = Date.now(), day = Math.floor(now / 86400000), minute = Math.floor(now / 60000);
    await getFirestore().runTransaction(async tx => {
      const old = (await tx.get(quota)).data() || {};
      const daily = old.day === day ? old.daily || 0 : 0;
      const burst = old.minute === minute ? old.burst || 0 : 0;
      if (daily >= 100 || burst >= 6) throw Error('QUOTA');
      tx.set(quota,{day,minute,daily:daily+1,burst:burst+1});
    });
    const response = await fetch('https://api.groq.com/openai/v1/chat/completions', {
      method:'POST',headers:{Authorization:`Bearer ${groqKey.value()}`,'Content-Type':'application/json'},
      body:JSON.stringify(payload),signal:AbortSignal.timeout(30000)
    });
    if (!response.ok) return res.status(503).json({error:'Online AI unavailable'});
    const data = await response.json();
    const answer = data.choices?.[0]?.message?.content;
    if (typeof answer !== 'string' || !answer.trim()) throw Error('Empty response');
    return res.json({reply:answer.slice(0,6000)});
  } catch (error) {
    return res.status(error.message === 'QUOTA' ? 429 : 503).json({error:'Online AI unavailable'});
  }
});
