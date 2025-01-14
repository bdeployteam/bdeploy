---
order: 1
icon: command-palette
---

# How can I setup systems from the CLI

## Preconditions

* You must know the common [Terms](../intro/terms.md) of **BDeploy**.
  * Having read the documentation about [Instances](../user/instance.md) is also adviceable.
* You must be on a machine which can reach the central, managed and node server simultaneously. It is also assumed that the central server is already set up and has a **Software Repository** which contains the `Product` that you want to deploy.

## Creating and starting the Servers

1.  Download **BDeploy** from [GitHub](https://github.com/bdeployteam/bdeploy).
    1. Navigate to the [Version](https://github.com/bdeployteam/bdeploy/releases) that you want.
    2. Find the zip files called `bdeploy-<OPERATING_SYSTEM>-<BDEPLOY_VERSION_NUMBER>.zip` and download the one that is appropriate for your operating system.
2.  Unzip the zip file.
3.  Open the unzipped folder and navigate to the subfolder `bin`. It contains a file called `bdeploy.bat` on Windows, and `bdeploy` on Linux. Execute this file via a CLI as shown below to initialize the servers:

    - managed

      ```
      bdeploy init --root=<PATH_TO_ROOT> --hostname=<URL> --port=<PORT> --mode=managed --initUser=admin --initPassword=<ADMIN_PASSWORD>
      ```

    - node

      ```
      bdeploy init --root=<PATH_TO_ROOT> --hostname=<URL> --port=<PORT> --mode=node --nodeIdentFile=<PATH_TO_NODE_TOKEN>
      ```

Where `<PATH_TO_ROOT>` is the path to the directory where the **BDeploy** server will be installed into, `<URL>` and `<PORT>` comprise the URL of the server via which the other servers will be able to contact it in the network and `<ADMIN_PASSWORD>` is the password for the initial admin account. The `PATH_TO_NODE_TOKEN` is a path where the node ident file will be created. This file contains a token that will be required to connect the node to another server.

4.  Each server can be started with the command shown below, where `PATH_TO_ROOT` is the path to the home directory of the respective minion.

    ```
    bdeploy start --root=<PATH_TO_ROOT>
    ```

    Alternatively you may configure a service on your operating system as described [here](../setup/service.md).

5.  Store local logins for each server for future convenience. Note that this will ask you for your username and password.

    - central

      ```
      bdeploy login --add=central --remote=https://<URI_OF_CENTRAL_SERVER>/api
      ```

    - managed

      ```
      bdeploy login --add=managed --remote=https://<URI_OF_MANAGED_SERVER>/api
      ```

    You can use

    ```
    bdeploy login --list
    ```

    to look at all currently stored connections. This will also show you the currently active connection. In case the currently active connection is not `central`, execute the following command to change that.

    ```
    bdeploy login --use=central
    ```

    Note that the login information is by default stored in the current users HOME (~/.bdeploy_login). This can be changed by setting the environment variable BDEPLOY_LOGIN_STORAGE to a different directory.

## Creating an Instance Group

We must create an instance group on the central server in order to be able to connect the managed server to it. This is achieveable with the following command:

```
bdeploy remote-group --create=<GROUP_NAME>
```

If you lack the permission to create an instance group, find an administrator of that server that can create the group and/or assign permissions to you.

## Connecting the Servers

### Connecting the node to the managed server

In order to connect the node to the managed server you need to locate the file that was created during init of the node (`--nodeIdentFile` argument of init). Execute this command on the node server:

```
bdeploy remote-node --useLogin=managed --add=<NODE_NAME> --nodeIdentFile=<PATH_TO_NODE_TOKEN>
```

You can use this command to look at all currently connected nodes of the managed server:

```
bdeploy remote-node --useLogin=managed --list
```

### Connecting the managed to the central server

1. Create a `managed-ident.txt`:

   ```
   bdeploy remote-central --useLogin=managed --managedIdent --output=<PATH_TO_MANAGED_TOKEN>
   ```

2. Upload the `managed-ident.txt`:

   ```
   bdeploy remote-central --instanceGroup=<GROUP_NAME> --attach=<PATH_TO_MANAGED_TOKEN> --description=<DESCRIPTION> --uri=https://<URI_OF_MANAGED_SERVER>/api
   ```

   Note: Make sure that `URI_OF_MANAGED_SERVER` is the URI as it is reachable from the central server. This might be different from how the managed server is reachable locally, e.g. due to NAT/VPN.

## Configuration and Installation of Instances

There are two ways to continue from here: Either with a: [System Template](#system-template) or an [Instance Template](#instance-template). The former approach will create a whole system all at once, while the latter allows you to build up a system instance by instance.

Either way, you will need the name of the managed server. It can be retrieved with this command:

```
bdeploy remote-central --list --instanceGroup=<GROUP_NAME>
```

### System Template

See [system-template.yaml](../power/product#system-templateyaml) for an example system template.

In order to use a system template via the CLI all of its `templateVariables` must have a `defaultValue`. In the example system template that is linked above, the template variables are marked with `<2>`. Furthermore, all template variables of the linked instance and application templates must be given a fixed value via `fixedVariables`. In the example system template that is linked above, the fixed variables of one instance are marked with `<4>`.

Now we upload the system template, creating a new system in the process.

```
bdeploy remote-system --create --name=<SYSTEM_NAME> --createFrom=<PATH_TO_SYSTEM_TEMPLATE> --instanceGroup=<GROUP_NAME> --server=<MANAGED_SERVER_NAME> --purpose=<PRODUCTIVE|TEST|DEVELOPMENT>
```

### Instance Template

See [instance-template.yaml](../power/product#instance-templateyaml) for an example instance template.

In order to use an instance template via the CLI you must have a so-called "response file" which contains all values required to apply the instance template without the need to query things from the user, as the CLI cannot do that. The response file has the exact same syntax as the [`instances` section of a system template](/power/product/#supported-instance-attributes).

This is an example of such a response file:

```yaml response-file.yaml
name: "Demo Instance"
description: "The Test System's Demo Instance"
productId: "io.bdeploy/demo"
productVersionRegex: "2\\..*"
templateName: "Default Configuration"
fixedVariables:
  - id: "text-param"
    value: "XX"
  - id: "sleep-timeout"
    value: 10
defaultMappings:
  - group: "Server Apps"
    node: "master"
  - group: "Client Apps"
    node: "Client Applications"
```

Note: The node of all client applications must be _exactly_ "Client Applications".

First we want to create an empty system. The following command can be used for this.<br>
Note that the return value of this operation will contain the `SYSTEM_ID` (which is not the same thing as the `SYSTEM_NAME`), which will be required in the next step.

```
bdeploy remote-system --instanceGroup=<GROUP_NAME> --create --name=<SYSTEM_NAME> --server=<MANAGED_SERVER_NAME>
```

Now we have to setup the system variable mentioned in the above response file. Depending on the instance templates and response files you are using you might have to set up way more than one variable - or none at all.<br>
E.g. the `PARAMETER_ID` of the parameter that would have to be set in the example above is "text-param".

```
bdeploy remote-system --instanceGroup=<GROUP_NAME> --update --uuid=<SYSTEM_ID> --setVariable=<PARAMETER_ID> --setValue=<VALUE>
```

Next, we apply the individual instance templates, each with its response files.

```
bdeploy remote-instance --create --name=<INSTANCE_NAME> --template=<PATH_TO_RESPONSE_FILE> --instanceGroup=<GROUP_NAME> --purpose=<PRODUCTIVE|TEST|DEVELOPMENT> --system=<SYSTEM_ID> --server=<MANAGED_SERVER_NAME>
```

## Install, Activate, Start

> Check out the page on [Deployment](../user/deployment.md) for details on this topic.

Figure out the ID of the system you just created in case you don't have it at hand anymore:

```
bdeploy remote-system --list --instanceGroup=<GROUP_NAME>
```

Figure out all instances in the instance group belonging to that system. In the result the ID of the system should be contained in the system column to identify instances of the system.

```
bdeploy remote-instance --list --instanceGroup=<GROUP_NAME>
```
Note for the above command: If you are executing this command on the central server you can add --server=<NAME_OF_MANAGED_SERVER> to limit the output to this the given managed server.

For each of the instances, install & activate them. The `--uuid` receives the value from the "ID" column of the previous command. `--version` receives the instance version you want to install, which is most likely the highest version number ("Ver." column in output of previous command) for this instance.

```
bdeploy remote-deployment --instanceGroup=<GROUP_NAME> --install --uuid=<INSTANCE_ID> --version=<INSTANCE_VERSION_NUMBER>
```

```
bdeploy remote-deployment --instanceGroup=<GROUP_NAME> --activate --uuid=<INSTANCE_ID> --version=<INSTANCE_VERSION_NUMBER>
```

Finally, start the instance - this will start all processes of the given instance which are set to be started automatically. Again - do this for all instances created.

```
bdeploy remote-process --instanceGroup=<GROUP_NAME> --uuid=<INSTANCE_ID> --start
```

Note that the aforementioned command only starts processes that have the start type `INSTANCE`. To start manual applications, the specific ID of the application must be provided, like so:

```
bdeploy remote-process --instanceGroup=<GROUP_NAME> --uuid=<INSTANCE_ID> --start --application=<APPLICATION_ID>
```
