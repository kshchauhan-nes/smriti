# Gemini backend on Cloudflare Workers

Firebase stays on Spark. The deployed endpoint is `https://smriti-ai.solarsteak972.workers.dev`. Gemini credentials are supplied by the `GEMINI_API_KEY` Cloudflare Secrets Store binding in `wrangler.jsonc`; no API key belongs in the Android app. No Firebase Secret Manager or Functions deployment is needed. `Setup-Groq.cmd` is a legacy Groq launcher and does not configure this Gemini integration.

Run `node --test core.test.js` and `npx wrangler deploy` from this directory after authenticating with Cloudflare. Preserve the Secrets Store binding when deploying. Android accepts `-PsmritiAiEndpoint=https://smriti-ai.YOUR-SUBDOMAIN.workers.dev` for an alternate endpoint.

Signed-in Firebase ID tokens are verified using Google's rotating public keys, issuer, audience, signature and expiry. Revocation is not checked until token expiry. Local-mode guests send `X-Smriti-Guest-ID` and are throttled by Cloudflare's client IP. The endpoint uses client-supplied current-patient snapshots, performs no database reads/writes and does not log prompts. Throttling is six requests/minute per UID or guest IP per Cloudflare location, not a global daily quota. Gemini account quotas still apply.

The Worker calls Gemini 3.1 Flash-Lite. Its regional folklore context comes from `folklore.json`, mirrored from the Android offline catalog. Online failures fall back to offline mode with a visible notice. Workers and Gemini free-tier quotas are separate; this integration does not require upgrading Firebase.
