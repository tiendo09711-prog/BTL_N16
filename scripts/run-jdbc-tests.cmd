@echo off
setlocal
pushd "%~dp0.."
call mvn -q test-compile dependency:copy-dependencies -DincludeScope=runtime
if errorlevel 1 (
  popd
  exit /b 1
)
java -cp "target\classes;target\test-classes;target\dependency\*" vn.ptit.btl16.selftest.XamppDatabaseSelfTest
set code=%errorlevel%
popd
exit /b %code%
