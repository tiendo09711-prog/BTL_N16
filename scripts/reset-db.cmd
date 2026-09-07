@echo off
setlocal
pushd "%~dp0.."
echo WARNING: This deletes the database selected by config/server.properties and recreates an EMPTY schema.
set /p answer=Type RESET to continue: 
if /I not "%answer%"=="RESET" (
  echo Cancelled.
  popd
  exit /b 0
)
call scripts\build.cmd
if errorlevel 1 (
  popd
  exit /b 1
)
java -cp "target\classes;target\dependency\*" vn.ptit.btl16.server.db.DatabaseResetMain
set code=%errorlevel%
popd
exit /b %code%
