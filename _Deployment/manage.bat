@echo off
REM ===========================================================================
REM Guest Desk - Docker Compose helper for Windows
REM ===========================================================================
REM For the first start use start-all.bat, which also creates docker\.env and seeds the data.
REM
REM Usage:
REM   manage.bat up      - Builds and starts all services in the background.
REM   manage.bat down    - Stops and removes the containers; the data volume is kept.
REM   manage.bat status  - Shows the status of the containers.
REM   manage.bat logs    - Follows the logs of all services, Ctrl+C to exit.
REM   manage.bat reset   - Stops the stack and DELETES the database volume.
REM ===========================================================================

setlocal
pushd "%~dp0docker"
set "COMMAND=%~1"
set "RC=0"

if /i "%COMMAND%"=="up" goto :up
if /i "%COMMAND%"=="down" goto :down
if /i "%COMMAND%"=="status" goto :status
if /i "%COMMAND%"=="logs" goto :logs
if /i "%COMMAND%"=="reset" goto :reset
goto :usage

:up
echo [INFO] Building and starting all services...
docker compose up --build -d
set "RC=%ERRORLEVEL%"
goto :cleanup

:down
echo [INFO] Stopping all services, the database volume is kept...
docker compose down
set "RC=%ERRORLEVEL%"
goto :cleanup

:status
echo [INFO] Service status:
docker compose ps -a
set "RC=%ERRORLEVEL%"
goto :cleanup

:logs
echo [INFO] Showing logs, press Ctrl+C to exit...
docker compose logs -f
set "RC=%ERRORLEVEL%"
goto :cleanup

:reset
echo [WARN] This stops the stack and DELETES the database volume with all data.
set /p "CONFIRM=Type YES to continue: "
if not "%CONFIRM%"=="YES" (
    echo [INFO] Cancelled.
    goto :cleanup
)
docker compose down -v
set "RC=%ERRORLEVEL%"
goto :cleanup

:usage
echo [ERROR] Invalid command: "%COMMAND%"
echo.
echo Usage: manage.bat [up^|down^|status^|logs^|reset]
echo   up     : Build and start all services, detached
echo   down   : Stop and remove the containers, the data volume is kept
echo   status : Show the status of the containers
echo   logs   : View real-time logs of all services
echo   reset  : Stop and DELETE the database volume
set "RC=1"

:cleanup
popd
endlocal & exit /b %RC%
