.NET Framework versions and dependencies
----------------------------------------

All native Windows applications are currently targeting .NET Framework 4.7.2.
This allows us to support all Windows versions starting from Windows 10 version 1803 as well as Windows Server 2019 without the need to install any additional software.

More details can be found here: https://docs.microsoft.com/en-us/dotnet/framework/migration-guide/versions-and-dependencies

Code Signing
----------------------------------------
Signing requires:
	- SafeNet drivers
	- Hardware token with the certificate
	- The SignTool.exe

SafeNet:
	Download and install the SafeNet drivers:
	https://support.globalsign.com/ssl/ssl-certificates-installation/safenet-drivers#Windows

Certificate:
  The hardware token needs to be connected

SignTool.exe
  The tool ships with the Windows SDK that can be installed with the Visual Studio Installer
  Start the installer and click on Modify. Select Individual Components and search for Windows 10 SDK. Install the latest one
  Add the following directory to the local path C:\Program Files (x86)\Windows Kits\10\bin\<version>\x64
