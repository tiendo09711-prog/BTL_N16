@echo off
setlocal
pushd "%~dp0.."
where mvn >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Maven was not found in PATH.
  echo Open the Maven project in NetBeans/VSCode or install Maven.
  popd
  exit /b 1
)
call mvn -q -DskipTests package dependency:copy-dependencies -DincludeScope=runtime
if errorlevel 1 (
  popd
  exit /b 1
)
echo [OK] Build completed.
popd
