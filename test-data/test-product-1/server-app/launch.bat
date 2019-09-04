@if "%DEBUG%" == "" @echo off

@rem #################################################
@rem # TEST APPLICATION                              #
@rem #################################################

:loop
IF NOT "%1" == "" (

    IF "%1" == "--sleep" (
       powershell -command "sleep %2"
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
        type %2
        SHIFT
    )

    SHIFT
    GOTO :loop

)

exit /B 0
