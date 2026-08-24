@echo off
echo IPv4 addresses on this computer:
ipconfig | findstr /R /C:"IPv4 Address" /C:"IPv4 Address. . ."
echo.
echo Put the correct LAN IPv4 into config\client.properties on client computers.
