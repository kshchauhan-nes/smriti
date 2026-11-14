@echo off
set "PATH=C:\Users\Devil\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin;%PATH%"
cd /d W:\NER\cloudflare
node node_modules\wrangler\bin\wrangler.js secret put GROQ_API_KEY
if errorlevel 1 goto failed
echo Key saved securely. Tell Codex: key saved.
pause
exit /b 0
:failed
echo Key setup failed. Share only the error, never the key.
pause
