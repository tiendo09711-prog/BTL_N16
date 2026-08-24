@echo off
setlocal
pushd "%~dp0.."
call scripts\compile-jdk-only.cmd
if errorlevel 1 (
  popd
  exit /b 1
)
start "BTL16 Memory Server" cmd /k java -Dbtl16.server.config=config/server-memory.properties -cp "out\main" vn.ptit.btl16.server.ServerMain
timeout /t 2 /nobreak >nul
start "BTL16 Client demo" java -cp "out\main" vn.ptit.btl16.client.ClientMain
start "BTL16 Client alice" java -cp "out\main" vn.ptit.btl16.client.ClientMain
start "BTL16 Client bob" java -cp "out\main" vn.ptit.btl16.client.ClientMain
popd
