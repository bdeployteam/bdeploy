@echo off

for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"

echo This is a simple chat application. It can print a menu or echo your input...
echo (enter m for menu)

:loop
        set input=
        set /p input="# "

        if "%input%"=="c" (
			for /L %%c in (0,1,7) do (
				echo %ESC%[3%%cm%ESC%[100m   3%%c   %ESC%[97m%ESC%[4%%cm   4%%c   %ESC%[9%%cm%ESC%[40m   9%%c   %ESC%[30m%ESC%[10%%cm  10%%c   %ESC%[0m
			)
        ) else if "%input%"=="m" (
                echo ----------------------------------
                echo Known commands:
                echo     m: print this menu
                echo     c: print a color map
                echo     [other input]: echo your input
                echo     [empty input]: exit
                echo -----------------------------------
        ) else if "%input%"=="" (
                goto :end
        ) else (
                echo you said: "%input%"
        )
goto :loop

:end
echo bye.
