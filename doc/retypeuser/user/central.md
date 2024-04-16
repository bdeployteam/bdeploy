---
order: 1
icon: gear
---

# Central/Managed Specific Configuration

The [Central/Managed Mode](/setup/master/#modes-of-operation) requires some configuration to attach `CENTRAL` and `MANAGED` servers.

On the `CENTRAL` server side:

- Each **Instance Group** has a list of `MANAGED` servers attached to it.
- Each **Instance** is associated with exactly **one** of those `MANAGED` servers.
- Synchronization to the `CENTRAL` servers data store is done on demand (through a button in the Web UI). When synchronizing from a `MANAGED` to a `CENTRAL` server, only _configuration_ data is synchronized. Actual **Product** and **Application** data is only synchronized when explicitly requested through the [Product Synchronization](/user/central/#product-synchronization) page on the `CENTRAL` server.
- It is possible to create a new **instance version** on the `CENTRAL` server from a **product version** which has been pushed only to the `CENTRAL` server (but not to the `MANAGED`). In this case, the required product data is sent to the `MANAGED` server once **install** is performed on this **instance version**. In this scenario **install** **must be** performed on the `CENTRAL` server.

On The `MANAGED` server side:

- Creation and editing of **Instance Group** (title, description, logo, etc.) is not possible locally. This has to happen on the `CENTRAL` server, as information **must** match.
- Creation and editing of **Instances** (applications, parameters, etc.) is possible just the same as with a `STANDALONE` server. New data will be synchronized to the `CENTRAL` server the next time the `CENTRAL` server requests such a synchronization.

!!!info Note
**BDeploy** is built for restrictive networks. The assumption is that the `CENTRAL` server can contact `MANAGED` servers, but never the other way around.
!!!

## Attaching an Instance Group to a Central Server

You will notice that a `MANAGED` server will greet slightly different on the welcome page. Instead of a [ **Add Instance Group** ] button, there is a [ **Link Instance Group** ] button, and the text reads accordingly.

:::{align=center}
![Managed Server Welcome Page](/images/Doc_ManagedEmpty.png){width=480}
:::

Attaching an **Instance Group** is a two way operation.

- The `MANAGED` server needs to share some details about itself with the `CENTRAL` server. This happens either via drag & drop of an element from the `MANAGED` servers Web UI to the `CENTRAL` servers Web UI, or manually by downloading the `MANAGED` servers information and dropping that file to the `CENTRAL` servers Web UI.
- On the `CENTRAL` server, you will choose the **Instance Group** which should be attached. The **Instance Group** _must_ exist on the `CENTRAL` server. If the `MANAGED` server happens to have an **Instance Group** of the same name already (i.e. if the `MANAGED` server was migrated from `STANDALONE`), this is perfectly fine - **Instances** in this **Instance Group** will not be lost, they will be attached to the `CENTRAL` server instead.
- The `CENTRAL` server will try to contact the `MANAGED` server. You can provide an alternate URL to accomodate for hostname differences (different DNS, NAT, VPN, etc.) before it does so.
- Once contacted, the `CENTRAL` server will push the **Instance Group** meta-data to the `MANAGED` server, as the information on the `CENTRAL` is the single source of **Instance Group** configuration.
- Finally, the `CENTRAL` server will fetch all potentially existing **Instance** information from the `MANAGED` server. This is only relevant if the `MANAGED` server was migrated from `STANDALONE` mode before.

To attach in **Instance Group**, you need both Web UIs of `CENTRAL` and `MANAGED` server. Make sure you have both available and open in browsers next to each other (on the same machine, so drag & drop will work).

!!!info Note
You can distinguish `CENTRAL` and `MANAGED` servers by the according banner in the expanded main menu in the Web UI.
!!!

On the `CENTRAL` server, choose [ **Managed Servers** ] on the main menu after selecting an **Instance Group** you want to attach. This will take you to the **Managed Servers** page for that **Instance Group**. Initially, this page will be empty.

:::{align=center}
![Managed Servers Page on Central](/images/Doc_CentralEmptyServers.png){width=480}
:::

Click the [ **Link Managed Server** ] button to initiate attaching a `MANAGED` server to this **Instance Group**.

:::{align=center}
![Link Managed Server Panel](/images/Doc_CentralLinkServer.png){width=480}
:::

You will be prompted to drop `MANAGED` server information on a drop-zone. You can find the counterpiece on the `MANAGED` server. To initiate attaching on the `MANAGED` server, click the [ **Link Instance Group** ] button on the main **Instance Group** page (which is the initial start page of the Web UI). This will open the _Link Instance Group_ panel.

:::{align=center}
![Link Instance Group](/images/Doc_ManagedLinkGroup.png){width=480}
:::

The `MANAGED` server provides the card to drag to the counterpiece on the `CENTRAL` server. Drag it over to fill out information on the `CENTRAL` server automatically.

!!!info Note
Alternatively, use the _Manual and Offline Linking_ panel to download the information in an encrypted form. This can be uploaded on the `CENTRAL` by dropping the file on the very same drop-zone.
!!!

Once the information is dropped on the according drop zone on the `CENTRAL` server, it will fill out the information. Adapt the URL if required and fill in a human readable description of the `MANAGED` server. The URL should be reachable from the `CENTRAL` server and accomodate for any hostname mapping required (NAT, VPN, DNS, ...).

:::{align=center}
![Filled out Link panel](/images/Doc_CentralLinkServerFilled.png){width=480}
:::

Clicking [ **Save** ] will initiate the actual attachment process. The `CENTRAL` server will contact the `MANAGED` server using the provided URL. It will then perform the initial synchronization of data. Once this is done, the panels close on both servers. The attached server appears on the `CENTRAL`, and the attached **Instance Group** appears on the `MANAGED` server. In case attaching is not possible automatically, you will be provided with the possibility to download synchronization information from the `CENTRAL` server. You can drop this in the _Manual and Offline Linking_ panel on the `MANAGED` server to complete linking without actual contact to the server.

!!!info Note
Servers which do not have an actual connection to each other, but are linked manually, will not be able to synchronize instance information. The link is purely _informational_, to hint that the server _exists_. More possibilities for synchronization may be added in the future.
!!!

:::{align=center}
![Link Success (Central)](/images/Doc_CentralLinkDone.png){width=480}
:::

## Instance Synchronization

Once a `MANAGED` server is attached to the `CENTRAL` server, **Instance** data can be synchronized from the `MANAGED` server on demand by the `CENTRAL` server. This can happen either from the **Managed Servers** page you saw before, by pressing [ **Synchronize** ] on the according server or directly from the **Instance Overview** and **Instance Dashboard**/**Instance Configuration** pages.

!!!info Note
The [ **Synchronize** ] **only** exists on the `CENTRAL` server.
!!!

:::{align=center}
![Instance Browser (Central)](/images/Doc_CentralInstanceList.png){width=480}
:::

:::{align=center}
![Instance Dashboard (Central)](/images/Doc_CentralInstanceDashboard.png){width=480}
:::

:::{align=center}
![Instance Configuration (Central)](/images/Doc_CentralInstanceConfiguration.png){width=480}
:::

!!!info Note
It is not required to synchronize the other way (`CENTRAL` to `MANAGED`) as this happens implicitly when performing changes to an **Instance**. Changes are actually performed **always** on the _controlling_ master, which is **always** the `MANAGED` server.
!!!

## Migrating between Modes

There is a limited possibility to change the _purpose_ of an already initialized **BDeploy** server root directory. It is only possible to migrate from `STANDALONE` to `MANAGED` and vice versa, as data is _mostly_ compatible. A command line tooling exists for this purpose:

!!!info Note
Migration from `STANDALONE` or `MANAGED` to `NODE` is available using a dedicated workflow from the Web UI. This allows to start off an installation as `STANDALONE` and later on migrate that to be a `NODE` in a larger setup. See [Convert/Migrate to Node](/setup/node/#convertmigrate-to-node)
!!!

```
bdeploy config --root=</path/to/root> --mode=MANAGED
```

The value for mode may be `MANAGED` or `STANDALONE`. The actual migration of data may be performed later on when first accessing them. For instance, when clicking an **Instance Group**, you might be prompted that an **Instance Group** requires to be attached to a `CENTRAL` server in `MANAGED` mode, and the **Attach to Central Server** wizard is launched.

## Product Synchronization

When working with `CENTRAL` and `MANAGED` servers, products can be uploaded to either of the servers. However, a product version must be available on the server which is used to update and configure an **Instance** using that version.

The recommended way of working is to exclusively use the `CENTRAL` server for all tasks, e.g. pushing new product versions, changing configuration, etc. When _installing_ an **Instance** version to the target server, the required product data is automatically transferred as part of the process.

Sometimes it might still be necessary to transfer product versions from one server to another. For instance, a product version was directly pushed to `MANAGED` server _A_, but is required as well on `MANAGED` server _B_. In this case you can use the **Product Synchronization** wizard to copy product versions from one server to another from the `CENTRAL` server.

You can find the _Product Synchronization_ panel on the **Products** page on the `CENTRAL` server inside a given **Instance Group**. The additional [ **Synchronize Product Versions** ] button is only available on the `CENTRAL` server.

:::{align=center}
![Synchronize Product Versions](/images/Doc_CentralProdSync.png){width=480}
:::

Clicking it will open the _Product Synchronization_ panel. First you need to choose a _direction_ of transfer. Either transfer products _to_ or _from_ the `CENTRAL` server. After selecting a direction, you will have to choose the `MANAGED` server to transfer from or to.

:::{align=center}
![Choose Server](/images/Doc_CentralProdSyncServer.png){width=480}
:::

Clicking a server will fetch available products from that server. The list will contain product versions which are not yet present on the target server. Check all you want to transfer and click [ **Transfer** ].

:::{align=center}
![Choose Product Versions](/images/Doc_CentralProdSyncVersion.png){width=480}
:::

The panel will initialize the transfer of the product version(s) in the background. You can keep track of transfers using the global activity report available from the main menu. Once a product version is transferred, it will appear in the list of available products on the target server.
