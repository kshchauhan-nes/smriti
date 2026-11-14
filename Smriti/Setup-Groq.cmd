@echo off
set "PATH=C:\Users\Devil\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin;%PATH%"
cd /d W:\NER\cloudflare
set "WRANGLER=W:\NER\cloudflare\node_modules\wrangler\bin\wrangler.js"
node "%WRANGLER%" login
if errorlevel 1 goto failed
node "%WRANGLER%" deploy
if errorlevel 1 goto failed
node "%WRANGLER%" secret put GROQ_API_KEY
if errorlevel 1 goto failed
echo Setup complete. Send Codex the workers.dev URL above, never your key.
pause
exit /b 0
:failed
echo Setup failed. Share the error, never your key. Keep the Workers FREE plan.
pause
