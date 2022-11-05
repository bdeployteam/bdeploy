---
order: 4
icon: table
---

# Administration

There are several components in the Web UI which allow maintenance of **BDeploy**. These are not to be used during _normal_ operation by users. They _are_ required during setup and to maintain current software versions of **BDeploy** itself if not done using the [`bdeploy remote-master`](/experts/cli/#bdeploy-cli) CLI.

The administration dialogs are grouped into a Configuration section  and a Housekeeping and Maintenance section.

## Settings

In the Settings area some global settings can be made.

### General

:::{align=center}
![BDeploy Settings](/images/Doc_Admin_Settings.png){width=480}
:::

_Gravatar_ support can be enabled here. If disabled, a generic icon is used for all useres. Otherwise, **BDeploy** shows the _Globally Recognized Avatar_ (or a substitute image) wherever a user icon is displayed in a dialog. Visit [gravatar.com](https://gravatar.com) for more information.

**BDeploy** supports both built-in and external authentication mechanims. The built-in user authentication can be disabled completely in the settings.

!!!info Note
At least one login method must be configured. Disable local accounts only if it is ensured that at least one administrator account exists via external authentication!
!!!

### OpenID Connect

OAuth2 with OpenID Connect support is available if enabled and configured. The URL must be the token endpoint of the OpenID Connect provider. For example, the URL may look something like this on Keycloak, depending on configuration:

```
https://auth.example.com/auth/realms/realmName/protocol/openid-connect/token
```

The Client ID and Client Secret are used to authenticate with the OpenID Connect provider, and need to be obtained from the OpenID Connect provider.

### LDAP Auth.

On the **LDAP Auth** tab, you can configure a list of LDAP servers which are queried when authenticating a user. Use drag and drop to to specify the sort order in which the LDAP servers are queried for users that log on.

:::{align=center}
![BDeploy LDAP Servers](/images/Doc_Admin_Ldap_Servers.png){width=480}
:::

!!!info Tip
Technical experts can use the _Check_ action to test a single LDAP server entry: **BDeploy** tries to establish a connection to the configured server. Some logging information, esp. Java Exceptions are shown in a popup window. Simmilarly, the _Test Auth._ action can be used to trace the entire authentication of a user.
!!!

**BDeploy** uses simple bind to authenticate users. First, a simple bind is made with the configured _User_. This user must have permissions to list other users that should be able to log into **BDeploy**. This bind is used to query for a user where the `Account User Field` matches the user name to be authenticated. This can be any field like `user`, `sAMAccountName`, or even `mail` if you want users to log on using their E-Mail Address. Once the user to log on is found, it's distinguished name is used to perform another simple bind using the provided password. Once this succeeds the user is authenticated and an according record is created in **BDeploy**. From that point on, permissions can be granted to this user.

:::{align=center}
![BDeploy LDAP Server Configuration](/images/Doc_Admin_Ldap_Server_Config.png){width=480}
:::

#### LDAP Server Properties

The following properties can be configured for each LDAP Server:

Property | Description
---      | ---
Server URL | The URL of the LDAP server. Both `ldaps://` (with a proper certificate on the server) and `ldap://` are supported. `ldaps://` should be preferred where possible. Self-signed certificates are currently not configurable inside **BDeploy** (although they can be configure on the operating system).
Description | Free text to describe the entry
User | The user which is used to query other users on the LDAP server (aka _bind user_)
Password | The password for the _User_ which is used to query other users on the LDAP server.
Account Base | Root of the LDAP tree containing all user accounts to query. Typically in the form of `dc=domain,dc=com`.
Account Pattern | A partial LDAP query expression. Multiple filters can be written one after another. The final LDAP query is built by prepending `(&`, and appending a filter which queries the configures _Account User Field_ for the given user. This means that a pattern `(field1=value1)(field2=value2)` will result in a query like `(&(field1=value1)(field2=value2)(sAMAccountName=<GIVEN USER>))`.
Account User Field | Specifies the field which must match the login name when querying for the user.
Account Name Field | The field which should be used as source for the _Full Name_ of the user, which is used as a display name in [User Accounts](/experts/system/#user-accounts) management.
Account E-Mail Field | The field which should be used as source for the users _E-Mail Address_. This is used for instance to query _Gravatar_ if _Gravatar_ support has been enabled in the [General Settings](/experts/system/#general).
Follow Referrals | Specifies whether the authentication process should follow referrals or not.

#### LDAP Certificate Trust

!!!info Note
This section currently only applies to Linux installations.
!!!

If the configured LDAP server uses official certificates which are not trusted by the current JVM's default trust store, you can alternatively configure the **BDeploy** service to use the system _cacert_ trust stores, which are typically available at a location like `/etc/pki/ca-trust/extracted/java/cacerts`. This path may be different on different Linux distributions.

To use this trust store, you need to add these parameters to the command line of **BDeploy**:

```
-Djavax.net.ssl.trustStore=/etc/pki/ca-trust/extracted/java/cacerts -Djavax.net.ssl.trustStoreType=jks -Djavax.net.ssl.trustStorePassword=changeit
```

You can do so by means provided by the Linux distribution, e.g. directly editing the `bdeploy.service` systemd service file or setting `BDEPLOY_OPTS` environment variable to that value either globally or in a service environment file.

### Global Attributes

In the _Global Attributes_ tab, globally available attributes for **Instance Groups** can be maintained. Global attributes can be used to maintain additional information for **Instance Groups**, which is then used as an additional grouping or sorting criteria.

:::{align=center}
![BDeploy Global Attributes](/images/Doc_Admin_Global_Attributes.png){width=480}
:::

### Plugins

The _Plugins_ tab can be used to manage the plugins known by **BDeploy**. Plugins that are currently loaded can be stopped here. Via the _Upload Plugin_ action global plugins can be uploaded. Global plugins can also be deleted from the system here.

:::{align=center}
![BDeploy Plugins Maintenance](/images/Doc_Admin_Plugins.png){width=480}
:::

## User Accounts

The **User Accounts** dialog lists all users known in the system, regardless of whether they are local users or LDAP users.

:::{align=center}
![BDeploy User Accounts](/images/Doc_Admin_User_Accounts.png){width=480}
:::

Use the [ **Create User** ] button to create a local user.

:::{align=center}
![BDeploy User Accounts](/images/Doc_Admin_User_Accounts_Add.png){width=480}
:::

Once a **User** is available, you can click it to open **User Details** panel where detail information is shown on top as well as the list of permissions. 

!!!info Note
To protect against accidental lockout from the system, the currently logged in user cannot be changed, disabled or deleted.
!!!

The [ **Deactivate Account** ] resp. [ **Activate Account** ] allows to deactivate/activate the selected user.

:::{align=center}
![BDeploy User Accounts](/images/Doc_Admin_User_Accounts_Inactive.png){width=480}
:::

The [ **Assign Permission** ] opens a popup for adding a permission entry. Global permissions as well as scoped permissions on **Instance Groups** can be maintained here.

:::{align=center}
![BDeploy User Accounts](/images/Doc_Admin_User_Accounts_Permissions_Add.png){width=480}
:::

Permission | Meaning
---        | ---
**CLIENT** | The **CLIENT** permission can be granted to allow access only to the client applications of **Instance Groups**. Users with global **CLIENT** permission can see the client applications of all **Instance Groups**.
**READ** | Only those **Instance Groups** for which a user has **READ** permission are displayed. Users with global **READ** permission can see all **Instance Groups**. The **READ** permission contains the **CLIENT** permission. The **READ** permission allows the user to read the **Instance Group** without making any modifications.
**WRITE** | The **WRITE** permission allows a user to maintain the contents of an **Instance Group** without modifying the **Instance Group** itself. The **WRITE** permission contains the **READ** permission. Users with global **WRITE** permission can maintain all **Instance Groups**.
**ADMIN** | The **ADMIN** permission contains the **WRITE** and **READ** permission and allows full access to an **Instance Group**. Users with global **ADMIN** permission have full access to all **Instance Groups** and additionally to the **BDeploy** system configuration.

The [ **Edit User** ] opens a popup for editing the main user properties and also for changing the password.

:::{align=center}
![BDeploy User Accounts](/images/Doc_Admin_User_Accounts_Edit.png){width=480}
:::

## Manual Cleanup

You can use the **Manual Cleanup** page from the **Administration** menu to trigger a manual cleanup of stuff that is not needed any more. There is no need to trigger this manually as a job is scheduled that performs the exact same operation every night: 

Target | Description
---    | ---
**Instances** with _Auto Uninstallation_ enabled | If the option _Automatic Uninstallation_ is enabled on an **Instance**, **Instance Versions** that are older than the activated and the previously activated **Instance Version** are uninstalled automatically.
**Instance Groups** with _Automatic Cleanup_ enabled | If the option _Auto Cleanup_ is enabled on an **Instance Group**, old **Product Versions** that are no longer in use by **Instances** of this **Instance Group** are deleted automatically. To avoid that a **Product** vanishes completely, the very latest **Product Version** always remains.
All Nodes | Delete **Manifests** that are not known by the master
All Nodes | Keep two **BDeploy Launcher** versions, delete all older versions.
All Nodes | Remove unused directories and files in the deployment (including pooled applications), download and temp directory.

The dialog can be used to immediately trigger a cleanup and to reviewing of the actions performed _before_ doing so. 

:::{align=center}
![BDeploy Cleanup Page](/images/Doc_Cleanup.png){width=480}
:::

Press the [ **Calculate** ] button to perform cleanup calculation. The result will be actions to be performed on **Instance Groups** or **Nodes** (including the **Master**). If no action is calculated at all, a corresponding message is displayed.

:::{align=center}
![BDeploy Cleanup Actions](/images/Doc_Cleanup_Actions.png){width=480}
:::

Press the [ **Perform** ] button to actually perform the calculated actions. The button [ **Abort Cleanup** ] resets the dialog without further actions.

!!!info Note
The dialog automatically resets itself after a certain timeout. This is to prevent execution of too old actions which might no longer be valid.
!!!

## Hive Browser
The **BHive** page from the **Administration** menu is an internal tool for administrative purposes. It allows access to the internals of the **BDeploy** storage. 

The table shows all available hives. The _default_ hive is the internal storage where metadata about users and outer hives are stored. The actual data is stored in the individual hives itself.

!!!warning Caution
It has the power to destroy _everything_ - use with extreme caution.
!!!

Clicking a BHive opens the panel with maintenance actions

The details tab allows access to the _Audit Logs_ and the content that is stored in a BHive. It also gives access to the repair and prune operations (see CLI) from the web interface.

## Metrics

This dialog provides a quick way to investigate potential performance issues in the server itself by providing access to the in-memory metrics kept track by the server for all actions and requests performed.

The `SERVER` metrics will show various system information about the Java Virtual Machine of the master hosting the Web UI.

## Logging

The logging page allows to view and download the master servers main audit log, which includes information about tools run on the root directory, as well as every request made to the APIs.

## BDeploy Update

The **BDeploy Update** page from the **Administration** menu offers a mechanism to upload new **BDeploy** software versions, deploy these versions (_upgrade_ and _downgrade_ possible) to the running **BDeploy** _master_ and attached _nodes_.

:::{align=center}
![BDeploy System Software](/images/Doc_System_BDeploy_Update.png){width=480}
:::

It also offers a way to _upload_ **BDeploy Launcher** binaries. These binaries are required when [`CLIENT` **Applications**](/power/product/#app-infoyaml) are configured.
Use the [ **Upload** ] button to upload full **BDeploy** versions or **Bdeploy Launcher** binaries from the binary distributions (_ZIP_). 
