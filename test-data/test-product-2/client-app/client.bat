@echo off

rem Mini demo client application. opens the system text editor using xdg-open
rem
rem Current working directory is set by the launcher, so the file is in a "good" place.

if "%1"=="" (
  goto empty
)

notepad "%1"
goto exit

:empty
(
    @echo BDeploy Launcher is working!
    @echo This file has been written by the demo client application on your system, and opened with notepad.
) > demo.txt

notepad demo.txt

:exit

