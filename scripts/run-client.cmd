@echo off
setlocal
pushd "%~dp0.."
call scripts\compile-jdk-only.cmd
if errorlevel 1 (
  popd
  exit /b 1
)
java -cp "out\main" vn.ptit.btl16.client.ClientMain
set code=%errorlevel%
popd
exit /b %code%
