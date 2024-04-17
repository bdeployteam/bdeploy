---
order: 2
icon: package-dependencies
---

# Runtime Dependencies

As you saw before ([`app-info.yaml`](/power/product/#app-infoyaml)), **Applications** can declare dependencies to third-party **Manifests**. These **Manifests** are hosted in **Software Repositories** on the **BDeploy** Server.

To make these **Manifests** available on the server, you need to:

- Use `bhive import` to import the directory containing the third-party software into a local **BHive**.
- Use `bhive push` to push the created **Manifest** to the **Software Repository** of your choice.

Alternatively, the Web UI provides a mechanism to upload arbitrary software to **Software Repositories**.

## Manifest Naming

Third party software **Manifests** can have basically any name. If you want to provide different **Manifests** per target operating system though, you will have to follow a simple naming rule: append the operating system name to the **Manifest** name part, e.g.:

- `my/external/software/windows:1.0.0`
- `my/external/software/linux:1.0.0`

It can then be referenced by an `app-info.yaml` using the short-hand syntax `my/external/software:1.0.0` and **BDeploy** will choose the correct one depending on the target operating system.

# Runtime Dependency Packaging

**BDeploy** will always make sure that your products are self contained. This means that [Runtime Dependencies](/power/runtimedependencies/#runtime-dependencies) are packaged with the product data at build time. The result is a product which contains all dependencies. This also means that pushing a product to a target server does not require **any** prerequisites on that target server.

!!!info Note
Included runtime dependencies will not show up on the target server's Web UI anywhere. There is no [Software Repository](/power/runtimedependencies/#software-repositories) created automatically for any third-party software. It is simply included in the product.
!!!

# Software Repositories

**Software Repositories** are a storage location for any external software required by products. In addition, BDeploy products can be stored and managed in **Software Repositories**, from where they can be transferred (imported) to **Instance Groups**. A **Software Repository** shares its namespace with **Instance Groups**, which means that the unique name of a **Software Repository** must not collide with any name of a **Software Repository** or **Instance Group**.

:::{align=center}
![Software Repositories](/images/Doc_SoftwareRepo.png){width=480}
:::

## Upload software

To upload external software, open a software repository and click on the [ **Upload Software** ] button in the toolbar. Then click on **browse** or **drop files**. You can upload zip packages.

After uploading arbitrary content, you need to specify the **name** of the software, the **version** and the supported **operating systems**.
If you already have a package in a BHive format or a package including a product descriptor containing all of this metadata, the available information will be used automatically.

:::{align=center}
![External Software Upload](/images/Doc_SoftwareRepoFillInfo.png){width=480}
:::

After all requested information is entered, click **Import** to finally import the files to the **Software Repository**.

:::{align=center}
![External Software Import](/images/Doc_SoftwareRepoUploadSuccess.png){width=480}
:::

If the upload was successful, the software for each operating system will show up.

The available software packages and products can be viewed and **downloaded** if required.

:::{align=center}
![Software Details](/images/Doc_SoftwareRepoDetails.png){width=480}
:::

## Software Repositories Access

**Software Repositories** are created and managed by global administrators. A **Software Repository** needs `READ` permission to be visible and readable. `WRITE` permissions are required to manage the software packages in the repository. Therefore, to upload software, a user requires `ADMIN` or `WRITE` permissions either globally or assigned in the **Software Repository Permissions** panel.

:::{align=center}
![Software Repository Permissions](/images/Doc_SoftwareRepoPermissions.png){width=480}
:::

The **Software Repository Permissions** panel works similarly to the [**Instance Group Permissions**](/user/instancegroup/#instance-group-access).

!!!info Note
You can make **Software Repository** public by assigning local `READ` permission to [**All Users Group**](/experts/system/#all-users-group).
!!!