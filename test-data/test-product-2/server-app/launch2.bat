@if "%DEBUG%" == "" @echo off

@rem #################################################
@rem # TEST APPLICATION                              #
@rem #################################################
echo "Command line arguments %*"

goto loop

:subprocess
    start /b powershell -command "sleep 60"
    set /a lc=lc-1
    if %lc%==0 goto loop
    goto subprocess

:loop
IF NOT "%1" == "" (

    IF "%1" == "--sleep" (
       powershell -command "sleep %2"
       SHIFT
    )
    IF "%1" == "--ping" (
        echo "Pinging localhost endlessly"
        ping -t localhost
        SHIFT
    )
    IF "%1" == "--text" (
        echo "Got some text: %2"
        SHIFT
    )
    IF "%1" == "--help" (
        echo "This is very helpful"
        exit /B 1
    )
    IF "%1" == "--cfg" (
        echo "Got config file: %2"
        powershell -command "get-content %2"
        SHIFT
    )
    IF "%1" == "--out" (
        echo "Writing to file: %2"
        echo TEST > "%2"
        SHIFT
    )
    IF "%1" == "--subprocesses" (
        echo "Starting %2 Sub-Processes..."
        set lc=%2
        SHIFT
        goto subprocess
    )
    IF "%1" == "--server" (
        echo "Server port: %2"
        SHIFT
    )
    IF "%1" == "--client" (
        echo "Client port: %2"
        SHIFT
    )

    SHIFT
    GOTO :loop

)

exit /B 0
