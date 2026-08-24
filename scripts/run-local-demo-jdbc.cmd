@echo off
setlocal
pushd "%~dp0.."
call scripts\setup-db.cmd
if errorlevel 1 (
  popd
  exit /b 1
)
start "BTL16 Server Dashboard" java -cp "target\classes;target\dependency\*" vn.ptit.btl16.server.dashboard.ServerDashboardMain
timeout /t 2 /nobreak >nul
start "BTL16 Client demo" java -cp "target\classes;target\dependency\*" vn.ptit.btl16.client.ClientMain
start "BTL16 Client alice" java -cp "target\classes;target\dependency\*" vn.ptit.btl16.client.ClientMain
start "BTL16 Client bob" java -cp "target\classes;target\dependency\*" vn.ptit.btl16.client.ClientMain
popd
