---
order: 7
icon: rows
---
# Instance Group

An **Instance Group** acts as logical container for **Instances**. It typically contains several **Instances** for different purposes (productive, test, development). Adding them to the same group provides a better overview which of them are belonging together. An empty page is displayed if no instance group has been created. 

:::{align=center}
![Empty Instance Group Overview](/images/Doc_EmptyGroups.png){width=480}
:::

Use the [ **+** ] button in the toolbar to create a new **Instance Group**.

:::{align=center}
![Create Instance Group](/images/Doc_AddGroupPanelFilled.png){width=480}
:::

!!!info Note
The name of an instance group cannot be changed, and is used internally as a name in the file system on the server. Be careful when choosing the name.
!!!

The _Automatic Cleanup_ option helps to reduce the amount of consumed hard disk space. During the lifetime of an **Instance Group**, probably many new versions of a **Product** are deployed. To avoid that the hard disk is filled up with **product versions** that are not used anymore, there is an option to activate an automatic cleanup job. If the option _Automatic Cleanup_ is enabled, old **product versions** that are no longer in use by **Instances** of this **Instance Group** are deleted automatically. To avoid that a **Product** vanishes completely, the very latest **product version** always remains.

## Instance Group Dialog

All **Instance Groups** that are visible to the user (as per the assigned permissions) are shown in the **Instance Groups** dialog.

:::{align=center}
![Demo Instance Group](/images/Doc_DemoGroup.png){width=480}
:::

## Instance Group Access

A new **Instance Group** is initially only visible to users with global read permission (or higher). You can assign permissions to users either globally, or per **Instance Group**. To assign local permissions, click the **Instance Group** to get to its **instance overview** and then click the [ **Group Settings** ] toolbar button. The Group Settings panel is shown.

:::{align=center}
![Instance Group Settings](/images/Doc_GroupSettings.png){width=480}
:::

Click the [ **Instance Group Permissions** ] button to navigate to the permission panel.

:::{align=center}
![Instance Group Permissions](/images/Doc_GroupPermGlobalOnly.png){width=480}
:::

To assign a local permission to a given user, click the [ **Modify** ] button in the according table row. A dialog will pop up asking for the permission to assign. Choose an apropriate one and click [ **OK** ].

:::{align=center}
![Instance Group Permission Assignment](/images/Doc_GroupPermSetWrite.png){width=480}
:::

The local permission will be shown next to the global permission. The highest available permission level is taken into account for any permission checks.

:::{align=center}
![Instance Group Local Permission](/images/Doc_GroupPermAssigned.png){width=480}
:::

The user table is grouped by permission assignment, either global, local or unassigned.

## Initial Steps

Click on an **Instance Group** to open the **instance browser** of this group. Here you can see all **Instances** grouped by their _purpose_ (can be `Development`, `Test` or `Productive`).

:::{align=center}
![Empty Instance Browser](/images/Doc_DemoInstancesEmpty.png){width=480}
:::

Since an **Instance** requires a **Product**, an empty **Instance Group** will initially display a shortcut to the [products](#manage-products) page. If there is at least one **Product** available already, the shortcut changes to help you to [create a new instance](#create-new-instances).

:::{align=center}
![Empty Instance Browser with Product available](/images/Doc_DemoInstancesNoInstance.png){width=480}
:::

## Manage Products

**Products** can be obtained by [building a **Product**](/power/product/#building-a-product) or by downloading an existing version from another **Instance Group** on the same or another **BDeploy** server, using the [ **Download** ] button on the **Product** details panel.

!!!info Note
On the BDeploy Releases page you will find some sample products to experiment with for each release, see https://github.com/bdeployteam/bdeploy/releases
!!!

:::{align=center}
![Empty Products Page](/images/Doc_ProductsEmpty.png){width=480}
:::

On the **Products** page, click the [ **Upload Product** ] button to upload new **Products**.

:::{align=center}
![Upload Product](/images/Doc_ProductsUploadPanel.png){width=480}
:::

Click browse and choose a **Product** _ZIP_ file to upload, or simply drop one on the highlighted drop zone. The **Product** will be uploaded immediately.

:::{align=center}
![Upload Product (success)](/images/Doc_ProductsUploadSuccess.png){width=480}
:::

Once a **Product** is available, you can click it to open the **Product** details panel. This panel allows you to [ **Download** ] a **Product** version as _ZIP_, or [ **Delete** ] individual versions of the **Product** as long as it is not currently in use by an **Instance** version. There rest of the panel provides additional information about the **Product**.

:::{align=center}
![Product Details Panel](/images/Doc_ProductDetailsPanel.png){width=480}
:::

!!!info Note
There are two distinct **Download** actions available on the details panel; **Download BHive** will download a re-importable ZIP file which contains all required data in a **BDeploy** internal data format. This file can be transferred to another **BDeploy** server and imported. **Download Content** on the other hand will give you a ZIP file which contains the plain, exported files as they would ultimately end up on the target. This is primarliy useful for inspection of the content, access to individual files and such. However this is *not* meant for transfer and import on another server.
!!!

## Create New Instances

To create a new **Instance**, click the [ **+** ] button at the bottom of the page. After giving the new **Instance** a name, purpose and description, the most important thing is to select the **Product** you want to deploy. Additionally, the initial **product version** has to be chosen. It can be changed later at any time (_up-_ and _downgrade_). 

:::{align=center}
![Create a new Instance](/images/Doc_InstanceAdd.png){width=480}
:::

It is a common requirement that certain **processes** of an **Instance** should be automatically started whenever the **BDeploy** server itself is started. To accomplish that, the _Automatic Startup_ flag of the **Instance** must be set. 

The **Instance** determines whether it is included in the automatic cleanup job. If the option _Automatic Uninstallation_ is enabled, the cleanup job will uninstall all **instance versions** that are older than the activated and the previously activated **instance version**. Due to this automatic uninstallation some of the old **product versions** might become unused. If the option _Automatic Cleanup_ is activated on the instance group, these unused **product versions** are deleted too (see [Instance Group](/user/instancegroup/#instance-group)).

Click an **Instance** to proceed to the [Instance Dashboard](/user/instance/#instance-dashboard).

:::{align=center}
![Instance Browser](/images/Doc_DemoInstance.png){width=480}
:::

## Global Attributes on Instance Groups

[Global Attributes](/experts/system/#global-attributes) can be configured - as the name suggests - globally in the system administration. Each Instance Group **may** then associate a **value** with each of these global attributes.

:::{align=center}
![Set Global Attribute Values](/images/Doc_SetGlobalAttributeValue.png){width=480}
:::

The value can then be used for grouping instance groups through the grouping panel.

:::{align=center}
![Group by Global Attribute](/images/Doc_GroupingPanel.png){width=480}
:::

## Instance Group Attributes

Very similar to the global attributes, each **Instance Group** may have its own **attributes**. These attributes are not global, but are only visible within the **Instance Group**. Each **Instance** can assign a value to each of these attributes. Again, similar to the **Instance Group** browser, the **Instance** browser can use the assigned values to group **Instances** by those values.

The **attributes** are defined in the **Instance Group** settings. The **values** are assigned in the **Instance** settings.

## Systems

**Systems** are a means of further grouping instances of different products inside a single **Instance Group**. **Systems** also provide means to apply shared configuration through [System Variables](/user/instancegroup/#system-variables).

Once created, a **System** can be used by an **Instance** by configuring the in-use **System** in the [Instance Configuration](/user/instance/#instance-configuration) of each **Instance** which should _participate_ in this particular **System**.

!!!info Note
A **System** definition may be used by many **Instances**, but each **Instance** can only be part of one **System** (or none).
!!!

### System Variables

**System Variables** can be configured on **Systems**. Those variables can then be used using _link expressions_ on [Link Expressions](/user/instance/#link-expressions) (process parameters, endpoint configuration, instance variables, configuration files).

### System Templates

A **System Template** allows definition of complete systems. Since those templates life outside of any specific single product, they are single YAML files which have a separate lifecycle.

A **System Template** will typically create multiple instances of multiple products in a to-be-created system. It can also create [System Variables](/user/instancegroup/#system-variables), and use template variable overrides to control in much detail how instances are created.

:::{align=center}
![System Template Wizard](/images/Doc_SystemTemplate_Wizard.png){width=480}
:::

The **System Template** Wizard will request you to upload a [`system-template.yaml`](/power/product/#system-templateyaml) file. Once this is done, you can specify template variable values for variables defined and used in the system template itself. These are not to be confused with template variables used in instance templates, which will be queried at a later stage in the process as required. **System Templates** can use template variables in their own YAML descriptor to provide very dynamic (pre-)configuration for instance templates, thus those variables need to be available before configuring instance templates.

:::{align=center}
![System Template Instance Configuration](/images/Doc_SystemTemplate_InstanceTemplates.png){width=480}
:::

The Wizard will now query each instance defined in the **System Template**. This allow users to skip instances, or configure intances in a way just "as if" they would apply the individual **Instance Templates** to a new, empty instance each.

:::{align=center}
![System Template Wizard - Done](/images/Doc_SystemTemplate_Done.png){width=480}
:::

Once done, the Wizard will show success confirmations along with potential warnings and errors that occured during application of the template(s).
