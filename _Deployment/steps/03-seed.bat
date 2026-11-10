@echo off
REM Guest Desk - step 3: seed the platform administrator and the demo data (Windows launcher for 03-seed.ps1).
REM Double-click it, or run from a terminal:  03-seed.bat [-AdminOnly]
REM Set GUESTDESK_NO_PAUSE=1 to skip the final "press any key".
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp003-seed.ps1" %*
set "RC=%ERRORLEVEL%"
if "%GUESTDESK_NO_PAUSE%"=="1" goto :done
echo.
if "%RC%"=="0" echo Finished successfully.
if not "%RC%"=="0" echo Finished with errors, exit code %RC%. Read the messages above.
pause
:done
endlocal & exit /b %RC%
