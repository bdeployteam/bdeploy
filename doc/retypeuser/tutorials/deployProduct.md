---
order: 1
icon: repo-push
---

# How can I deploy a new product version?

Upgrading the **Product Version** that is used in an **Instance** requires two distinct steps:

1. Uploading the new **Product Version**
2. Upgrading the **Instance**

## Upload Product Version

Uploading a new **Product Version** is done in the **Products** page of the **Instance Group**:

1. Click on the desired **Instance Group**
2. Click on [ **Products** ] button in the main menu.
3. Click on the [ **Upload Product** ] button in the toolbar.
4. Select the ZIP archive containing the new **Product Version**. This archive is typically created by your **Build** tool.
5. The product is uploaded, processed and will appear in the list of available products once successfully imported.

The **Product Version** is now available and can be used in all **Instances** of this **Instance Group**.

## Import Product Version

You can import a new **Product Version** from a software repository.
It is done in the **Products** page of the **Instance Group**

1. Click on the desired **Instance Group**
2. Click on [ **Products** ] button in the main menu.
3. Click on the [ **Import Product...** ] button in the toolbar.
4. Select software repository.
   :::{align=center}
   ![Choose Repo](/images/Doc_ImportProduct_SelectRepo.png){width=480}
   :::
5. Select product.
   :::{align=center}
   ![Choose Product](/images/Doc_ImportProduct_SelectProduct.png){width=480}
   :::
6. Select product version.
   :::{align=center}
   ![Choose Product Version](/images/Doc_ImportProduct_SelectVersion.png){width=480}
   :::
7. Click import button.
   :::{align=center}
   ![Press Import button](/images/Doc_ImportProduct_PanelFilled.png){width=480}
   :::
8. The product will appear in the list of available products once successfully imported.
   :::{align=center}
   ![Product Version imported](/images/Doc_ImportProduct_Success.png){width=480}
   :::

The **Product Version** is now available and can be used in all **Instances** of this **Instance Group**.

## Upgrade Instance

Changing the **Product Version** of an **Instance** is done in the **Instance Configuration** dialog:

1. Navigate back to the **Instance Overview** dialog by clicking the [ **Instances** ] button in the main menu.
2. Open the desired **Instance** by clicking it.
3. Each node displays the currently used product version. In case an update is available, a hint is shown in form of a small icon.
4. Clicking on this hint opens the **Instance Configuration** page along with the **Update Product** panel.
   a. You can also navigate to the **Instance Configuration** page by clicking the [ **Instance Configuration** ] button in the main menu. This page shows a more prominent hint about the new **Product Version**.
5. Click on the [ **Update** ] button of the desired product version
6. The update to the new **Product Version** is performed and validated on the server. It is - however - not saved, so you can undo/redo, perform changes - just like with any other configuration change.
7. Update _hints_ may be shown in a separate section which can be dismissed. Those are purely informative things which point to actions performed during the update which may or may not have an impact on the software.
8. _Validation Issues_ may be shown in a separate section if they occur due to the update. This can be a wide variety of things - all of them require manual intervention before you are able to save the **Instance Configuration**.
9. Adopt the configuration of the **Processes** as required
10. Click on **Save** in the top right corner to save this version

The **Instance** is now based on the new **Product Version**. Remember that the new **Instance Version** must be **Installed** and **Activated** so that the changes have an impact - clicking **Save** will bring you to the **Instance Dashboard** from where this can be performed directly.. Please read the [Process Configuration](/tutorials/processconfig/#how-can-i-change-a-process-configuration) tutorial about more details how to do that.
