---
order: 3
---
# Instance History

Whenever saving the [Instance Configuration](/user/instance/#instance-configuration), **BDeploy** creates a new **instance version**. Each historic version is avilable from the [Instance History](/user/history/#instance-history). You can **view** all those changes, **who** made them and **when** they were made here.

There are three types of history entries: _Creation_, _Deployment_ and _Runtime_ events. Each event has a title, the corresponding **version**, as well as the **time** the event happened.

!!!info Note
_Deployments_ and _Runtime_ events are not displayed by default. You can enable them using the toolbar buttons.
!!!

!!!info Note
If you haven't made any changes, only one entry will be shown: _Version 1: Created_. This is the initial (empty) **instance version**.
!!!

:::{align=center}
![Instance history](/images/Doc_History.png){width=480}
:::

To see details for a certain **history entry**, click it. This will open the details panel. This panel offers different kinds of information and actions depending on the type of history entry.

_Creation_ events are special in that they represent a specific **instance version**, and allow you to **install**, **activate** and **compare** them to other **instance versions**. Given the proper permissions, you can also **delete** them - which will remove this specific version completely from the server, no restoring possible.

**Instance versions** can also be **exported** from the details. The export produces a _ZIP_ file which contains **all** data that represents an instance version. This can be used to create a new **instance version** on a **different** instance from the current configuration of **this** instance by importing the _ZIP_ in the [Instance Configuration](/user/instance/#instance-configuration) of the other instance.

:::{align=center}
![History Entry](/images/Doc_HistoryEntry.png){width=480}
:::

You can compare either to the **current** version, the **active** version or any chosen version available from the history.

:::{align=center}
![Compare Versions](/images/Doc_HistoryCompare.png){width=480}
:::

## Creation Events

Each change that leads to a new **instance version** is a _creation_ event. This typically is saving the [Instance Configuration](/user/instance/#instance-configuration).

## Deployment Events

_Deployment_ events are: `INSTALLED`,`ACTIVATED`,`DEACTIVATED`,`UNINSTALLED`.

_Deployment_ events will show up once you **enabled** them using the toolbar. _Deployment_ events allow tracking of who deployed what and when.

:::{align=center}
![Runtime Events](/images/Doc_HistoryDeployment.png){width=480}
:::

## Runtime Events

_Runtime_ events are: `STARTED`, `STOPPED`, `CRASHED`, `RESTARTED`, `CRASHED PERMANENTLY`.

_Runtime_ events will show up once you **enabled** them using the toolbar. _Runtime_ events can be used to track **process** state history. You will be able to see who started/stopped **processes** and when. Events not induced by a user action are shown using _BDeploy System_ as user, e.g. when a **proces** is restarted after a crash.

!!!info Note
The **Process ID** and **Exit Code** will also show up given that the information was available at the **moment** the event happened. This can be influenced for instance by a **restart** of **BDeploy** itself, in which case limited information about running **processes** is available.
!!!

:::{align=center}
![Runtime Events](/images/Doc_HistoryRuntime.png){width=480}
:::
