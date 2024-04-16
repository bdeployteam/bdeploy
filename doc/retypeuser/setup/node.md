---
order: 3
icon: git-branch
---

# Nodes

**Nodes** are optional and only required in the case that the application that you want to deploy should run on different servers but be managed from a certain master. By default a master is itself a node as well where applications can be deployed to.

## Initializing

**Nodes** must be initialized on the corresponding machines (or on the same machine using non-default ports; the default port is 7701).
The command is similar to the one used to initialize the **master**. Pass (for example) `--port=7702` to change the
default port to some other value to avoid conflicts. Running multiple **BDeploy** nodes on the same machine is
supported for testing, but not recommended for productive setups (as the **master** can be deployed to directly anyway - no need for an explicit **node**).

```
bdeploy init --root=</path/to/root> --hostname=<hostname> --nodeIdentFile=node-ident.txt --port=7702 --mode=node
```

Note the `--nodeIdentFile` parameter given to the `init` command: this file is created during `init`, and will contain the required access information for this **node**. You need it to register the **node** with the **master**, so make sure you will have it available when doing so.

## Launch

After the initialization - which needs to be done only once - the **node** can be started with the following command:

```
bdeploy start --root=</path/to/root>
```

## Administration

To register, remove or manage a **node** on the **master** you can either use the command line interface or the administrative Web UI on the **master**.

### Command line interface

Use the CLI to register, remove or manage a **node** on the **master** (see the `bdeploy remote-node --help` command for more information).

```
bdeploy remote-node --remote=https://<hostname>:7701/api --tokenFile=master-token.txt --add=<node-name> --nodeIdentFile=node-ident.txt
```

!!!info Note
Both the `https` (beware the `s`) prefix and the `/api` suffix are required in all **BDeploy** URIs.
!!!

The **node** is now registered as 'node-name'. This is the name which is reported when querying **nodes** from the **master**.
This is also the name displayed when configuring applications to run on certain **nodes** later on.

### Administrative Web UI

The administrative Web UI provides the ability to add, remove, repair, and otherwise manage **nodes**.

:::{align=center}
![Node Administration](/images/Doc_Admin_Nodes_Details.png){width=480}
:::

To add a new node, click the `Add Node...` button. In the resulting panel you can drag and drop the `node-ident.txt` file created during the initialization step. This will prefill the panel with the required information. You can change node-name and URI according to your needs before saving.

:::{align=center}
![Add Node](/images/Doc_Admin_Nodes_Add.png){width=480}
:::

!!!info Note
In case you do not have the `node-ident.txt` file to drop to the drop zone, you can also drag the _content_ of the file as plain text to the drop zone - this has the same effect.
!!!

When editing an existing node by selecting it in the list, you can also drop the same `node-ident.txt` file to update the information. This way you can create nodes before they actually exist - using a dummy URI and authentication - and later on update the node administration with the actual configuration. This allows to configure instances to their future nodes without them existing yet.

### Replacing and Restoring Nodes

**BDeploy** supports replacing and restoring nodes. This is primarily meant to restore nodes after a catastrophic failure of the node in question. This feature is capable of reinstalling all required node configurations of all instances which are/were deployed on the node.

Replacing can be performed via the [Administrative Web UI](/setup/node/#administrative-web-ui), much the same as _editing_ a node. The steps to replace a node are:

1. Find the node in the [Administrative Web UI](/setup/node/#administrative-web-ui) and click it to open the node details panel.
2. In the panel, click [ **Replace...** ]. This will bring up the _Replace Node_ panel.
3. Initialize the new node on the target system. This can be done at any time up until now in preparation.
4. Drag & drop the `node-ident.txt` file to the drop zone in the _Replace Node_ panel. The URI and authentication information will be prefilled.
5. Click the [ **Save** ] button. The panel will enter loading state. You can follow the progress of the operation in the _Activities_ panel from the main menu.
6. Once the operation completes, you will be brought back to the node details panel.
7. Replacing and restoring has been performed, and all instances which use this node are available again.

### Convert/Migrate to Node

Existing `STANDALONE` and `MANAGED` servers can be migrated to type `NODE`. This can be handy in case software needs to run standalone on a `NODE` before the actual master server is available. In this case, setup a `STANDALONE` server on the node, configure all the required software, and migrate the server to `NODE` type later.

To convert an existing server:

1. Open the [Administrative Web UI](/setup/node/#administrative-web-ui) on both servers in separate browser tabs.
2. On the new master server, click [ **Add Node...** ] in the toolbar to bring up the _Add Node_ panel.
3. On the to-be-migrated server, click the **master** node in the list of nodes. Click the [ **Convert to Node...** ] button.
4. Now drag the yellow node identification card from the to-be-migrated server to the new masters drop zone.
5. Information about the server is pre-filled in the form. Double check the information and press [ **Save** ] to start the migration.
6. You will be prompted to confirm the migration. Click [ **Migrate** ] to continue.

:::{align=center}
![Node Conversion](/images/Doc_Admin_Nodes_Conversion.png){width=480}
:::

After the migration is complete, the server will be available as a `NODE` on the new master server. All the **Instances** have been migrated to the new master server.

!!!info Note
During migration all **Instances** are modified in a way such that software still runs on the same physical hardware as before, i.e. the now-new master server is **not** participating in any of the **Instances** configuration. Instead, the previously-master, now-node server is still configured as deployment target where this was the case previously.
!!!

!!!info Note
If the to-be-migrated server has **nodes** attached to it, all those **nodes** will be attached to the new master after migration.
!!!
