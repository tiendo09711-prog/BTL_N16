@echo off
echo [INFO] Delegating legacy JDK-only name to dependency-aware self-tests.
call "%~dp0run-self-tests.cmd"
exit /b %errorlevel%
