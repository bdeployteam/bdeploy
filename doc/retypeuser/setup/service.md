---
order: 2
icon: codespaces
---

# Service

## Linux Service (SYSTEMD)

The `bdeploy-linux64-X.Y.Z.zip` package contains a template file from which a systemd service can be created. It is located in the `etc` directory and named `bdeploy-minion.service`.

The template itself contains instructions on how to create a valid service from it.

You can copy the service template to a different filename while installing it into the systemd service directory. The filename will later on be the service name. This allows installing multiple **BDeploy** services on a single machine.

!!!info Note
To get systemd to recognize the service(s) after copying the service file(s) use the `systemctl daemon-reload` command (as root).
Once installed, you can control the service using `systemctl`.
!!!

## Windows Service

The `bdeploy-win64-X.Y.Z.zip` package contains a batch script to create a new Windows service. The script is located in the `etc` directory and is named `bdeploy-service-install.bat`

The script must be called with administrative privileges and it expects the following arguments:

```
--master | --node      Start a master or node (only controls the name of the service).
<Path-to-minion-bat>   Absolute path to the bdeploy batch file.
                       Sample: C:\BDeploy\server\bin\bdeploy.bat
<Path-to-store-files>  Absolute path to the data directory. Make sure to not include a trailing backslash.
                       Sample: C:\BDeploy\data
```

!!!warning Warning
By default the service runs as _Local System_ but you can and should change that afterwards by using the services.msc application. Simply configure the desired account in the _Log On_ tab of the services management console. We **strongly** recommend to use a non-privileged user to run the **BDeploy** service.
!!!

Once installed, you can start the service using services.msc or by calling `net start BDeployMaster | BDeployNode`.

!!!info Note
Use the _Event Viewer_ (Windows Logs->System) to get more information when the service fails to start.
!!!

!!!info Note
Application logs (stdout, stderror) are written to a log file that is stored in `<path-to-store-files>\log\service.log`
!!!

!!!info Note
The service parameters are stored in the Windows registry: `HKLM\SYSTEM\CurrentControlSet\Services\BDeployMaster`
!!!

!!!info Note
You need to modify the batch script when you want to create multiple services on the same machine. Simply adopt the `MINION_SERVICE_PREFIX` to use another prefix for the service.
!!!
