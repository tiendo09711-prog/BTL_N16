@echo off
setlocal
pushd "%~dp0.."
call scripts\compile-jdk-only.cmd
if errorlevel 1 (
  popd
  exit /b 1
)
java -Dbtl16.server.config=config/server-memory.properties -cp "out\main" vn.ptit.btl16.server.ServerMain
set code=%errorlevel%
popd
exit /b %code%
