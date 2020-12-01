.NET Framework versions and dependencies
----------------------------------------

All native Windows applications are currently targeting .NET Framework 4.5
This allows us to support Windows 8, Windows 10 as well as Windows Server 2012, 2016, 2019 without 
the need to install any additional software.

https://docs.microsoft.com/en-us/dotnet/framework/migration-guide/versions-and-dependencies

Code Signing
----------------------------------------
Executables are signed for development purpose with a self-signed certificate. 
Signing requires:
	- A self signed certificate
	- The SignTool.exe 

Certificate:
  A new certificate can be created with the following command:
  New-SelfSignedCertificate -Subject "BDeploy Development" -Type CodeSigningCert -CertStoreLocation Cert:\CurrentUser\My

SignTool.exe 
  The tool ships with the Windows SDK that can be installed with the Visual Studio Installer.
  Start the installer and click on Modify. Select Individual Compnents and search for Windows 10 SDK. Install the latest one
  Add the following directory to the local path C:\Program Files (x86)\Windows Kits\10\bin\<version>\x64
