@echo off
title Currency Converter
color 0A
echo.
echo  ==============================
echo    Currency Converter Starting
echo  ==============================
echo.

cd /d "%~dp0"

where mvn >nul 2>&1
if %errorlevel% neq 0 (
    echo  [ERROR] Maven not found. Make sure Maven is installed and added to PATH.
    echo.
    pause
    exit /b 1
)

echo  [INFO] Building and starting the app...
echo  [INFO] Open your browser at http://localhost:8080
echo.

mvn spring-boot:run

echo.
echo  [INFO] App stopped.
pause
