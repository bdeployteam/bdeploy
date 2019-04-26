@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Install minion master | slave as service on Windows
@rem
@rem ##########################################################################

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.\

@rem Customize this property if you want to run multiple services 
@rem on the same machine. The service prefix must be unique.
@rem The type of the minion (master | slave) will be added to this prefix.
set MINION_SERVICE_PREFIX=BDeploy

@rem Check if we have the required permissions
whoami /groups | find "S-1-16-12288" > nul
if ERRORLEVEL 1 goto restricted

@rem Whether the service represents a master or a slave node
set MINION_TYPE_ARG=%1
if "%MINION_TYPE_ARG%"=="" goto usage
if "%MINION_TYPE_ARG%"=="--master" ( 
	set MINION_TYPE=master
	set MINION_SERVICE_NAME=%MINION_SERVICE_PREFIX%Master
	set MINION_SERVICE_DISPLAY=%MINION_SERVICE_PREFIX% Master
)
if "%MINION_TYPE_ARG%"=="--slave" (
	 set MINION_TYPE=slave
	 set MINION_SERVICE_NAME=%MINION_SERVICE_PREFIX%Slave
	 set MINION_SERVICE_DISPLAY=%MINION_SERVICE_PREFIX% Slave
)
if "%MINION_TYPE%"=="" goto usage

@rem The full qualified path to the minion executable
set MINION_EXE=%~f2
if "%MINION_EXE%"=="" goto usage

@rem The full qualified path to the directory where the minion should store the data files
set MINION_DATA=%~f3
if "%MINION_DATA%"=="" goto usage

@rem Create the actual service
%DIRNAME%\nssm.exe install %MINION_SERVICE_NAME% "%MINION_EXE%" %MINION_TYPE% """--root=%MINION_DATA%""" > nul
if ERRORLEVEL 1 goto serviceFailure

@rem Setup required properties
%DIRNAME%\nssm.exe set %MINION_SERVICE_NAME% DisplayName "%MINION_SERVICE_DISPLAY%" > nul
%DIRNAME%\nssm.exe set %MINION_SERVICE_NAME% Description "Deployment Control Service" > nul
%DIRNAME%\nssm.exe set %MINION_SERVICE_NAME% AppStdout %MINION_DATA%\log\service.log > nul
%DIRNAME%\nssm.exe set %MINION_SERVICE_NAME% AppStderr %MINION_DATA%\log\service.log > nul
echo Service "%MINION_SERVICE_NAME%" successfully created!
goto done

:usage
@echo Failed to create service. One or more mandatory parameters are missing
@echo.
@echo Usage: bdeploy-service-install.bat ^<--master^|--slave^> ^<Path-to-minion-exe^> ^<Path-where-to-store-files^> 
exit /B 1

:restricted 
@echo Creating a service requires elevation (Run as administrator).
exit /B 1

:serviceFailure
@echo Failed to create service.
exit /B 1

:done
exit /B 0
