@echo off
setlocal
pushd "%~dp0.."
call scripts\compile-jdk-only.cmd
if errorlevel 1 (
  popd
  exit /b 1
)
java -cp "out\main;out\test" vn.ptit.btl16.selftest.AllSelfTests
set code=%errorlevel%
popd
exit /b %code%
