---
order: 2
icon: apps
---

# Client Applications

A dedicated overview page shows all configured **Client Applications** of an entire **Instance Group**. This page can be opened by navigating to the desired **Instance Group**. Clicking on the [ **Client Applications** ] button in the main navigation menu opens the overview page.

:::{align=center}
![Client Applications](/images/Doc_ClientApps.png){width=480}
:::

The page will offer the appropriate **Client Application** based on the operating system that the user is currently using. Switching the OS can be done by updating the default grouping of the **Client Applications** page.

## Launcher

**Client Application** are started through a special **Launcher** that needs to be installed on all client computers. The launcher is responsible for keeping the application up-to-date and to pass the configured parameters to the application.

## Installer

An **Installer** is provided for each **Client Application** to make the installation as easy as possible. The installer does not have any prerequisites and can be downloaded and executed by any user.

The **Installer** checks if the latest version of the **Launcher** is already installed. If not then the **Launcher** is downloaded and stored on the client computer. The user can then start a **Client Application** with the branded **Shortcut** that is stored on the **Desktop** or in the **Start Menu**.

### Windows

The **Launcher** is installed for the **Current User** in the _%LOCALAPPDATA%\BDeploy_ folder (_C:/Users/<user>/AppData/Local/BDeploy_). The location can be changed by setting the **BDEPLOY_HOME** environment variable before launching the installer. No administrative privileges are required for this installation type.

#### Unattended Mode

The **Installer** can also be started in an unattended mode with the **/Unattended** flag so that no UI is shown.

```
./Installer.exe /Unattended
```

When automating the installation it might be required to wait until the actual installation is finished. To do that using the **PowerShell** the _Start-Process_ cmdlet can be used:

```
Start -FilePath "./Installer.exe" -ArgumentList "/Unattended" -Wait
```

#### For All Users

The **Launcher** can also be installed for **all** users by starting the installer with the **/ForAllUsers** flag. The default location in that case is _%ProgramFiles%/BDeploy_ (_C:/Program Files/BDeploy_). The location can be changed by setting the **BDEPLOY_HOME** environment variable before launching the installer. Ensure that all users have access to the customized location. Otherwise they cannot launch the application.

Installation for **All** users requires a terminal with elevated privileges (_Run as Administrator_). Just running the command as local administrator is not sufficient.

```
Start -FilePath "./Installer.exe" -ArgumentList "/ForAllUsers" -Verb RunAs
Start -FilePath "./Installer.exe" -ArgumentList "/Unattended","/ForAllUsers" -Wait -Verb RunAs
```

!!!info Note
The _Start_ cmdlet with the _-Verb RunAs_ switch triggers a UAC prompt so that the installer is running with elevated privileges.
!!!

!!!info Note
Additional configuration might be required depending on the installation location. See [Multi-User Installations](/user/clientapps/#multi-user-installations) for more information.
!!!

#### Authenticode Signature

All Windows binaries of **BDeploy** are signed using a _SSI Schaefer IT Solutions GmbH_ code signing certificate. The installers are built and signed at release time. However, due to the very dynamic nature of applications in **BDeploy**, the installer needs to be branded _after_ being signed during the build (to make it know which server to contact, which application to install, etc.).

**BDeploy** uses a technique commonly called _Signature Stuffing_. This technique is widely used in various installation programs, as many have the same requirements. Unfortunately, this technique can also be abused by malicious code and is thus regarded as potential security risk, see [the official Microsoft report](https://msrc.microsoft.com/update-guide/en-US/vulnerability/CVE-2013-3900). Microsoft does _not_ enable the mitigation for this vulnerability by default, since this breaks signature validation on _many_ installation programs.

The effect of enabling the recommended settings as per the report above will have the effect, that all branded **BDeploy** binaries will be regarded _unsigned_. Those binaries will be shown (in properties dialogs and all other means of verification, e.g. `signtool verify`) as _unsigned_. No hint will be displayed, that the binary is, in fact, signed but does not meet the stricter security policy in place.

## Linux

The **Installer** stores the **Launcher** and all **Client Applications** in _$HOME/.bdeploy_. This location can be changed by setting the environment variable **BDEPLOY_HOME**.

!!!info Note
The installer uses the ImageMagick suite to read and convert icons for the applications. If this is undesired or not possible, settings the `SKIP_ICON` environment variable to any value will skip this step. The installed application(s) will have no icon associated with them.
!!!

## Click & Start

**Click & Start** enables the launching of **Client Application** by simply clicking a link in the web browser. In this case the applications are not integrated into the operating system (Start Menu, Desktop Shortcut). A prerequisite is that the **Click & Start Launcher** is deployed on the client computer. This can be done by using the **Click & Start Installer** available in the top-right corner of the **Client Applications** page. The installer will download the **Click & Start Launcher** and associate it with **.bdeploy** files.

!!!info Note
The **Launcher** can also be manually deployed using the provided ZIP file. After downloading and unpacking the **Launcher** must be associated with **.bdeploy** files using the `FileAssoc.exe` on Windows or `file-assoc.sh` on Linux. Those commands are part of the launcher distributable.
!!!

Clicking on a _Click & Start_ link in the **Browser** will download a **.bdeploy** file that can be directly executed. A more elegant way is to configure the **Browser** to directly open the **.bdeploy** file type. Firefox as well as Chrome allows to configure this after the file has been downloaded.

## Application Browser

The **Launcher** (_BDeploy.exe_) can also be launched directly without any arguments. In this case a dialog is shown that lists all locally installed applications.

:::{align=center}
![Client Application Browser](/images/ManualDoc_ClientBrowserApp.png){width=480}
:::

The **Customize & Launch** context menu entry allows the user to to modify the command line argumements that is used to start the application. A new dialog is opened that lists the existing command line arguments as currently defined by the **active instance version**.

Additional arguments can be added or existing ones can be modified or deleted as desired. This option is especially useful for testing. Arguments can be modified locally without the need to change the global [Instance Configuration](/user/instance/#instance-configuration). The modified arguments are not saved and they need to be re-done the next time the application is launched.

## Additional Command Line Arguments

When launching an application using a **.bdeploy** file then additional command line arguments can be defined which are passed to the target application.

```
./myApp.bdeploy -- "arg1=value1" "arg2=value2"
```

All arguments after the **--** switch are passed to the launched application. They are added _AFTER_ all existing arguments that are currently defined in the **active instance version**. Individual arguments need to be separated using a single space. Quotation marks are required when the argument or its value contains spaces.

!!!info Note
A shortcut can be saved that includes the customized parameters. Doing that it is possible to save the customized arguments so that they do not need to be entered all the time. Executing the shortcut then launches the application with the customized arguments.
!!!

## Multi-User Installations

Larger organizations typically do not want to deploy client applications on each client computer manually. They prefer to install the client software on a central server and publish the software via Citrix or similar technologies so that all users have access to one shared installation. This has the advantage that the administrators are in control of the installation and can centralize the update handling. **BDeploy** supports such an installation szenario.

There are two different locations that are important in the context of deploying applications in a multi-user setup:

- **Installation Area** - The location where the launcher as well as all applications are installed. This location is typically protected by file system privileges so that an **Administrator** has full permissions and all other users have read permissions. This location is defined by the environment variable **BDEPLOY_HOME**.

- **User Area** - The per-user are where the launcher as well as each launched application is permitted to write files. This location is mandatory in case that the **Installation Area** is read-only. This location is defined by the environment variable **BDEPLOY_USER_AREA**.

### Configuration

When the **Installation Area** is read-only the environment variable **BDEPLOY_USER_AREA** must be set for each user and must point to a directory that is writable for the user that wants to launch an application.

### Installing new software

New software is typically installed by the **Administrator** that has full permissions in the **Installation Area**. The administrator is either using the provided **Installer** or a **Click & Start** file in order to install the new application. After that step the application is available and can be launched by each new user by using the **CLick & Start** file.

### Updating software

Whenever a new **Product version** is **Activated** on the server the administrator **needs** to launch the application **once** to deploy the new update. Not doing that will lead to an error that each user receives when trying to launch the application. The **Launcher** always checks for updates and tries to install them. Using an outdated application is not permitted and thus users will not be able to launch the application any more.

:::{align=center}
![Required Software Update](/images/ManualDoc_ClientLauncherUpdateRequired.png){width=480}
:::

!!!info Note
Configuration changes in a client application - like adding, removing or changing a parameter - **do not** require **Administrator** attention since the installation itself is not affected. The change is automatically applied on the next start of the application.
!!!

!!!warning Caution
Changing the product version or changing the launcher version on the server require a manual interaction of the **Administrator** otherwise **NO** user can use the client application anymore.
!!!

### Application Could Not be Launched

Whenever the client application cannot be launched a generic error message is shown to the user. The root cause can be viewed when clicking on the **View Details** button. The error message is also logged to the **Application Log** file. The log file is located next to the installation directory in a folder called **logs**.

The table below shows some the most common error messages and how to solve them.

=== **Error Message**: `jakarta.ws.rs.ProcessingException: java.net.ConnectException: Connection refused`
_Description:_ The BDeploy server is either not running or the required port is not reachable.

_Solution:_ Start the BDeploy server and check that the port is not blocked by a firewall.
=== **Error Message**: `Inconsistent file and folder permissions: Missing permission to delete files.`
_Description:_ Permissions of the root directory are invalid. Typically happens when just he `Modify` permission is revoked.

_Solution:_ Make sure to also revoke the `Write` permissions to make the installation read-only or grant `Modify` permission.
=== **Error Message**: `Inconsistent file and folder permissions: Missing permission to create files.`
_Description:_ Permissions of the root directory are invalid. Typically happens when the _'Advanced Security'_ options are used.

_Solution:_ Make sure that the permissions are set to `Modify` or to `Read, Execute`.
=== **Error Message**: `Inconsistent file and folder permissions. Missing permission to modify files.`
_Description:_ Permissions of the root directory are invalid. Typically happens when the _'Advanced Security'_ options are used.

_Solution:_ Make sure that the permissions are set to `Modify` or to `Read, Execute`.
=== **Error Message**: `BDeploy home directory has changed. Expected X. Got Y.`
_Description:_ Launcher detected that the installation directory has been moved or that it is accessed via a different path.

_Solution:_ When moving was done intentially update the path in the `bdeploy.home` file.

When accessing the launcher via a file share then make sure that the path is exactly the same.

- \\\my-server\launcher
- \\\my-server.my-domain\launcher
- \\\192.168.2.100\launcher

```
The above network paths are treated as a different even if they actually resolve to the same server.
When starting the launcher make sure to always use the same path.
```

===
