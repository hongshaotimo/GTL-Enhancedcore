@echo off
setlocal EnableExtensions
set "BUILD_ARGS="
:args
if "%~1"=="" goto build
if /I "%~1"=="--no-pause" (
    set "BUILD_ARGS=%BUILD_ARGS% -NoPause"
    goto next
)
if /I "%~1"=="--offline" (
    set "BUILD_ARGS=%BUILD_ARGS% -Offline"
    goto next
)
if /I "%~1"=="--deploy" (
    set "BUILD_ARGS=%BUILD_ARGS% -Deploy"
    goto next
)
if /I "%~1"=="--sync-dependencies" (
    set "BUILD_ARGS=%BUILD_ARGS% -SyncDependencies"
    goto next
)
echo Unknown option: %~1
exit /b 2
:next
shift
goto args
:build
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build.ps1" %BUILD_ARGS%
exit /b %errorlevel%
