@echo off
setlocal
pushd "%~dp0.."
call scripts\build.cmd
if errorlevel 1 (
  popd
  exit /b 1
)
java -cp "target\classes;target\dependency\*" vn.ptit.btl16.server.ServerMain
set code=%errorlevel%
popd
exit /b %code%
