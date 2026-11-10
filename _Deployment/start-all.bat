@echo off
REM Guest Desk - one-click deployment: dependencies, build and start, seed data (Windows launcher for steps\start-all.ps1).
REM Double-click it, or run from a terminal:  start-all.bat [-Yes] [-SkipSeed]
REM Set GUESTDESK_NO_PAUSE=1 to skip the final "press any key".
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0steps\start-all.ps1" %*
set "RC=%ERRORLEVEL%"
if "%GUESTDESK_NO_PAUSE%"=="1" goto :done
echo.
if "%RC%"=="0" echo Finished successfully.
if not "%RC%"=="0" echo Finished with errors, exit code %RC%. Read the messages above.
pause
:done
endlocal & exit /b %RC%
