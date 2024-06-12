@echo off
set SCRIPT_HOME=%~dp0

if exist "%SCRIPT_HOME%_bdeploy-new.bat" (
    echo "Updating start script..."
    move /y "%SCRIPT_HOME%_bdeploy-new.bat" "%SCRIPT_HOME%_bdeploy.bat"
)

call "%SCRIPT_HOME%_bdeploy.bat" %*
