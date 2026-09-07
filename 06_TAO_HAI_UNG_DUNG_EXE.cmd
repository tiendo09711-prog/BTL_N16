@echo off
setlocal
pushd "%~dp0"
call npm run dist
set code=%errorlevel%
popd
pause
exit /b %code%
