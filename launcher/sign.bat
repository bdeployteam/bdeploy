@echo off

rem This only works if the code signing certificate is installed on this windows machine.
rem The EV certificate requires a hardware token in addition to the certificate.
rem Currently only Markus Duft's machine has the token and the certificate. It will only work there.

signtool sign /n "IT Solutions GmbH" /tr http://rfc3161timestamp.globalsign.com/advanced /td SHA256 src\win64\dist\BDeploy.exe
signtool sign /n "IT Solutions GmbH" /tr http://rfc3161timestamp.globalsign.com/advanced /td SHA256 src\win64\dist\FileAssoc.exe
