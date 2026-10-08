@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\set-version.ps1" %*
exit /b %errorlevel%
