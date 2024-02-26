---
order: 5
icon: database
---
# Storage Pooling

**BDeploy** can be configured to use a dedicated object database to pool common objects from several **BHives**. This can be done on individual BHive basis, or be configured to automatically setup new **BHives** to use pooling.

## Per-BHive Pool Setup

Each **BHive** can have a configured _pool directory_, which must be non-existant or an existing object database. You can enable pooling on a single **BHive** from the CLI using:

```
bdeploy bhive --hive=/path/to/hive --pool=/path/to/pool
```

!!!warning Caution
In case the **BHive** is currently being served by **BDeploy**, pooling will only be enabled after restarting the server. The **BHive Browser** page in **Administration** will however show the pooling status as `PENDING`.
!!!

:::{align=center}
![BHive Browser](/images/Doc_Admin_BHive_Browser.png){width=480}
:::

!!!info Note
Pooling naturally only makes sense if more that one **BHive** is configured to use the *same* pool directory.
!!!

## Global Default Pool

**BDeploy** can be configured to provide a global default pool directory which is automatically configured on *new* BHives (e.g. new **Instance Group**, new **Software Repository**), but not on existing BHives. This setup can be performed during init:

```
bdeploy init --root=/path/to/root ... --pooling [--pool=/path/to/somewhere/pool]
```

You can also setup the global default after init using:

```
bdeploy pool --root=/path/to/root --defaultPool=/path/to/pool
```

!!!info Note
By convention, the pool should be a subdirectory of the **BDeploy** root (i.e. what you pass as `--root=/path/to/root`) called `objpool`. This is the path used when `--pooling` is enabled, but `--pool=` is *not* given. This is not technically required, and the pool can even reside on different filesystems when set using `--pool=` during `init` or later using `--defaultPool=`.
!!!

### Global Usage Threshold

The _global usage threshold_ specifies how often a certain object has to be seen in **BHives** participating in a particular pool. Only once this threshold is reached will an object be move to the pool. The threshold can be set on the CLI, its default value if **2**:

```
bdeploy pool --root=/path/to/root --usageThreshold=3
```

## Pooling the *default* BHive

The *default* **BHive** created during `init` will be configured to use pooling if `--pooling` is given. Otherwise this can be changed using the [Per-BHive Pool Setup](#per-bhive-pool-setup) later on.

## Re-organizing Pools

Pooling works semi-_offline_ in **BDeploy**. Each **BHive** uses its pool as *read-only* additional data-source. The _Pool Re-organization_ Job will (at some configured point in time or when triggered manually) re-organize pool storage by finding all objects eligible to be moved to the pool and doing so. Thereafter those objects are removed from their origin **BHives**. As a last step, the job will find objects in the pool which are no longer referenced by any of the **BHives**, and delete them.

:::{align=center}
![Jobs](/images/Doc_Admin_Jobs.png){width=480}
:::

## _Unpooling_ or disabling Pools

Pooling can be disabled on **BHives** if pooling is currently enabled. However this operation requires to _unpool_ required objects from the currently set pool back into the **BHive** local storage. You can do this using:

```
bdeploy bhive pool --hive=/path/to/hive --unpool
```

## Managing Pool through Administrative UI

Pooling can be enabled or disabled through the **BHive Browser** administrative UI. Selecing a **BHive** will give you the options to enable and disable pooling. This is a little less flexible compared to the CLI, as it will enable pooling only on the globally set default pool - if there is one set. Otherwise enabling through the UI is not possible.

:::{align=center}
![BHive Details](/images/Doc_Admin_BHive_Details.png){width=480}
:::
