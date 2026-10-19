@echo off
setlocal
pushd "%~dp0.."
where mvn >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Maven was not found.
  popd
  exit /b 1
)
call mvn -q test-compile dependency:copy-dependencies -DincludeScope=test
if errorlevel 1 (
  popd
  exit /b 1
)
java -Dbtl16.test.allowEmbedded=true -cp "target\classes;target\test-classes;target\dependency\*" vn.ptit.btl16.selftest.AllSelfTests
set code=%errorlevel%
popd
exit /b %code%
