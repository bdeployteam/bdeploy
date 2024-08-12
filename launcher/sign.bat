@echo off

rem This script will sign all BDeploy executables.
rem This only works if the code signing tools are installed on this windows machine.
rem The EV certificate requires a hardware token in addition to the certificate.

rem Prerequisites:
rem Connect the hardware token
rem Download Windows 10 SDK:  https://developer.microsoft.com/en-US/windows/downloads/windows-10-sdk/
rem Download SafeNet Drivers: https://support.globalsign.com/ssl/ssl-certificates-installation/safenet-drivers#Windows
rem Add the following directory to the local path C:\Program Files (x86)\Windows Kits\10\bin\<version>\x64

signtool sign /n "IT Solutions GmbH" /fd SHA256 /tr http://rfc3161timestamp.globalsign.com/advanced /td SHA256 src\win64\dist\BDeploy.exe
signtool sign /n "IT Solutions GmbH" /fd SHA256 /tr http://rfc3161timestamp.globalsign.com/advanced /td SHA256 src\win64\dist\FileAssoc.exe
signtool sign /n "IT Solutions GmbH" /fd SHA256 /tr http://rfc3161timestamp.globalsign.com/advanced /td SHA256 src\win64\dist\bin\Installer.bin
