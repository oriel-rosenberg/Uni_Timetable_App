@echo off
rem Builds with the portable toolchain. Usage: build.cmd [gradle tasks]   (default: assembleRelease)
setlocal
if "%TIMETABLE_TOOLCHAIN%"=="" set "TIMETABLE_TOOLCHAIN=C:\Dev\android-toolchain"
set "JAVA_HOME=%TIMETABLE_TOOLCHAIN%\jdk"
set "ANDROID_HOME=%TIMETABLE_TOOLCHAIN%\sdk"
set "ANDROID_USER_HOME=%TIMETABLE_TOOLCHAIN%\android-user"
set "GRADLE_USER_HOME=%TIMETABLE_TOOLCHAIN%\gradle-home"
set "BUILD_ROOT=%TIMETABLE_TOOLCHAIN%\build"

set "TASKS=%*"
if "%TASKS%"=="" set "TASKS=assembleRelease"

call "%~dp0gradlew.bat" -p "%~dp0." "-Ptimetable.buildRoot=%BUILD_ROOT%" %TASKS%
if errorlevel 1 exit /b 1

set "APK=%BUILD_ROOT%\app\outputs\apk\release\app-release.apk"
if exist "%APK%" copy /y "%APK%" "%~dp0timetable.apk" >nul
endlocal
