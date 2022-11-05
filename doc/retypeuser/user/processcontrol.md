---
order: 4
icon: dependabot
---

<style>
    .t1 th:first-child {
        width: 40px;
    }
</style>

# Process Control

For the active **instance version**, the **process control** of each **Process** can be displayed by clicking on it in the [Instance Dashboard](/user/instance/#instance-dashboard). The upper part of the **Process Control** panel shows the current **status**. Below this, the control elements for _starting_, _stopping_ or _restarting_ the process can be found.

:::{align=center}
![Process Control Panel](/images/Doc_DashboardProcessControl.png){width=480}
:::

## Process Bulk Control

In addition to the actions for individual processes, the [Instance Dashboard](/user/instance/#instance-dashboard) offers **bulk control** actions from its toolbar, which allow to control all processes of **start type** `INSTANCE`, or multiple selected processes (of any start type except `MANUAL_CONFIRM`) at once.

:::{align=center}
![Bulk Process Control](/images/Doc_DashboardBulkProcessControl.png){width=480}
:::

!!!info Note
Using the **bulk control** actions with a single process selected is the same as using the individual actions on the process details panel.
!!!

## Process Status

The process status is visualized using an icon for each process. Hover over the icon to get details about the current status.

:::t1
Icon   | Description
---    | ---
![](/images/ManualDoc_ProcessStopped.png){width=32} | Process is **stopped**.
![](/images/ManualDoc_ProcessStarting.png){width=32} | Process is **starting**.
![](/images/ManualDoc_ProcessRunning.png){width=32} | Process is **running**.  If the process defined a _startup probe_ (see [`app-info.yaml`](/power/product/#app-infoyaml)), this status is reached as soon as the startup probe indicates a successful startup of the application. Otherwise - without probe - the process immediatly reaches the **running** state automatically.
![](/images/ManualDoc_ProcessStopPlanned.png){width=32} | Process is **running** but **stopping** has been **requested** by the user.
![](/images/ManualDoc_ProcessLifenessFailed.png){width=32} | Process is **running**, but a defined _lifeness probe_ (see [`app-info.yaml`](/power/product/#app-infoyaml)) indicates that the process has a problem.
![](/images/ManualDoc_ProcessCrashed.png){width=32} | Process **crashed** unexpectedly. **Process Control** scheduled an automatic restart. No interaction required.
![](/images/ManualDoc_ProcessCrashedPermanent.png){width=32} | Process **crashed** unexpectedly. **Process Control** gave up restarting as the last 5 attempts failed. **Manual interaction required**.
:::

The life cycle of a process is visualized in the following state graph:

:::{align=center}
![Process State Graph](/images/BDeploy_Process_State_Graph.png){width=480}
:::

## Process Outdated

An warning message - **Outdated** - is displayed whenever one or more **Processes** are running in a version that is currently not **active**. This happens when **deploying** a new version while **Processes** are still running. 

:::{align=center}
![Outdated Process](/images/ManualDoc_ProcessOutdated.png)
:::

In principle this is nothing to worry about. It is just a remainder that the configuration has changed. The **Processes** will remain running in their current version until they are actively restarted. The message cannot be confirmed / closed as it automatically disappear once all **Processes** are running in the **active** version.

## Process Start Type

The **Start Type** of a **Process** can be configured in the **Process Configuration** dialog. The available options are depending on the **Application**. That means the publisher of an **Application** defines which **Start Types** are supported. The following types are available:

Name   | Description
---    | ---
`MANUAL` | Process must be started manually. No automatic startup will be done.
`MANUAL_CONFIRM` | Process must be started manually and an additional confirmation is required. These kind of processes **cannot** take part in bulk control actions.
`INSTANCE` | Process will be started automatically **if** the _Automatic Startup_ flag of the **Instance** is set. 

It is a common requirement that certain **Processes** of an **Instance** should be automatically started whenever the **BDeploy** server itself is started. To accomplish that, the _Automatic Startup_ flag of the **Instance** must be set. This can be done in the [Instance Configuration](/user/instance/#instance-configuration). Additionally the **start type** of the **Process** must set to `INSTANCE`. This can be done in the **parameter configuration** of the **Process**.

**Processes** that are executing actions that cannot be reverted or that are potentially dangerous in productive environments (dropping a database, deleting files) should be configured with the start type `MANUAL_CONFIRM`. Doing that results in an additional popup dialog that enforces the user to enter the name of the **Process** before it is started. The idea is, that the user takes an additional moment to ensure that he is really starting the desired **Process**.

:::{align=center}
![Manual Confirmation On Startup](/images/Doc_DashboardProcessManualConfirm.png){width=480}
:::

## Startup and Shutdown Order

The **process control** starts the processes in the order as they are defined in the **process configuration** dialog. **Processes** can be grouped in **Process Control Groups**, which dictate the exact behavior for a group of **Processes**. When [ **Start Instance** ] is invoked then all processes with startup type `INSTANCE` are started in the defined order - potentially waiting for a **startup probe** after launching the command. When [ **Stop Instance** ] is invoked then all running processes are stopped according to the group strategies. The order is reversed during the stop operation. That means the last process is stopped first and the first process is stopped at last (in `SEQUENTIAL` mode). The next process is stopped only when the previous is terminated. The same holds for **Process Control Groups**; groups are processed one after another when starting, and in reverse order when stopping.

The exact meanings of the different configuration options is described in the [Process Control Groups](/user/instance/#process-control-groups) section.

!!!info Note
Bulk control of selected processes uses the exact same [Process Control Groups](/user/instance/#process-control-groups) configuration as the [ **Start Instance** ] and [ **Stop Instance** ] actions. Only selected processes will be affected, as opposed to the whole instance. The [ **Start Instance** ] and [ **Stop Instance** ] will only affect processes with start type `INSTANCE`, bulk control can also affect processes with start type `MANUAL` in addition (when selected).
!!!

## Keep Alive

If the **Keep Alive** flag for a **Process** is configured then the **process control** restarts it when it crashes unexpectedly. The first restart attempt is immediately executed after the process terminates. Subsequent attempts are delayed. That means the **process control** waits a given time period until the next start attempt is executed. Such a situation is visualized in the **Process** state.

:::{align=center}
![Crashed Server Process (temporarily)](/images/Doc_DashboardProcessCrash.png){width=480}
:::

The **process control** will give up restarting a process after a configurable number of unsuccessful restart attempts. Such a situation is visualized in **Process** state. This icon means that the user has to manually check why it is failing and restart it if desired.

:::{align=center}
![Crashed Server Process (permanently)](/images/Doc_DashboardProcessCrashPermanent.png){width=480}
:::

## View stdout / stderr

Clicking on the terminal icon displayed below the process control actions will open a live stream of the **stdout** as well as **stderr** stream of the running **Process**. This allows a quick health check to ensure that everything is as expected.

:::{align=center}
![Show and Follow Process Output](/images/Doc_DashboardProcessConsole.png){width=480}
:::

## Process Port Status

The applications server ports (if any are defined) and their state on the target node can be viewed by clicking on the [ **Process Port Status** ] below the process controls. Each parameter of type `SERVER_PORT` is displayed here, with its description and configured value. Each port has a **status**. This **status** determines whether the port has the **expected** state on the server. This means that the port is **closed** if the process is **not** running, and vice versa. **BDeploy** cannot check whether the port was opened by the correct application.

## Native Processes

Clicking on the [ **Native Processes** ] below the process control will open a panel showing all operating system processes that associated with this **Process**.

## Parameter Pinning

Parameter Pinning allows you to pin "important" paramteres, so that they are not only visible during configuration of a process, but also on the process panel on the dashboard. This provides quick access to information about a process. A parameter can be pinned by clicking the little pin icon which appears on each parameters editor when hovering over it.

:::{align=center}
![Data Files](/images/Doc_InstanceConfigParameterPin.png){width=480}
:::

The pinned parameter is visible after saving, **installation** and **activation** of the **Instance** version where the parameter has been pinned.

:::{align=center}
![Data Files](/images/Doc_DashboardPinnedParameter.png){width=480}
:::

## Data Files

The **Data Files** page lists all files that are stored in the data directory of each node. Files can be downloaded or opened directly in the the UI where possible. The table is by default sorted by the last modification timestamp. Thus the newest files displayed first.

:::{align=center}
![Data Files](/images/Doc_DataFiles.png){width=480}
:::

!!!info Tip
The [ **delete** ] button can be used to delete a file. This requires administrative permissions on the server or the instance group.
!!!

Clicking a file will view the file, the [ **Follow** ] toggle allows to grab new output as it is written on the server.

:::{align=center}
![View Data File](/images/Doc_DataFilesView.png){width=480}
:::

Data Files can also be manually added and edited online. Use the [ **Add File** ] button, and the [ **Edit** ] button per file to do so.

:::{align=center}
![Edit Data File](/images/Doc_DataFilesEdit.png){width=480}
:::
