@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup script (Windows), упрощённая версия.
@REM Скачивает Maven при первом запуске; URL — в .mvn\wrapper\maven-wrapper.properties.
@REM ----------------------------------------------------------------------------
@echo off
setlocal enabledelayedexpansion

set "WRAPPER_DIR=%~dp0.mvn\wrapper"
set "PROPS_FILE=%WRAPPER_DIR%\maven-wrapper.properties"

if not exist "%PROPS_FILE%" (
  echo ERROR: %PROPS_FILE% not found
  exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in ("%PROPS_FILE%") do (
  if /i "%%A"=="distributionUrl" set "DIST_URL=%%B"
)

if "%DIST_URL%"=="" (
  echo ERROR: distributionUrl not set
  exit /b 1
)

for %%F in ("%DIST_URL%") do set "DIST_FILE=%%~nxF"
set "DIST_NAME=%DIST_FILE:-bin.zip=%"
set "DIST_NAME=%DIST_NAME:.zip=%"

if "%MAVEN_USER_HOME%"=="" set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "CACHE_DIR=%MAVEN_USER_HOME%\wrapper\dists\%DIST_NAME%"
set "MAVEN_HOME=%CACHE_DIR%\%DIST_NAME%"

if not exist "%MAVEN_HOME%" (
  echo [mvnw] Maven %DIST_NAME% not found locally, downloading...
  if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
  powershell -NoProfile -Command "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%CACHE_DIR%\%DIST_FILE%'"
  powershell -NoProfile -Command "Expand-Archive -Path '%CACHE_DIR%\%DIST_FILE%' -DestinationPath '%CACHE_DIR%' -Force"
  del /q "%CACHE_DIR%\%DIST_FILE%"
)

if "%JAVA_HOME%"=="" (
  where java >nul 2>nul
  if errorlevel 1 (
    echo ERROR: JAVA_HOME not set and java not on PATH
    exit /b 1
  )
)

call "%MAVEN_HOME%\bin\mvn.cmd" %*
