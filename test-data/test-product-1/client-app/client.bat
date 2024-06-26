@ECHO OFF

:: Mini demo client application.
:: Current working directory is set by the launcher, so the file is in a "good" place.

ECHO BDeploy Client Application

SET argumentCount=0
FOR %%x IN (%*) DO SET /A argumentCount+=1

IF %argumentCount%==1 (
	notepad %1
) ELSE (
	(
		ECHO BDeploy Launcher is working!
		ECHO This file has been written by the demo client application on your system and opened with notepad.
		IF %argumentCount%==0 (
			ECHO No command line arguments were provided.
		)
		IF "%argumentCount%" GTR "1" (
			ECHO The command line arguments were: %*
		)
	) > demo.txt
	notepad demo.txt
)
