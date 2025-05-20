@echo off
set SCRIPT_HOME=%~dp0

if exist "%SCRIPT_HOME%_bdeploy-new.bat" (
    move /y "%SCRIPT_HOME%_bdeploy-new.bat" "%SCRIPT_HOME%_bdeploy.bat" >nul
)

"%SCRIPT_HOME%_bdeploy.bat" %*
