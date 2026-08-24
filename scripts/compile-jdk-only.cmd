@echo off
setlocal
pushd "%~dp0.."
echo [INFO] JavaFX/WebSocket require Maven dependencies; this legacy command now uses Maven.
call mvn -q test-compile dependency:copy-dependencies -DincludeScope=runtime
set code=%errorlevel%
popd
exit /b %code%
