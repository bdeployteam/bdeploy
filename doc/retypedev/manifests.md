---
order: 6
icon: file-code
---
# Manifests

Almost all of **BDeploy**'s storage is encapsulated in **Manifests** in a **BHive**. This holds true not only for actual application/product data, but for any configuration data as well. These have a few advantages over traditional storage:

* Contents is immutable
* Contents is validatable (through checksums)
* Contents is automatically versioned
* Contents history is available

There are a bunch of **Manifests** for several purposes.

:::{align=center}
![Special Manifest Types](/images/ManifestOverview.png){width=480}
:::

Manifest Name | Purpose
---           | ---
`InstanceGroupManifest` | Stores information (name, description, logo) related to an **Instance Group**. There is exactly one `InstanceGroupManifest` per **Instance Group** **BHive**.
`SoftwareRepositoryManifest` | Stores information (name, description) related to a **Software Repository**. There is exactly one `SoftwareRepositoryManifest` per **Software Repository** **BHive**.
`InstanceManifest` | The `InstanceManifest` holds information about an instance and keeps track of the all `InstanceNodeManifest` for the **Instance**.
`InstanceNodeManifest` | Keeps track of the **Applications** and their configuration per configured **Node** in the **Instance**.
`ProductManifest` | Holds information about a product and keeps track of all the `ApplicationManifest` for the **Product**.
`ApplicationManifest` | Holds information about a single application, includes the `app-info.yaml` for the application which holds information about all supported parameters, etc.
`MinionManifest` | Holds information about available nodes on a **master**.
`UserDatabase` | Manages special per-user **Manifests** which hold information about each user.

## MetaManifests

In addition to traditional **Manifests** there are **MetaManifests**. These allow to _attach_ certain information to other **Manifests**. This allows to update the attached **MetaManifest** independently of the _immutable_ **Manifest**, whilst keeping all benefits of a **Manifest** for the **MetaManifest** as well (versioning, history, ...).

MetaManifest Name | Purpose
---               | ---
`InstanceState` | Keeps track of **Instance** state related information (installed **Instance** versions, activated **Instance** version, etc.).
`InstanceManifestHistory` | Keeps track of the history of an **Instance** version, storing timestamps at which certain actions happened (creation, installation, activation, etc.).
`ManagedMasters` | Keeps track of attached managed masters on a central master.
`ControllingMaster` | Keeps track of the responsible controlling master for a single **Instance**.
