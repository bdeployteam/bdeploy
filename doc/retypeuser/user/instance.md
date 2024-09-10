---
order: 6
icon: project
---

# Instances

## Instance Dashboard

The [Instance Dashboard](/user/instance/#instance-dashboard) is the central location for managing an instance. It provides an overview about all configured **Processes** and on which nodes they are running. Each **Process** is represented by an entry on the respective node.

If the instance has no active version yet, the dashboard is empty and shows a shortcut to the [Instance Configuration](/user/instance/#instance-configuration) instead.

Instances in **BDeploy** need to be configured, installed and activated. Once [configured](/user/instance/#instance-configuration), the instance can be installed and activated either directly from the dashboard or by using the [Instance History](/user/history/#instance-history) page.

:::{align=center}
![Empty Instance Dashboard](/images/Doc_InstanceEmpty.png){width=480}
:::

Once the instance has been [configured](/user/instance/#instance-configuration), installed and activated, the dashboard shows an overview of all available nodes, their respective state (CPU Usage, Load Average where available) and the **Processes** configured on them.

:::{align=center}
![Instance Dashboard](/images/Doc_InstanceDashboardActive.png){width=480}
:::

!!!info Note
For more information, see [Process Control](/user/processcontrol/#process-control).
!!!

## Instance Configuration

The first step is to select the desired and required nodes used in the instance. The **master** node is enabled by default, the virtual **Client Applications** node is always present if client applications exist in the **product**. Additional nodes can be selected from the **Instance Settings**'s **Manage Nodes** section.

:::{align=center}
![Manage Nodes](/images/Doc_InstanceManageNodes.png){width=480}
:::

Next up, you can assign **processes** to **nodes** by selecting **applications** from the **node**'s [ **Add Application...** ] toolbar button.

:::{align=center}
![Add Processes](/images/Doc_InstanceAddProcessPanel.png){width=480}
:::

The panel will display all **applications** along with their **process templates** if available. You can click the [ **Add** ] button to add a new, unconfigured **process** to the **node**. Using the [ **Add template** ] button, you can add a new **process** from a **template**, which typically includes a complete configuration of the selected **application**, see [Application Templates](/user/instance/#application-templates) for more information.

In any case, the **process** will appear in the selected **node**. You can use drag & drop to re-order **processes** within a **node**. The order of the processes is relevant - please see [Process Control Groups](/user/instance/#process-control-groups) for details.

:::{align=center}
![New Process](/images/Doc_InstanceNewProcess.png){width=480}
:::

!!!info Note
The virtual **Client Application Node** is not available if the product does not contain any client applications.
!!!

When changing the configuration of processes, you might notice a colored border next to new or modified processes. This indicate the current state the process is in. A newly added process receives a **green** border, a modified process receives a border in the **accent** color of the current theme and a process which has validation issues receives a border in the **warning** color of the current theme. Additionally, validation issues are displayed above any **node**.

:::{align=center}
![Configuration Validation](/images/Doc_InstanceConfigValidation.png){width=480}
:::

### Local Changes

**BDeploy** keeps track of any changes performed on any of the [Instance Configuration](/user/instance/#instance-configuration) pages panels. These changes can be viewed by pressing the [ **Local Changes** ] toolbar button.

:::{align=center}
![Local Changes Panel](/images/Doc_InstanceConfigLocalChanges.png){width=480}
:::

You can [ **Undo** ] and [ **Redo** ] changes. Even dismissable messages (on product update) can be brought back by [ **Undo** ] and [ **Redo** ]. To view the current changes compared to the state you started from, use the [ **Compare Local with Base** ] button.

:::{align=center}
![Local Changes Comparison](/images/Doc_InstanceConfigCompareChanges.png){width=480}
:::

### Process Settings

A **process** is started by executing the **start** command that is defined by the **application**. The parameters that are passed to the **process** are configured on the **Process Setting** panel. Click a **process** to access its settings panel.

:::{align=center}
![Process Settings](/images/Doc_InstanceConfigProcessSettings.png){width=480}
:::

From there, use the [ **Configure Parameters...** ] button to access the **parameter configuration**.

The available parameters, their type and whether or not they are mandatory or optional are defined by the **Application**. The dialog groups the available parameters into categories, which can be expanded by clicking them.

:::{align=center}
![Parameter Configuration](/images/Doc_InstanceConfigParams.png){width=480}
:::

!!!info Note
The **Application** defines in which order the parameters are passed to the **Process**. This order cannot be changed for predefined parameters.
!!!

Hovering the mouse over a parameter will show a small popup that contains a thorough description of the selected parameter. This also works in the **command line preview** section, as well as in any **compare** views throughout **BDeploy**.

**Validation issues** are displayed per group in the respective title and next to the affected parameter.

!!!info Tip
You can use the _Search Bar_ to search and filter for parameters even if they are not shown in the table. Groups will be hidden from the page unless a parameter matches - this includes optional (not yet configured) parameters.
!!!

#### Copy & Paste

You can copy a **process** configuration by accessing its **process settings** panel. Use the [ **Copy to Clipboard** ] button to copy the configuration to the clipboard. You can paste the configuration by accessing the [ **Add Application...** ] button of the desired **node**. Use the [ **Paste** ] button to paste the configuration from the clipboard.

:::{align=center}
![Process Settings](/images/Doc_InstanceConfigProcessSettings.png){width=480}
:::

!!!info Note
You need to grant **BDeploy** access to the PCs Clipboard for the [ **Paste** ] button to appear in the **node**'s **application** panel.
!!!

#### Optional Parameters

**Optional parameters** can be selected for each group using the [ **Select Parameters...** ] button present on the header of each parameter group.

:::{align=center}
![Optional Parameters](/images/Doc_InstanceConfigOptionalParams.png){width=480}
:::

Add an optional parameter by clicking the [ **Add** ] button in front of it. You can also remove an optional parameter by clicking the [ **Remove** ] button in front of it.

#### Custom Parameters

**Custom parameters** can be maintained in a dedicated parameter group which is always present. Because all **parameters** must have a determined sequence, **custom parameters** must define a **predecessor** parameter after which they are put on the command line. If no **predecessor** is defined, the parameter will end up **first** on the command line.

Click the [ **Add** ] button in the **Custom Parameters** group to add a new **custom parameter**.

:::{align=center}
![Add Custom Parameter](/images/Doc_InstanceConfigAddCustomParam.png){width=480}
:::

#### Global Parameters

**Global Parameters** are valid for all **Processes** of an **Instance** that have a global parameter with the same **id**. They are also configured in the **Process**, but changes are copied to all other processes that also use this parameter. **Global parameters** are matched by their parameter ID, and marked with a globe icon in the **parameter configuration** panel.

#### Conditional Parameters

**Conditional parameters** are parameters which are only configurable if a specific dependent parameter exists or has a certain value. These parameters are hidden until the dependent parameter meets requirements of the condition.

#### Link Expressions

Link expressions can be used on all process parameters (as well as for endpoint configuration, inside configuration files, etc.). The **BDeploy** UI provides content assist for link expressions once a parameter editor is switched to expression mode, which is done using the toggle in front of each parameter. Each parameter is _either_ a plain value or a link expression, depending on the toggle.

:::{align=center}
![Content Asssist for Link Expressions](/images/Doc_InstVar_InParameter.png){width=480}
:::

The content assist will propose categories of [Variable Expansions](/power/variables/#variable-expansions) first, and, once the category is determined, will continue to provide more detailed assistance.

See [Variable Expansions](/power/variables/#variable-expansions) for more details.

#### Command Line Preview

A preview of the command that is executed to launch this process can be viewed by expanding the **Command Line Preview** section. The preview is especially useful in case of custom parameters to ensure that they are added in the correct order.

:::{align=center}
![Preview Command Line with Custom Parameter](/images/Doc_InstanceConfigPreview.png){width=480}
:::

#### Allowable Configuration Directories

!!!info Note
This section applies to `CLIENT` applications only.
!!!

`CLIENT` applications do not receive any [Configuration Files](/user/instance/#configuration-files) from the instance configuration by default. The reason for this is simple: security. Client PCs are typically less secure than the server running `SERVER` applications. Regardless, `CLIENT` applications may _require_ configuration files. It is therefore possible to **whitelist** certain configuration directories in the configuration of _each_ `CLIENT` process in the instance configuration.

:::{align=center}
![Allowed Configuration Directories](/images/Doc_InstanceConfig_ClientConfigDirs.png){width=480}
:::

Select one or more directories to have those installed to the PC running the `CLIENT` application in addition to server nodes.

### Instance Variables

**Instance Variables** have been introduced along with their even more global counterpart [System Variables](/user/instancegroup/#system-variables). **Instance Variables** complement the concept of [Global Parameters](/user/instance/#global-parameters). They offer a more flexible - and along with [System Variables](/user/instancegroup/#system-variables) also a more powerful - way of achieving the same result in many cases, and allow for even more use cases.

**Instance Variables** come in especially handy when used outside of process parameters, e.g. in configuration files, or process endpoints. They are inherently shared between all elements of an instance.

To add **Instance Variables**, use the [ **Instance Variables...** ] option in the **Instance Settings** panel. Add a new **Instance Variable** using the [ **+** ] button in the panels toolbar.

:::{align=center}
![Creation of a new Instance Variable](/images/Doc_InstVar_Plain.png){width=480}
:::

The value of an **Instance Variable** can not only be a plain value of the selected type, but it can also be a _link expression_, which is a value consisting of a combination of plain text components and one or more [Variable Expansions](/power/variables/#variable-expansions). In this mode, the editor will provide content assist for expansions and a dedicated variable list popup.

:::{align=center}
![Creation of a new Instance Variable (Link Expression)](/images/Doc_InstVar_Link.png){width=480}
:::

Once created **Instance Variables** can be referenced from all other [Link Expressions](/user/instance/#link-expressions), e.g. on process parameters, configuration files, etc.

### Configuration Files

The **configuration files** of all **Processes** of an **Instance** are maintained together in one place. It can be opened by clicking on the [ **Configuration Files** ] button in the **Instance Settings** panel. The initial set of **configuration files** is derived from the default set delivered with the product; see [`product-info.yaml`](/power/product/#product-infoyaml).

:::{align=center}
![Instance Configuration Files](/images/Doc_InstanceConfigFiles.png){width=480}
:::

The **configuration files** of an **Instance** can be compared with the original **configuration file templates** of the **Product** at any time, an according up to date hint is shown next to each configuration file if applicable. The [ **Compare with product template** ] button starts the comparison. Files which are present in the **product** but not in the **instance configuration** are marked - the same is true the other way around.

New configuration files can be be created using the [ **+** ] button. Prompt for a file name and an optional initial content to upload. When dropping a file onto the drop zone, the filename is updated automatically to match the dropped file.

The [ **Edit** ] button on each file can be used to edit the content of the file using an online rich editor.

:::{align=center}
![Editing an Instance Configuration File](/images/Doc_InstanceConfigFilesEdit.png){width=480}
:::

Online editing is only possible for text files. Binary files like ZIP, PDF, etc. cannot be edited online. However, it is possible to download them, modify them locally, and then reupload (and thus replace) them.

[Variable Expansions](/power/variables/#variable-expansions) can be used in configuration files. The variables will be expanded when the file is written on disc on the target node. The editor for configuration files provides content assist for [Variable Expansions](/power/variables/#variable-expansions) on [ **CTRL** ] + [ **Space** ] if the current word in the editor contains `{{`.

!!!info Note
Changes done in configuration files must be **saved** and they result in a new **instance version** that must be **installed** and **activated** so that the changes have an impact, much the same as **any** other change in the [Instance Configuration](/user/instance/#instance-configuration).
!!!

### Change Product Version

**Instances** are based on a **product version**. While the **Product** of the **Instance** cannot be changed afterwards, the **Version** can be chosen from the available **product versions** (upgrade to a newer version / downgrade to an older version).

If there's a newer **product version** available (newer than the one that is configured for the latest **instance version**), a notification is shown in the [Instance Configuration](/user/instance/#instance-configuration) pages toolbar.

:::{align=center}
![Update Notification](/images/Doc_InstanceProductUpdateAvail.png){width=480}
:::

Clicking on the notification opens the **Update Product** panel. The same panel can also be opened by clicking on the [ **Update Product Version** ] button in the **Instance Settings** panel.

:::{align=center}
![Change Current Product Version](/images/Doc_InstanceProductUpdate.png){width=480}
:::

Changing the version can be done by clicking on the [ **Upgrade** ] or [ **Downgrade** ] button displayed at the right side of the product version. Changing the product version will trigger an automated migration. This migration will also validate changes. It gives **hints** about potentially relevant (but not blocking) changes and validation issues in case the migration could not be performed fully automatically. You then have the chance to fix issues manually before saving the resulting **instance version**.

:::{align=center}
![Product Update Hints](/images/Doc_InstanceProductUpdateHints.png){width=480}
:::

!!!info Note
Changing the **product version** will never change the **Configuration Files** of the **Instance**. In case configuration file templates change from one product version to the other, an update hint will be shown. You can then manually update configuration files as needed, see chapter [Configuration Files](/user/instance/#configuration-files).
!!!

### Banner Message

A banner message can be created for an **Instance**, which is displayed very clearly at the top of the overview dialog. You can choose from a series of predefined colors, so that depending on the urgency or content of the message a suitable color can be selected.

:::{align=center}
![Instance Banner Configuration](/images/Doc_InstanceBannerConfig.png){width=480}
:::

Banner messages are maintained on instance level and are not versioned, i.e. they are independent of instance versions. Therefore they outlast configuration changes of an instance and can be configured without saving the [Instance Configuration](/user/instance/#instance-configuration).

:::{align=center}
![Instance Banner Configuration](/images/Doc_InstanceBanner.png){width=480}
:::

The banner is shown in the [Instance Overview](/user/instancegroup/#create-new-instances) (as tooltip on the instance), in the [Instance Dashboard](/user/instance/#instance-dashboard) and in the [Instance Configuration](/user/instance/#instance-configuration) pages.

### Import/Export

Instance versions can be exported and downloaded from the [Instance History](/user/history/#instance-history). This will download this specific instance version's raw data as a _ZIP_. The _ZIP_ can be re-imported using the **Instance Settings** panel to create a new **instance version** which has that exported instances content.

!!!warning Warning
This mechanism allows access to the internal data structures of **BDeploy**. Great care has to be taken to not damage any of the data when manipulating the content of the _ZIP_ manually.
!!!

### Application Templates

A product may contain **Application Templates**. These are pre-defined configurations for applications, resulting in a more complete process configuration when added to the target node.

We saw earlier how to [add applications using templates](/user/instance/#instance-configuration). Depending on the selected template, you may be prompted to enter the required template variable values.

:::{align=center}
![Add Process Template](/images/Doc_InstanceAddProcessTemplVars.png){width=480}
:::

The process configuration is created from the application template using the given variable values.

!!!info Note
You will notice that the name of the process now matches the name of the template, not the name of the underlying application.
!!!

### Instance Templates

A product can define and include **Instance Templates**. These templates can be applied on an instance (e.g. after creating a new instance). They can define processes just like **Application Templates**, in fact they typically include existing **Application Templates**.

!!!info Note
The advantage of an **Instance Template** is that it can contain more knowledge of how processes need to be set up to work together, wheras **Application Templates** define configuration for a single application.
!!!

:::{align=center}
![Instance Templates](/images/Doc_InstanceTemplates.png){width=480}
:::

!!!info Note
**Instance Templates** can also be applied to instances which already have configured processes.
!!!

Selecting a template (here: _Default Configuration_) will show a list of _groups_ defined in the template. These _groups_ can be assigned to compatible nodes - groups containing server applications to server nodes, and groups containing client applications to the virtual _Client Applications_ node. Selecting _(skip)_ as target node will skip the processes in this group.

:::{align=center}
![Instance Templates Node Assignment](/images/Doc_InstanceTemplatesNodes.png){width=480}
:::

When creating configurations on a `SERVER` node, applications will be added matching the nodes OS. If a server application is included in a group which is not available for the target OS, you will receive an according message.

When creating configurations for a `CLIENT` group, applications are added to the _Client Applications_ virtual node, one for each OS supported by the application.

Next you will be presented with the template variables which need to be provided.

:::{align=center}
![Instance Templates Variable Assignment](/images/Doc_InstanceTemplatesVars.png){width=480}
:::

Clicking [ **Confirm** ] will create the processes defined in the template. The configuration will **not** be saved automatically in order to allow further tuning of the configuration. Applying **templates** can be undone by clicking [ **Undo** ] like any other change.

:::{align=center}
![Applied Instance Templates](/images/Doc_InstanceTemplatesDone.png){width=480}
:::

### Network Ports

The **Manage Network Ports** panel can be reached from the **Instance Settings** panel. This panel provides a concise overview of all ports (`CLIENT_PORT`, `SERVER_PORT` and `URL` parameters) used in the **Instance**.

The [ **Shift Ports** ] action allows to bulk edit selected port parameters and shift them upwards or downwards by a given offset.

The [ **Export CSV** ] action allows to export a CSV list of all ports configured in the system. This can be used to forward information to external partners, e.g. for further firewall configuration, etc.

### Process Control Groups

**Processes** can be grouped into **Process Control Groups**. Those groups dictate the order in which processes are started and stopped, as well as whether or not process startup is awaited.

Each **Process Control Group** has configurable behavior for contained processes regarding starting and stopping. Groups are processed in the configured order when starting, and the reversed configured order when stopping. Processes **within** each group are processed according to the groups configuration. Each group has its own configuration for startup and stop behaviour.

Possible values for the **Start Type** and **Stop Type** handling configuration are:

- `SEQUENTIAL`: Each **Process** in the group is handled (started/stopped) sequentially. Any potential waiting (for startup and or shutdown, depending on the capabilities of the process) is done before proceeding to the next **Process** or **Process Control Group**. The precise behavior is determined by the **Start Wait** configuration (see below).
- `PARALLEL`: Each **Process** in the group is handled (started/stopped) in parallel. Only once all **Process** operations within the group have finished, the group is considered finished and processing continues with the next **Process Control Group**. Be aware that in conjunction with `CONTINUE` wait mode for process startup, this will make a group complete immediately and continue on to the next group immediately as well.

Additionally, each **Process Control Group** can be configured to wait for process startup, i.e. startup probes. Each **Process Control Group** has a **Start Wait** configuration:

- `CONTINUE`: When starting a **Process** in the group, do not wait for its startup to be complete, instead move on to the next process immediately. This makes the startup handling types (`SEQUENTIAL`, `PARALLEL`) essentially irrelevant, as processes are typically created so fast that it does not make any difference.
- `WAIT`: When starting a **Process** in the group, wait for its startup to be complete before continuing to the next **Process**. The startup of the process is considered complete once the **Startup Probe** becomes live. If no **Startup Probe** is configured, this mode is equal to `CONTINUE`.
- `WAIT_UNTIL_STOPPED`: When starting a **Process** in the group, wait for it to finish before continuing to the next **Process**. A process is considered finished once its state is **STOPPED**.


:::{align=center}
![Add Process Control Groups](/images/Doc_InstanceConfigAddProcessControlGroup.png){width=480}
:::

Each `SERVER` node automatically uses **Process Control Groups** and is initialized with a 'Default' group. Adding a new **Process** to a **Node** will always add that **Process** to the last **Process Control Group**. It can then be moved by drag and drop to another **Process Control Group** if required.

:::{align=center}
![Edit Process Control Groups](/images/Doc_InstanceConfigEditProcessControlGroup.png){width=480}
:::

**Process Control Groups** themselves can be re-ordered using the [ **Move Up** ] and [ **Move Down** ] buttons.

### Deleting an Instance

An instance can be deleted from the **Instance Configuration** page, in the **Instance Settings** panel.
