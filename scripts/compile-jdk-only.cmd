@echo off
setlocal enabledelayedexpansion
pushd "%~dp0.."
if exist out rmdir /s /q out
mkdir out\main
mkdir out\test
for /r src\main\java %%f in (*.java) do echo %%f>>out\main-sources.txt
javac --release 17 -encoding UTF-8 -d out\main @out\main-sources.txt
if errorlevel 1 (
  popd
  exit /b 1
)
for /r src\test\java %%f in (*.java) do echo %%f>>out\test-sources.txt
javac --release 17 -encoding UTF-8 -cp out\main -d out\test @out\test-sources.txt
if errorlevel 1 (
  popd
  exit /b 1
)
echo [OK] JDK-only compilation completed.
popd
