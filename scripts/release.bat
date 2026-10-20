@echo off
rem SandTimer release wrapper - double-click friendly entry for release.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0release.ps1" %*
pause
