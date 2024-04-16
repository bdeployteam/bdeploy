---
order: 5
icon: server
---

# Master

## Modes of Operation

**BDeploy** may be set up in different modes, making a few different overall usage scenarios possible:

Mode | Function
--- | ---
`STANDALONE` | A standalone **BDeploy** master which is responsible for itself, its nodes, and every application deployed thereon.
`MANAGED` | A **BDeploy** master which is controlled by a `CENTRAL` **BDeploy** master. The `MANAGED` master can still be used nearly the same as the `STANDALONE` master, with very few restrictions. A `MANAGED` master can **additionally** be controlled indirectly through the attached `CENTRAL` master
`CENTRAL` | Allows a central **BDeploy** master to control and manage multiple `MANAGED` masters. The `CENTRAL` master itself has no local deployment capabilities. Its purpose ist _exclusively_ to control other masters. Other than that, from a users perspective, the server handles mostly like a `STANDALONE` or `MANAGED` master.
`NODE` | A **node** which can be attached to either a `STANDALONE` or `MANAGED` server as additional target location to run applications on.

:::{align=center}
![STANDALONE Deployment Scenario](/images/Scenario_Standalone.svg){width=480}
:::

Compared to the rather straightforward standalone scenario above, a `CENTRAL` and `MANAGED` server setup allows for more flexibility, while being manageable through a single Web UI:

:::{align=center}
![CENTRAL/MANAGED Deployment Scenario](/images/Scenario_Central_Managed.svg){width=480}
:::

!!!info Note
You can still manage every `MANAGED` server directly through its own Web UI. Creation of new **Instance Groups** is restricted to the `CENTRAL` Web UI though.
!!!

**BDeploy**'s `CENTRAL` mode is built in a way that attached `MANAGED` servers can have an alternate URL, making it possible to manage servers which are known under different names in the local network (e.g. VPN/NAT, alternate DNS, etc.).

!!!info Note
See [Central/Managed Specific Configuration](/user/central/#centralmanaged-specific-configuration) for more details.
!!!

## Initialization

To start using BDeploy you will at least need a single **master**. The **master** needs to be initialized before it can be started.

The **root directory** contains all the runtime data. It is adviseable to select an empty directory in the data area of your system (e.g. /var/bdeploy on Linux) that is intended exclusively for this purpose. Keep the root directory separate from the BDeploy binary installation. Make sure that there is enough space available.

A `STANDALONE` master can be initialized like this:

```
bdeploy init --root=</path/to/root> --hostname=<hostname> --mode=STANDALONE --port=7701 --initUser=<username> --initPassword=<password>
```

The `init` command will create the initial administrator user from the `--initUser` and `--initPassword` parameters. This user has full administrative privileges. You can use the `bdeploy login` command to authorize all other CLI commands for your user.

!!!warning Warning
Make sure to choose a secure password!
!!!

The `init` command will write an access token to a file if given with `--tokenFile`. The main purpose of this tokens is automation (scripting) and testing. The token is a _system_ token which is not associated with any actual user. This token is important when initializing a root for a `NODE`, as it will be required when attaching the `NODE` to a `STANDALONE` or `MANAGED` master. On the master you can generate a token for any user from the **BDeploy** UI anytime later on.

The `--mode` parameter of the `init` command is used to determine the future purpose of the **BDeploy** root. The mode can be `STANDALONE`, `MANAGED`, `CENTRAL` or `NODE` (also see [Node](/setup/node/#nodes)).

!!!info Note
It is not recommended ([but possible](/user/central/#migrating-between-modes)) to change the mode of an initialized root retroactively, so take care to use the correct mode for the intended use.
!!!

The result of the `init` command is a **root directory** which can be used by the **start** command.

!!!warning Warning
Be aware that the user running the **start** command later on **must** be the owner of the root directory. Otherwise **BDeploy** will run into problems when accessing files. If you configure **BDeploy** as a service make sure to either run the service as the same user who initialized the root directory, or pass ownership of the root directory to the user running the service before starting it.
!!!

## Launch

After the initialization - which needs to be done only once - the **master** can be started with the following command:

```
bdeploy master --root=</path/to/root>
```

This will start the **master** server which also hosts the Web UI: [https://localhost:7701](https://localhost:7701)

!!!info Note
The server is using a self-signed certificate by default. Thus you need to instruct your browser to accept it. See [Custom Certificate](/setup/certificate/#custom-certificate) for instructions on how to provide a better certificate.
!!!

## User

Only authenticated users have access to the Web UI. The initial user has been created by the `init` command. Use this user to log in to the Web UI and create additional users (or provide external authentication mechanisms) from the [User Accounts](/experts/system/#user-accounts) administration page.
