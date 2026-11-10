@echo off
REM Guest Desk - step 2: build and start the Docker deployment (Windows launcher for 02-build-and-start.ps1).
REM Double-click it, or run from a terminal:  02-build-and-start.bat [-TimeoutSeconds 180]
REM Set GUESTDESK_NO_PAUSE=1 to skip the final "press any key".
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp002-build-and-start.ps1" %*
set "RC=%ERRORLEVEL%"
if "%GUESTDESK_NO_PAUSE%"=="1" goto :done
echo.
if "%RC%"=="0" echo Finished successfully.
if not "%RC%"=="0" echo Finished with errors, exit code %RC%. Read the messages above.
pause
:done
endlocal & exit /b %RC%
