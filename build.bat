@echo off
setlocal EnableExtensions
set "BUILD_ARGS="
set "NO_PAUSE="
:args
if "%~1"=="" goto build
if /I "%~1"=="--no-pause" (
    set "NO_PAUSE=1"
    goto next
)
if /I "%~1"=="--offline" (
    set "BUILD_ARGS=--offline"
    goto next
)
echo Unsupported option: %~1
echo Usage: build.bat [--offline] [--no-pause]
exit /b 2
:next
shift
goto args
:build
set "GRADLE=%USERPROFILE%\Desktop\gt_shanhai\gradle-install\gradle-8.8\bin\gradle.bat"
if not exist "%GRADLE%" (
    echo Gradle 8.8 not found: %GRADLE%
    exit /b 1
)
if exist "C:\Program Files\Java\jdk-21.0.11\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11"
pushd "%~dp0" || exit /b 1
if not exist "tools\hidden_structure_bindings.json" (
    echo WARNING: tools\hidden_structure_bindings.json is missing. Skipping the entire regressionTest task.
    set "BUILD_ARGS=%BUILD_ARGS% -x regressionTest"
)
call "%GRADLE%" build --no-daemon %BUILD_ARGS%
set "BUILD_RESULT=%errorlevel%"
popd
if not defined NO_PAUSE pause
exit /b %BUILD_RESULT%
