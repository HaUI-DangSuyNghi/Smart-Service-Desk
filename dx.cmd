@echo off
rem Loi vao ngan: dx start | dx up | dx down ... (xem scripts/dx.ps1)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\dx.ps1" %*
exit /b %ERRORLEVEL%
