---
order: 5
---
# Deployment

The process of making one **instance version** up and running is called **deployment**. This includes the steps **installation**, **activation** and **process control**.

A **Deployment** can be performed on the **current** **instance version** or on a **historic** **instance version**. All **instance versions** can be deployed using the [Instance History](/user/history/#instance-history) page - select a version and use the according actions. For the most common case of deplying the **current** **instance version**, the **deployment** is performed by clicking the [ **Install** ] and [ **Activate** ] buttons on the [Instance Dashboard](/user/instance/#instance-dashboard) after configuring an **instance**.

## Installation

All available **instance versions** can be installed via the action [ **Install** ] on the [Instance History](/user/history/#instance-history) page or the [Instance Dashboard](/user/instance/#instance-dashboard). Clicking on that button will transfer all files that the application requires to the configured target nodes. More specific that means the applications are extracted from the internal storage that **BDeploy** is using and written to the hard disk. 

Installed versions occupy additional disk space on the target node. Applications are installed in a shared **pool** directory on the target node. Thus multiple different **processes** referring to the same **application** are written only once to the hard disk. This minimizes the required disk space and leads to faster **installation** times as only the first **process** needs to be be written to the hard disk. The kind of pooling used can be configured in [app-info.yaml](/power/product/#app-infoyaml) by the product vendor.

Multiple **instance versions** can be installed simultanously. This allows very quick switching beteen them, for instance in case of a failed upgrade to immediately switch back to the previous software version.

## Activation

Exactly one of the installed **instance versions** can be marked as _active_ with the action [ **Activate** ]. The **process control** always refers to the _active_ version. Versions that are just installed but not active cannot be started. Only **processes** from the **active** version can be started. The [Instance Dashboard](/user/instance/#instance-dashboard) allows control of the **active** versions **processes**.

Marking a desired version as _active_ does not require to stop all running **processes**. An _outdated_ hint is displayed on the [Instance Dashboard](/user/instance/#instance-dashboard) when processes from _non-active_ versions are still running. They can be manually stopped if desired and then started from the currently active version.
