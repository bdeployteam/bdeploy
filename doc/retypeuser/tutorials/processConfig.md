---
order: 3
---
# How can I change a process configuration?

Changing the configuration of a **Process** is done in the **Process Configuration Panel**:

1. Start from the **Instance Group Overview** page, which is the first page right after logging in.
2. Click on the desired **Instance Group**.
3. Click in the desired **Instance**.
4. Click on the [ **Instance Configuration** ] button in the main menu.
5. Click on the **Process** you wish to configure.
6. Click on **Configure Parameters...** in the process edit panel.
7. Modify the **Process** as desired: Rename, Change Parameters, Add / Remove Parameters
8. Click on **Apply** in the top right corner if you are finished with this **Process**
9. You can undo/redo changes to multiple processes and/or instance configurations. You can also view any local changes using the [ **Local Changes** ] button.
10. Repeat the steps 5-8 with every **Process** that should be adopted.
11. Click on **Save** in the top right corner.
 
The changes are now saved but they are not yet **active**. The new version must now be **Deployed** and all **Processes** must be restarted so that the changes have an effect.

1. Saving **Instance Configuration** will automatically redirect you to the **Instance Dashboard**. You can also access the dashboard from the **Instance Overview** page by clicking an **Instance**.
2. If the current **Instance Version** is _not_ the currently active one, a banner will show a hint and the required buttons to **install** and **activate** the current version.
    a. Access to _older_ **Instance Versions** is provided from the [ **Instance History** ] button in the main menu.
3. Click on **install** and wait for the operation to complete.
4. Click on **activate**, this operation is typically very fast.
5. A hint (`OUTDATED`) will be displayed if a **Process** is running in an older version, for instance the version which was active until moments ago.
6. Stop the process you want to update by clicking on the **Process**. Click on the [ **Stop** ] button.
7. Start the processes by click on the [ **Start** ] button.
8. Repeat the steps 5-6 with each process that should be running from the new, now active version.
9. The hint (`OUTDATED`) disappears once all processes are running in the active version.
10. It can be desirable and OK to **not** restart certain processes, and leave them in the `OUTDATED` state. This highly depends on the software being deployed.

The configuration changes are now live and all **Processes** are running again. The [Process Settings](/user/instance/#process-settings) and the [Process Control](/user/processcontrol/#process-control) chapters provide more details about the step outlined in this tutorial.
 