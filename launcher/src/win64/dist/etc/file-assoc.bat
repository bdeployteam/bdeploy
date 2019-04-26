@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Install BDeploy launcher file association
@rem
@rem ##########################################################################

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.\

@rem Check if we have the required permissions
whoami /groups | find "S-1-16-12288" > nul
if ERRORLEVEL 1 goto restricted

ftype BDeployLauncher=%DIRNAME%..\bin\launcher.bat %%1
assoc .bdeploy=BDeployLauncher
