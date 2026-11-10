@echo off
REM Guest Desk - step 1: check and install the dependencies, create .env (Windows launcher for 01-check-dependencies.ps1).
REM Double-click it, or run from a terminal:  01-check-dependencies.bat [-Yes]
REM Set GUESTDESK_NO_PAUSE=1 to skip the final "press any key".
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp001-check-dependencies.ps1" %*
set "RC=%ERRORLEVEL%"
if "%GUESTDESK_NO_PAUSE%"=="1" goto :done
echo.
if "%RC%"=="0" echo Finished successfully.
if not "%RC%"=="0" echo Finished with errors, exit code %RC%. Read the messages above.
pause
:done
endlocal & exit /b %RC%
