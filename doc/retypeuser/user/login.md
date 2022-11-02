---
order: 9
---
# Login

Each user needs to authenticate with a username and password in order to get access to all parts of the system. An anonymous access is not available.

:::{align=center}
![BDeploy Login](/images/Doc_Login.png){width=480}
:::

After a successful login the main page is shown with all **Instance Groups** that the user is permitted to see, either through global or scoped permission.

The main menu - which is always present on the left side of the screen - can be expanded using the _hamburger_ button. This will provide a more readable version of the main menu. The content of the main menu depends on the current application context. This means that items will be added whenever more context is available, e.g. when clicking on an instance group, and removed when leaving this context.

:::{align=center}
![BDeploy Main Menu](/images/Doc_MainMenu.png){width=480}
:::

Clicking on the user menu in the top-right corner provides access to various user related actions and information.

:::{align=center}
![BDeploy User Settings](/images/Doc_UserSettings.png){width=480}
:::

!!!info Note
Users which are authenticated against an external system (e.g. LDAP) are not able to edit their user information (including password) through the user menu. Updates have to be performed on the controlling system (e.g. LDAP server) in this case.
!!!