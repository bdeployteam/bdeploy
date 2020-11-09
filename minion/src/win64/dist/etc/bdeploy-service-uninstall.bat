@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Uninstall minion master | node service
@rem
@rem ##########################################################################

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.\

@rem Customize this property if you want to run multiple services 
@rem on the same machine. The service prefix must be unique.
@rem The type of the minion (master | node) will be added to this prefix.
set MINION_SERVICE_PREFIX=BDeploy

@rem Check if we have the required permissions
whoami /groups | find "S-1-16-12288" > nul
if ERRORLEVEL 1 goto restricted

@rem Whether the service represents a master or a node node
set MINION_TYPE_ARG=%~1
if "%MINION_TYPE_ARG%"=="" goto usage
if "%MINION_TYPE_ARG%"=="--master" ( 
	set MINION_SERVICE_NAME=%MINION_SERVICE_PREFIX%Master
)
if "%MINION_TYPE_ARG%"=="--node" (
	 set MINION_SERVICE_NAME=%MINION_SERVICE_PREFIX%Node
)
if "%MINION_SERVICE_NAME%"=="" goto usage

@rem Remove the service
%DIRNAME%\nssm.exe remove "%MINION_SERVICE_NAME%" confirm
if ERRORLEVEL 1 goto serviceFailure
goto done

:usage
@echo Failed to remove service. One or more mandatory parameters are missing
@echo.
@echo Usage: bdeploy-service-uninstall.bat ^<--master^|--node^>
exit /B 1

:restricted 
@echo Removing a service requires elevation (Run as administrator).
exit /B 1

:serviceFailure
@echo Failed to remove service.
exit /B 1

:done
exit /B 0
