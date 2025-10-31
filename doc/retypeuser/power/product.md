---
order: 4
icon: container
---

# Integration of a new product

This chapter is intended for those who want to integrate their own **Product** to be deployed with **BDeploy**. Therefor a couple of YAML files are required. These files describe your product and all its applications together with some additional metadata. These artifacts are required:

[app-info.yaml](#app-infoyaml)
:::
&emsp;YAML file to describe a single **Application**. It is required once for every client and server **Application** of the product.
:::
[product-info.yaml](#product-infoyaml)
:::
&emsp;YAML file with metadata for the whole product
:::
[product-version.yaml](#product-versionyaml)
:::
&emsp;YAML file required for every **Product Version**
:::
[application-template.yaml](#application-templateyaml)
:::
&emsp;Optional YAML file(s) which can be used to define **Application Templates**
:::
[instance-template.yaml](#instance-templateyaml)
:::
&emsp;Optional YAML file(s) which can be used to define **Instance Templates**
:::
[parameter-template.yaml](#parameter-templateyaml)
:::
&emsp;Optional YAML file(s) which can be used to provide shared definitions for parameters which are re-usable in [app-info.yaml](#app-infoyaml) files
:::
[instance-variable-template.yaml](#instance-variable-templateyaml)
:::
&emsp;Optional YAML file(s) which can be used to provide shared definitions for instance variables which are re-usable in [instance-template.yaml](#instance-templateyaml) files
:::
[instance-variable-definitions.yaml](#instance-variable-definitionsyaml)
:::
&emsp;Optional YAML file(s) which can be used to provide instance variable definitions for the product. Whenever instance uses the product, it will generate instance variables (if missing) based on those definitions.
:::
[system-template.yaml](#system-templateyaml)
:::
&emsp;Actually not part of any product itself. Freestanding description that can be used to create multiple instances of multiple products in one go
:::
[product-validation.yaml](#product-validationyaml)
:::
&emsp;A configuration file which references all the YAML files which should be part of a product pre-validation
:::

!!!info Note
You can find some zipped sample products on the [BDeploy Releases Page](https://github.com/bdeployteam/bdeploy/releases).
!!!

:::{align=center}
![YAML relation overview](/images/yaml-overview.png){width=480}
:::

This picture illustrates the relation between the various YAML files which are (can be) used to define a product. Almost all product information and data revolves around **BDeploy** enabling a user to configure instances and applications.

## app-info.yaml

The `app-info.yaml` file is one of the most important parts of **BDeploy**. It describes an **Application**, especially its start command. This **Application** may be _any_ application which contains an _executable_ (script, executable, etc.) and which allows 'installation' by copy to a directory. This is important, as **BDeploy** will 'install' an application by ultimately copying the application to an automatically determined internal directory and launch it from there.

!!!info Tip
An **Application** should not modify files in its own installation directory (although it _can_ do it). **BDeploy** tries to aggressively pool applications per version to reduce the footprint on the disc. If this does not work for your application, use the [pooling configuration](/experts/pooling/) to specify different behaviour.
!!!

!!!warning Warning
`runtimeDependencies` of **Applications** are always considered `GLOBAL` poolable.
!!!

!!!warning Warning
Client applications are always considered for `GLOBAL` pooling by the client launcher.
!!!

Basically, `app-info.yaml` allows you to specify which executable to run, and which parameters could **potentially** be used to configure the **Application**. The `app-info.yaml` does not specify an actual configuration, but describes all possible parameters for an **Application**.

The `app-info.yaml` file must be placed in the root directory of the **Application** it describes.

```yaml app-info.yaml
name: "My Application" <1>
type: CLIENT <2>
pooling: GLOBAL <3>

supportedOperatingSystems: <4>
  - LINUX
  - WINDOWS

branding: <5>
  icon: "branding/my-icon.ico"
  splash:
    image: "branding/my-splash.bmp"
    textRect:
      x: 20
      y: 174
      width: 400
      height: 20
      foreground: "#000000"
    progressRect:
      x: 15
      y: 194
      width: 450
      height: 12
      foreground: "#333333"

processControl: <6>
  supportedStartTypes:
    - INSTANCE
  supportsKeepAlive: true
  noOfRetries: 5
  gracePeriod: 3000
  attachStdin: false
  startupProbe: <7>
    endpoint: "Startup Endpoint"
  livenessProbe: <8>
    endpoint: "Liveness Endpoint"
    initialDelaySeconds: 5
    periodSeconds: 10
  configDirs: "/dir1,/dir2" <9>
  supportsAutostart: true

startCommand: <10>
  launcherPath: "{{WINDOWS:launch.bat}}{{LINUX:launch.sh}}"
  parameters:
    - id: "my.param.1"
      name: "My numeric parameter"
      longDescription: "This is a numeric parameter"
      groupName: "My Parameters"
      parameter: "--number"
      defaultValue: "{{X:instance-number}}"  <11>
      type: NUMERIC
      validateRegex: ^\d+$ <12>
    - id: "my.param.2"
      name: "My textual parameter"
      longDescription: "This is a textual parameter"
      groupName: "My Parameters"
      parameter: "--text"
      mandatory: true
      suggestedValues:
        - "Value 1"
        - "Value 2"
    - id: "my.param.3"
      name: "My parameter without value"
      longDescription: "This is a parameter without a value. It will only put its name on the CLI."
      groupName: "My Parameters"
      parameter: "-v"
      hasValue: false <13>
    - id: "my.param.4"
      name: "My conditional parameter"
      longDescription: "This is only visible and configurable if my.param.2 has value 'Value 1'"
      parameter: "--conditional"
      mandatory: true
      condition: <14>
        parameter: "my.param.2"
        must: EQUAL
        value: "Value 1"
    - template: param.template <15>

stopCommand: <16>
  ...

endpoints: <17>
  http:
    - id: "my-endpoint" <18>
      path: "path/to/the/endpoint"
      port: "{{V:port-param}}" <19>
      secure: false
    - id: "Startup Endpoint"
      type: PROBE_STARTUP <20>
      path: "startup/endpoint"
      port: "{{V:port-param}}"
      secure: false
    - id: "Liveness Endpoint"
      type: PROBE_ALIVE <20>
      path: "liveness/endpoint"
      port: "{{V:port-param}}"
      secure: false

runtimeDependencies: <21>
  - "adoptium/jre:1.8.0_202-b08"
```

1. A human readable name of the **Application**. Will be displayed in the **Configure Application** pane, and is also used as _default_ name for any process _instantiated_ from this **Application**.
2. The type of the application, may be `SERVER` or `CLIENT`. `SERVER` applications can be deployed to **Nodes** (including the **master**) and there be started as server processes. `CLIENT` applications in comparison cannot be deployed on a **Node**, but run on a client PC instead.
3. The supported pooling type for server applications. Supported values are `GLOBAL`, `LOCAL` and `NONE`. `GLOBAL` means that the application is fully poolable and may be installed once (per application version) and used by multiple instance versions of multiple instances. `LOCAL` means that there is limited pooling support, and the application may only be re-used inside a single instance (by multiple instance versions of that instance, e.g. when changing only configuration). `NONE` means that there is no pooling support and the application will be installed freshly per instance version, even if just configuration values changed. This gives some control over how to deploy applications which write data into their installation directory at runtime - which should of course be avoided for better pool-ability. This setting is currently ignored by the client application launcher. Client applications are always globally pooled.
4. List of supported operating systems. This list is solely used to verify during import of the **Product**, that the **Application** actually supports the operating system under which it is listed in the `product-version.yaml`.
5. Only relevant for `CLIENT` applications: The `branding` attribute controls the appearance of `CLIENT` type **Applications** when downloaded by the user. It can be used to specify an `icon` (used to decorate desktop links created by the _client installer_), and a `splash` screen. For the `splash`, you can fine tune the exact location used to display progress text and a progress bar while the application is downloaded to the client PC by the [Launcher CLI](/experts/cli/#launcher-cli). Paths are interpreted relative to the root folder of the **Application**.
6. Only relevant for `SERVER` applications: Process control parameters allow to fine tune how `SERVER` type **Applications** are started and kept alive by **BDeploy**. For details, see the list of [processControl](#supported-processcontrol-attributes) attributes.
7. A _startup probe_ can specify an HTTP Endpoint of type `PROBE_STARTUP` which is queried by **BDeploy** if specified until the endpoint returns a status code >= 200 and < 400. Once this happens, the _startup probe_ is considered to be successful and the **Process** state advances from _starting_ to _running_. The exact response reported by the **Process** is available from the **Process** details panels **Process Probes** section.
8. A _liveness probe_ can specify an HTTP Endpoint of type `PROBE_ALIVE` along with an initial delay in seconds and an interval in which the probe is queried. **BDeploy** starts querying _liveness probes_ only after the application entered _running_ state. This happens either automatically when the process is started (if no _startup probe_ is configured), or once the existing _startup probe_ succeeded. The _liveness probe_ is queried every `periodSeconds` seconds, and the application is considered to be alive if the endpoint returns a status code >= 200 and < 400. If the probe fails, the **Process** status is updated to indicate the problem. The exact response reported by the **Process** is available from the **Process** details panels **Process Probes** section.
9. Allowed configuration directories preset - only valid for `CLIENT` applications. These relative sub-directories of the configuration files directory tree will be made available to this application when run on a client PC. This can later also be configured per process using the [Allowable Configuration Directories](/user/instance/#allowable-configuration-directories) configuration.
10. The start command of the **Application**. Contains the path to the _executable_ to launch, as well as all known and supported parameters. For details, see the full list of [parameter](#supported-parameters-attributes) attributes. To apply e.g. instance-specific values, [Variable Expansion](/power/variables/#variable-expansions) is a powerful tool. It can be used for the `launcherPath` and each parameter's `defaultValue`. In the Web UI it can be used for the parameter values.
11. [Variable Expansion](/power/variables/#variable-expansions) can also be used to expand to [Instance Variables](/user/instance/#instance-variables) in default values. These instance variables are required to exist once the application is configured in an instance. They can either be pre-provided using [Instance Templates](/user/instance/#instance-templates) or need to be manually created when required.
12. An optional regular expression that will be used to validate input on UI parameter configuration form (not applicable to `BOOLEAN` type).
13. A parameter without a value can be used to put a fixed value into the start command. It will appear as a BOOLEAN parameter that can be toggled on and off. This is most useful for flag-like arguments (e.g. -v for verbose).
14. A conditional parameter is a parameter with a condition on it. The condition always refers to another parameter of the same application. The parameter with the condition set will only be visible and configurable if the condition on the referenced parameter is met.
15. A product can provide [parameter templates](#parameter-templateyaml) which can be re-used by referencing their ID inline in applications parameter definitions. All parameter definitions in the template will be inlined at the place the template is referenced.
16. The optional stop command can be specified to provide a mechanism for a clean application shutdown once **BDeploy** tries to stop a process. This command may use [Variable Expansion](/power/variables/#variable-expansions) to access parameter values of the `startCommand` (e.g. configured 'stop port', etc.). It is **not** configurable through the Web UI though. All parameter values will have their (expanded) default values set when the command is run. If no `stopCommand` is specified, **BDeploy** will try to gracefully quit the process (i.e. `SIGTERM`). Both with and without `stopCommand`, **BDeploy** resorts to a `SIGKILL` after the [`gracePeriod`](#supported-parameters-attributes) has expired.
17. Optional definition of provided endpoints. Currently only HTTP endpoints are supported. These endpoints can be configured on the application later, including additional information like authentication, certificates, etc. **BDeploy** can later on call these endpoints when instructed to do so by a third-party application.
18. The ID of the endpoint can be used to call the endpoint remotely by tunneling through potentially multiple levels of **BDeploy** servers.
19. [Variable Expansion](/power/variables/#variable-expansions) can be used on most of the endpoint properties.
20. The type of the endpoint can be used to control how the endpoint is handled by **BDeploy**.
21. Optional runtime dependencies. These dependencies are included in the **Product** when building it. Dependencies are fetched from [**Software Repositories**](/power/runtimedependencies/#software-repositories). `launcherPath` and parameter `defaultValue` (and of course the final configuration values) can access paths within each of the dependencies by using the `{{M:adoptium/jre}}` [Variable Expansion](/power/variables/#variable-expansions), e.g. `launcherPath: {{M:adoptium/jre}}/bin/java`. Note that the declared _dependency_ does not need to specify an operating system, but **must** specify a _version_. This will be resolved by **BDeploy** to either an exact match if available, or a operating system specific match, e.g. `adoptium/jre/linux:1.8.0_202-b08` on `LINUX`. When _referencing_ the dependency in a [Variable Expansion](/power/variables/#variable-expansions), neither an operating system nor a version is required - in fact it must not be specified.

### Supported `processControl` attributes

!!!info Note
Some `processControl` attributes are only supported for `SERVER` applications, while others are only for `CLIENT` applications.  
The mock app-info.yaml above is marked as a `CLIENT` application, but it still lists _all_ process control attributes. This would be nonsencial in a real environment - only the applicable attributes should be used.
!!!

| Type   | Attribute             | Allowed Values                               | Default Value | Description                                                                                                                                                                                                                                                                                                                                                                                                          |
| ------ | --------------------- | -------------------------------------------- | ------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| SERVER | `supportedStartTypes` | `MANUAL`, `MANUAL_CONFIRM`, `INSTANCE`       |               | Can be either `MANUAL` (**Application** must be started _explicitly_ through the Web UI or CLI), `MANUAL_CONFIRM` (**Application** must be started _explicitly_ through the Web UI and a confirmation has to be entered by the user), or `INSTANCE` (the **Application** can be started _automatically_ when the **Start Instance** command is issued, either manually or during server startup - implies `MANUAL`). |
| SERVER | `supportsKeepAlive`   | `true`, `false`                              | false         | Whether this **Application** may be automatically restarted by **BDeploy** if it exits.                                                                                                                                                                                                                                                                                                                              |
| SERVER | `noOfRetries`         | x ∈ ℕ₀ ∧ x < 2⁶⁴                             | 5             | The number of times **BDeploy** will retry starting the **Application** if it `supportsKeepAlive`. The counter is reset after the **Application** is running for a certain amount of time without exiting.                                                                                                                                                                                                           |
| SERVER | `gracePeriod`         | x ∈ ℕ₀ ∧ x < 2⁶⁴                             | 30000         | How long to wait (in milliseconds) for the **Application** to stop after issuing the `stopCommand`. After this timeout expired, the process will be killed.                                                                                                                                                                                                                                                          |
| SERVER | `attachStdin`         | `true`, `false`                              | false         | Specifies if a process expects (and can/wants to handle) input on stdin.                                                                                                                                                                                                                                                                                                                                             |
| SERVER | `startupProbe`        | String (HTTP endpoint ID)                    |               | Specifies a probe which can indicate to **BDeploy** that the application has completed startup.                                                                                                                                                                                                                                                                                                                      |
| SERVER | `livenessProbe`       | String (HTTP endpoint ID)                    |               | Specifies a probe which can indicate to **BDeploy** whether the application is _alive_. _Alive_ means whether the application is currently performing as it should. **BDeploy** does not take immediate action on its own if a liveness probe fails. It will only report the failure to the user.                                                                                                                    |
| CLIENT | `configDirs`          | String (comma-separated list of directories) |               | Specifies a list of configuration sub-directories within the instance's configuration directory which should be made available on the client. Use with care. May expose security sensitive information to clients.                                                                                                                                                                                                   |
| CLIENT | `supportsAutostart`   | `true`, `false`                              | false         | Whether the application is allowed to automatically start upon system bootup (on the client PC).                                                                                                                                                                                                                                                                                                                     |
| CLIENT | `startScriptName`     | String                                       |               | A console-friendly name for a script. This script will be placed in the client PCs PATH (user environment) and can be used to launch the application from the client command line. The application will be started through the **BDeploy** launcher and will perform updates as usual.                                                                                                                               |
| CLIENT | `workingDirectory`    | `DONT_SET`, `SET`                            | `SET`         | Whether to set the working directory to an application specific directory when launching. Note that the current working directory is _never_ set if the application is launched via the CLI via a start script.                                                                                                                                                                                                      |
| CLIENT | `offlineStartAllowed` | `true`, `false`                              | false         | Whether the application is allowed to continue starting in case the server can not be contacted to check for updates.                                                                                                                                                                                                                                                                                                |
| CLIENT | `fileAssocExtension`  | String (e.g. .myext)                         |               | A file extension which should be associated with the client application on the client PC. The client application will still be launched through the **BDeploy** launcher, and applies updates as usual.                                                                                                                                                                                                              |

### Supported `parameters` attributes

!!!info Note
Parameters appear on the final command line in the **exact** order as they appear in the `app-info.yaml` file, regardless of how they are presented in the Web UI, or how they are grouped using the `groupName` attribute. This allows to build complex command lines with positional parameters through `app-info.yaml`.
!!!

=== **Attribute**: `id`
_Default:_

_Mandatory:_ yes

_Description:_ A unique ID of the parameter within the whole product which will contain the **Application** described by this `app-info.yaml`.

=== **Attribute**: `name`
_Default:_

_Mandatory:_ yes

_Description:_ A human readable name of the parameter used as label in the configuration UI.

=== **Attribute**: `longDescription`
_Default:_

_Mandatory:_ no

_Description:_ An optional human readable description of the paramater, which is displayed in an info popover next to the parameter in the Web UI.

=== **Attribute**: `groupName`
_Default:_

_Mandatory:_ no

_Description:_ An optional group name. The configuration UI may use this information to group parameters with the same `groupName` together.
!!!warning Caution
Although parameters in the UI are grouped together (and thus might change order), the order in which parameters appear on the final command line is exactly the order in which they are defined in the `app-info.yaml` file.
!!!

=== **Attribute**: `suggestedValues`
_Default:_

_Mandatory:_ no

_Description:_ An optional list of suggested values for parameters of type `STRING` (the default). The Web UI will present this list when editing the parameter value.

=== **Attribute**: `parameter`
_Default:_

_Mandatory:_ yes

_Description:_ The actual parameter, e.g. `--parameter`, `-Dmy.system.prop`, etc.
!!!info Note
The value of the parameter is not part of this definition, nor is any potential value separator (e.g. `=`).
!!!

=== **Attribute**: `hasValue`
_Default:_ `true`

_Mandatory:_ no

_Description:_ Whether the parameter has a value or not. If the parameter has no value, it is treated as `BOOLEAN` type parameter (i.e. it is either there (`true`) or not (`false`)).

=== **Attribute**: `valueAsSeparateArg`
_Default:_ `false`

_Mandatory:_ no

_Description:_ Whether the value of the parameter must be placed as a separate argument on the command line. If not, the value (if `hasValue`) will be concatenated to the `parameter` using the `valueSeparator`.

=== **Attribute**: `valueSeparator`
_Default:_ `=`

_Mandatory:_ no

_Description:_ The character (sequence) to use to concatenate the `parameter` and the actually configured value of it together. Used if not `valueAsSeparateArg`.

=== **Attribute**: `defaultValue`
_Default:_

_Mandatory:_ no

_Description:_ A default value for the parameter. The default value may contain variable references according to the [Variable Expansion](/power/variables/#variable-expansions) rules.

=== **Attribute**: `global`
_Default:_ `false`

_Mandatory:_ no

_Description:_ Whether this parameter is `global`. This means that inside a single **Instance**, every process requiring this parameter will receive the same value. The configuration UI will provide input fields for the parameter for every **Application** which requires the parameter, and propagate value changes to all other **Applications** requiring it.

=== **Attribute**: `mandatory`
_Default:_ `false`

_Mandatory:_ no

_Description:_ Whether the parameter is required. If the parameter is not required, it is by default not put on the command line and must be added manually through a dedicated dialog on the configuration page.

=== **Attribute**: `fixed`
_Default:_ `false`

_Mandatory:_ no

_Description:_ Whether the parameter is fixed. This means that the parameter can **not** be changed by the user.

Consider a command line like this:

```bash
/path/to/java/bin/java -Dmy.prop=value -jar application.jar
```

In this case you will want the user to be able to edit the value of `-Dmy.prop` parameter, but the user may **never** be able to edit the `-jar application.jar` part. A definition for this command line would look like this:

```yaml
startCommand:
  launcherPath: "{{M:openjdk/jre:1.8.0_u202-b08}}/bin/java{{WINDOWS:w.exe}}"
  parameters:
    - id: "my.prop"
      name: "My Property"
      parameter: "-Dmy.prop"
      mandatory: true
    - id: "my.jar"
      name: "Application JAR"
      parameter: "-jar"
      defaultValue: "application.jar"
      valueAsSeparateArg: true
      mandatory: true
      fixed: true <1>
```

The fixed flag will cause the parameter to **always** use the defined default value and disable editing in the configuration UI.

=== **Attribute**: `type`
_Default:_ `STRING`

_Mandatory:_ no

_Description:_ The Type of the parameter. This defines the type of input field used to edit the parameter. Available are `STRING`, `NUMERIC`, `BOOLEAN`, `PASSWORD`, `CLIENT_PORT`, `SERVER_PORT`, `URL` and `ENVIRONMENT`.

`CLIENT_PORT`, `SERVER_PORT` and `URL` are special because they can be modified via some port-specific dialogs, e.g. the port-shift functionality on the instance configuration page.

The `ENVIRONMENT` type will cause a parameter to be put in the process' environment instead of its command line. The name of the environment variable is specified using the `parameter` field. The fields `hasValue`, `valueAsSeparateArg` and `valueSeparator` must not be set on `ENVIRONMENT` parameters, and are ignored. Instead the Operating System specific Environment Variable handling is applied.

=== **Attribute**: `customEditor`
_Default:_ `STRING`

_Mandatory:_ no

_Description:_ The id of a custom editor to be used when editing this parameter through the **BDeploy** Web UI. Custom editors can be contributed by [Plugins](/experts/system/#plugins) which are either globally installed in the **BDeploy** server, or contributed dynamically by the product.

=== **Attribute**: `condition`
_Default:_

_Mandatory:_ no

_Description:_ A conditional parameter is a parameter with a condition on it. The condition always refers to another parameter on the same application. The parameter with the condition set will only be visible and configurable if the condition on the referenced parameter is met.

A condition expression (isolated) looks like this:

```yaml
condition:
  parameter: "my.param.2"
  must: EQUAL
  value: "Value 1"
```

Or, in its newer form, the very same (but ultimately more powerful) using `expression` would look like this:

```yaml
condition:
  expression: "{{V:my.param.2}}"
  must: EQUAL
  value: "Value 1"
```

The power comes from the ability to provide an arbitrary amount of [Variable Expansions](/power/variables/#variable-expansions) in the [Link Expressions](/user/instance/#link-expressions).

The condition block understands the following fields:

| Name         | Description                                                                                                                                                                                                            |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `parameter`  | The referenced parameters ID.                                                                                                                                                                                          |
| `expression` | A [Link Expression](/user/instance/#link-expressions) which is resolved and matched against the condition.<br/><br/>:warning:**WARNING** `parameter` may not be set if the newer `expression` is used, and vice versa. |
| `must`       | The type of condition.                                                                                                                                                                                                 |
| `value`      | The value to match against if required by the condition type.                                                                                                                                                          |

The `must` field understands the following condition types:

| Name           | Description                                                                                                                                                                                                               |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `EQUAL`        | The referenced parameters value must equal the given condition value.                                                                                                                                                     |
| `CONTAIN`      | The referenced parameters value must contain the given condition value.                                                                                                                                                   |
| `START_WITH`   | The referenced parameters value must start with the given condition value.                                                                                                                                                |
| `END_WITH`     | The referenced parameters value must end with the given condition value.                                                                                                                                                  |
| `BE_EMPTY`     | The referenced parameters value must be empty. In case of `BOOLEAN` parameters the value must be `false`.<br/><br/>:information_source:**NOTE** Leading and trailing whitespaces are ignored for this check.              |
| `BE_NON_EMPTY` | The referenced parameters value must be any non-empty value. In case of `BOOLEAN` parameters the value must be `true`.<br/><br/>:information_source:**NOTE** Leading and trailing whitespaces are ignored for this check. |

!!!warning Warning
Be aware that the condition on a parameter has a higher precedence than `mandatory`. A `mandatory` parameter whos condition is not met is still not configurable. As soon as the condition is met, it is automatically added to the configuration using its default value.
!!!

!!!info Tip
If possible, a parameter with a condition should be defined **after** the parameter referenced in the condition if the referenced parameter is mandatory. This will make a difference when an application configuration is initially created by drag & drop.
!!!

===

### Supported `endpoints.http` attributes

!!!info Note
Endpoints definitions are templates which can later on be configured by the user. Please check table below for properties that **are not** editable.
!!!

!!!info Note
For some properties [Variable Expansion](/power/variables/#variable-expansions) can be used, for instance to reference a parameter of the application (using `{{V:port-param}}` where `port-param` is the ID of a parameter on the `startCommand`).
!!!

| Attribute        | Variable Expansion | Editable | Description                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| ---------------- | ------------------ | -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`             | No                 | No       | The unique ID of the endpoint. This ID can be used by an authorized third-party application to instruct **BDeploy** to call this endpoint and return the result.                                                                                                                                                                                                                                                                                   |
| `enabled`        | Yes                | No       | Where or not the endpoint can be used. When using [Link Expression](/user/instance/#link-expressions), if the expression evaluates to a non-empty value which does _not_ equal `false`, the endpoint is considered available/enabled and presented to the user both for configuration and usage. This can be used to tie an endpoint to the configuration of a certain parameter, e.g. the server port configuration which will host the endpoint. |
| `proxying`       | No                 | No       | Should be true if this endpoint supports [UI Endpoint Proxying](/experts/proxy/#ui-endpoints).                                                                                                                                                                                                                                                                                                                                                     |
| `type`           | No                 | No       | Currently `DEFAULT`, `PROBE_STARTUP`, `PROBE_ALIVE` and `UI` are supported. Endpoints referenced by _startup_ or _liveness probes_ in the `processControl` section of a server process need to have the according type. If not specified, the `DEFAULT` type is assumed.<br><br>See [UI Endpoints](/experts/proxy/#ui-endpoints) for details regarding UI endpoints.                                                                               |
| `path`           | Yes                | No       | The path of the endpoint on the target process. **BDeploy** uses this and other parameters (`port`) to construct an URI to the local server.                                                                                                                                                                                                                                                                                                       |
| `contextPath`    | Yes                | No       | May be used to add more path segments to the link generated by BDeploy for a [UI Endpoint](/experts/proxy/#ui-endpoints).                                                                                                                                                                                                                                                                                                                          |
| `port`           | Yes                | Yes      | The port this endpoint is hosted on.                                                                                                                                                                                                                                                                                                                                                                                                               |
| `secure`         | Yes                | Yes      | Whether HTTPS should be used when calling the endpoint.                                                                                                                                                                                                                                                                                                                                                                                            |
| `trustAll`       | No                 | Yes      | Whether to trust any certificate when using HTTPS to call the endpoint. Otherwise a custom `trustStore` must be set if a self-signed certificate is used by the application.                                                                                                                                                                                                                                                                       |
| `trustStore`     | Yes                | Yes      | Path to a KeyStore in the `JKS` format, containing certificates to trust.                                                                                                                                                                                                                                                                                                                                                                          |
| `trustStorePass` | Yes                | Yes      | The passphrase used to load the `trustStore`.                                                                                                                                                                                                                                                                                                                                                                                                      |
| `authType`       | Yes                | Yes      | The type of authentication used by **BDeploy** when calling the endpoint. Can be `NONE`, `BASIC`, `DIGEST` or `OIDC`.                                                                                                                                                                                                                                                                                                                              |
| `authUser`       | Yes                | Yes      | The username to use for `BASIC` or `DIGEST` `authType`.                                                                                                                                                                                                                                                                                                                                                                                            |
| `authPass`       | Yes                | Yes      | The password to use for `BASIC` or `DIGEST` `authType`.                                                                                                                                                                                                                                                                                                                                                                                            |
| `tokenUrl`       | Yes                | Yes      | The URL for obtaining a token to use for `OIDC` `authType`.                                                                                                                                                                                                                                                                                                                                                                                        |
| `clientId`       | Yes                | Yes      | The client id for which you are obtaining a token for `OIDC` `authType`.                                                                                                                                                                                                                                                                                                                                                                           |
| `clientSecret`   | Yes                | Yes      | The client secret to use for `OIDC` `authType`.                                                                                                                                                                                                                                                                                                                                                                                                    |

!!!info Note
Endpoints which are not considered _enabled_ are not required to be configured by the user, but are still reported via the public API.
!!!

## product-info.yaml

!!!info Note
There is no actual requirement for the file to be named `product-info.yaml`. This is just the default, but you can specify another name on the command line or in build tool integrations.
!!!

The `product-info.yaml` file describes which **Applications** are part of the final **Product**, as well as some additional **Product** metadata.

```yaml product-info.yaml
name: My Product <1>
product: com.example/product <2>
vendor: My Company <3>
minMinionVersion: 1.2.3 <4>

applications: <5>
  - my-app1
  - my-app2

configTemplates: my-config <6>
pluginFolder: my-plugins <7>

applicationTemplates:
  - "my-templates/app-template.yaml" <8>
instanceTemplates:
  - "my-templates/template.yaml" <9>

parameterTemplates:
  - "my-templates/param-template.yaml" <10>
instanceVariableTemplates:
  - "my-templates/variable-template.yaml" <11>
instanceVariableDefinitions:
  - "my-definitions/instance-variable-definitions.yaml" <12>

versionFile: my-versions.yaml <13>
```

1. A human readable name of the **Product** for display purposes in the Web UI.
2. A unique ID of the **Product** which is used to base **Instances** of. This should not change, as changing the **Product** ID of an existing **Instance** is not supported.
3. The vendor of the product. Displayed in the Web UI and used when installing client applications.
4. The minimum BDeploy version that a minion must have in order to allow installation of this product.
5. The list of **Applications** which are part of the **Product**. These IDs can be anything, they just have to match the IDs used in the `product-version.yaml` referenced below.
6. Optional: A relative path to a directory containing configuration file templates, which will be used as the default set of configuration files when creating an **Instance** from the resulting **Product**.
7. Optional: A relative path to a directory containing one or more plugin JAR files. These plugins are loaded by the server on demand and provided for use when configuring applications which use this very product version.
8. A reference to an application template YAML file which defines an [`application-template.yaml`](#application-templateyaml).
9. A reference to an instance template YAML file which defines an [`instance-template.yaml`](#instance-templateyaml).
10. A reference to a parameter template YAML file which defines a [`parameter-template.yaml`](#parameter-templateyaml).
11. A reference to an instance variable template YAML file which defines an [`instance-variable-template.yaml`](#instance-variable-templateyaml).
12. A reference to an instance variable definitions YAML file which defines an [`instance-variable-definitions.yaml`](#instance-variable-definitionsyaml)
13. The `product-version.yaml` which associates the **Application** IDs (used above) with actual paths to **Applications** on the file system.

## product-version.yaml

!!!info Note
There is no actual requirement for the file to be named `product-version.yaml` as it is referenced from the `product-info.yaml` by relative path anyway. This is just the default name.
!!!

The `product-version.yaml` file associates **Application** IDs used in the `product-info.yaml` with actual locations on the local disc. This is used to find and import each included **Application** when importing the **Product**.

The reason why this file is separate from the `product-info.yaml` is because its content (e.g. version) is specific to a single product **Build** . Therefore the `product-version.yaml` ideally is created during the build process of the product by the build system of your choice. This is different to the `app-info.yaml` files and the `product-info.yaml` file as they are written manually.

```yaml product-version.yaml
version: "2.1.0.201906141135" <1>
appInfo:
  my-app1: <2>
    WINDOWS: "build/windows/app-info.yaml" <3>
    LINUX: "build/linux/app-info.yaml"
  my-app2:
    WINDOWS: "scripts/app2/app-info.yaml" <4>
    LINUX: "scripts/app2/app-info.yaml"
```

1. A unique **Tag** to identify the product version. There is no requirement for any version-like syntax here, it can be basically anything. It should just be unique per **Product Version**.
2. The **Application** ID must match the one used in `product-info.yaml`.
3. You may have different binaries for a single application depending on the target operating system. It is not required to provide every application for every operating system. You can just leave out operating systems you don't care about.
4. You can also use the exact same **Application** directory and `app-info.yaml` to satisfy multiple operating system targets for one **Application**.

## application-template.yaml

!!!info Note
There is no actual requirement for the file to be named `application-template.yaml` as it is referenced from the `product-info.yaml` by relative path anyway. Multiple **Application Template** YAML files can exist and be referenced by `product-info.yaml`.
!!!

This file defines a single **Application Template**. A [`product-info.yaml`](#product-infoyaml) can reference multiple templates, from which the user can choose.

```yaml application-template.yaml
id: server-with-sleep <1>
application: server-app
name: 'Server With Sleep (Oracle)' <2>
processName: 'Server With Sleep' <2>
description: 'Server application which sleeps before exiting'
preferredProcessControlGroup: 'First Group' <3>

applyOn: <4>
  - LINUX

templateVariables: <5>
  - id: sleep-timeout
    name: 'Sleep Timeout'
    description: 'The amount of time the server application should sleep'
    type: NUMERIC
    defaultValue: 60
    suggestedValues:
    - '60'
    - '120'

processControl: <6>
  startType: MANUAL_CONFIRM
  keepAlive: false
  noOfRetries: 3
  gracePeriod: 30000
  attachStdin: true

startParameters: <7>
- id: param.sleep
  value: '{{T:sleep-timeout}}'
```

1. An **Application Template** must have an ID. This can be used to reference it from an **Instance Template**.
2. `name` is the value user sees when they choose the template in UI. `processName` is the name of the resulting process configuration.
3. The preferred process control group is used to determine which process control group to use when applying the application template. This is only used if a **Process Control Group** with this name exists in the instance configuration. **Process Control Groups** can be pre-configured in an [`instance-template.yaml`](#instance-templateyaml).
4. A list of operating systems that this application may run on. If the node is running on an operating system that is not contained in the list, then the process will not be imported whenever an instance template references this application. If no list is present, then all operating systems are considered valid.
5. A template can define (and use) template variables which are mandatory input by the user when using the template. A template variable can be referenced in parameter value definitions using the `{{T:varname}}` syntax. If the parameter value is numeric, you can also use simple arithmetic operations on the template variable like `{{T:varname:+10}}` which will add 10 to the numeric value of the template variable.
6. A template can define arbitrary process control parameters to further control the default process control settings.
7. Start command parameters are referenced by their ID, defined in [`app-info.yaml`](#app-infoyaml). If a value is given, this value is applied. If not, the default value is used. If a parameter is optional, it will be added to the configuration if it is referenced in the template, regardless of whether a value is given or not.

!!!info Note
An **Application Template** can also _extend_ another previously defined template. This works the same as the `template`
specifier in [`instance-template.yaml`](#instance-templateyaml) and also allows for `fixedVariables`. In this case
specify `template` (passing the id of another application template) instead of `application`. The last template in the
inheritance chain must specify an `application`.
Inheritance will apply all the properties of the "parent" application template to the current one, allowing to override
or extend individual properties.
!!!

```yaml application-template.yaml
id: fixed-sleep
name: 'Server With 10 Seconds Sleep'
template: server-with-sleep <1>

fixedVariables: <2>
  - id: sleep-timeout
    value: 10
```

1. The `template` attribute specifies another application template (which must be registered in the product) to extend. All properties of that other template are merged into this application template. Properties of this template take precedence in case of a conflict.
2. `fixedVariables`, as also described for [`instance-template.yaml`](#instance-templateyaml), allow to override the value of a specific template variable in _this and the base template_. Values queried from the user when applying this template will be ignored for any variable which has an _overridden_, _fixed_ value.

### Supported `templateVariables` Attributes

| Attribute         | Description                                                                                                                                                       |
| ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`              | The unique ID of the template variable. If multiple applications (or instances) define the same variable ID, it is queried from the user only once.               |
| `name`            | The user visible name of the variable, used when querying user input while applying the template.                                                                 |
| `description`     | Further detailed description of the template variable, explaining to the user the purpose of the variable.                                                        |
| `type`            | Type of template variable. This defines the type of input field used to edit the variable. Available are `STRING` (default), `NUMERIC`, `BOOLEAN` and `PASSWORD`. |
| `defaultValue`    | An optional default value which is pre-filled when querying the user for template variable values.                                                                |
| `suggestedValues` | A list of values which will be suggested to the user once they begin providing a value for this variable.                                                         |

!!!info Note
Defined `templateVariables` can be used in the `name` of the application template, as well as in each `startParameter`s `value` attribute.
!!!

### Supported `fixedVariables` Attributes

Fixed variables allow overriding template variable input from the user to a _fixed_ value.

| Attribute | Description                                                                                                              |
| --------- | ------------------------------------------------------------------------------------------------------------------------ |
| `id`      | The unique ID of the template variable. The variable may be declared in either this or any parent template.              |
| `value`   | The target fixed value for the variable to be used in this and any parent template. This value overrides any user input. |

### Supported `startParameters` Attributes

The list of `startParameters` provides control over the parameters in the resulting process. This is different from the parameter _definition_ in [`app-info.yaml`](#app-infoyaml) as this list only provides information about presence and value of parameters when applying this template.

| Attribute | Description                                                                                                                                                                                                                                                     |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`      | The unique ID of the parameter. A definition of a parameter with this ID **must** exist in the [`app-info.yaml`](#app-infoyaml) file of the referenced `application`. If the parameter is optional, it will be added to the process when applying the template. |
| `value`   | The target value of the parameter. If no value is given, the `defaultValue` from the parameter's definition is applied.                                                                                                                                         |

## instance-template.yaml

!!!info Note
There is no actual requirement for the file to be named `instance-template.yaml` as it is referenced from the `product-info.yaml` by relative path anyway. Multiple **Instance Template** YAML files can exist and be referenced by `product-info.yaml`.
!!!

This file defines a single **Instance Template**. A [`product-info.yaml`](#product-infoyaml) can reference multiple templates, from which the user can choose.

```yaml instance-template.yaml
name: Default Configuration <1>
description: "Creates an instance with the default server and client configuration"

templateVariables: <2>
  - id: sleep-timeout
    name: "Sleep Timeout"
    description: "The amount of time the server application should sleep"
    defaultValue: 60

instanceVariables: <3>
  - id: var.id
    value: "MyValue"
    description: "The description of my instance variable"
    type: PASSWORD
  - template: var.template <4>

instanceVariableDefaults: <5>
  - id: var.id
    value: "A value other than above"

instanceVariableValues: <6>
  - id: def.id
    value: "A value that will update instance variable created from def.id definition"
  - id: other.def.id
    value: "{{T:sleep-timeout}}"

processControlGroups: <7>
  - name: "First Group"
    startType: "PARALLEL"
    startWait: "WAIT"
    stopType: "SEQUENTIAL"

groups: <8>
- name: "Server Apps"
  description: "All server applications"

  applications:
  - application: server-app
    name: "Server No Sleep"
    description: "Server application which immediately exits"
  - template: server-with-sleep <9>
    fixedVariables: <10>
      - id: sleep-timeout
        value: 10
  - application: server-app <11>
    name: "Server With Sleep"
    description: "Server application which sleeps before exiting"
    applyOn: <12>
      - LINUX
    processControl:
      startType: MANUAL_CONFIRM
    startParameters: <13>
    - id: param.sleep
      value: "{{T:sleep-timeout}}"
    applyTo: <14>
    - LINUX
- name: "Client Apps"
  type: CLIENT <15>
  description: "All client applications"
  autoStart: false <16>
  autoUninstall: true <17>

  applications:
  - application: client-app
    description: "A default client application."
```

1. Each **Instance Template** has a name and a description, which are shown on the **Instance Template** Wizard.
2. A template can define (and use) template variables which are mandatory input by the user when using the template. A template variable can be referenced in parameter value definitions using the `{{T:varname}}` syntax. If the parameter value is numeric, you can also use simple arithmetic operations on the template variable like `{{T:varname:+10}}` which will add 10 to the numeric value of the template variable.
3. [Instance Variables](/user/instance/#instance-variables) can be defined in an instance template. Those definitions will be applied to a new instance when this template is used. [Link Expressions](/user/instance/#link-expressions) can then be used to expand to the [Instance Variables](/user/instance/#instance-variables) values in parameters, configuration files, etc.
4. [Instance Variables](/user/instance/#instance-variables) can also be defined in an [`instance-variable-template.yaml`](#instance-variable-templateyaml) file externally, and referenced via its ID.
5. `instanceVariableDefaults` allows to override the value of a previous [Instance Variables](/user/instance/#instance-variables) definition in the same template. This is most useful when applying [`instance-variable-template.yaml`](#instance-variable-templateyaml) files using the `template` syntax in `instanceVariables`. The instance variable template can be shared more easily if instance templates have means of providing distinct values per instance template.
6. `instanceVariableValues` allows to override the value of an instance variable created from [instance-variable-definitions.yaml](#instance-variable-definitionsyaml)
7. **Process Control Groups** can be pre-configured for an instance template. If an application template later on wishes to be put into a certain **Process Control Group**, the group is created based on the template provided in the instance template. Note that the defaults for a **Process Control Group** in a template are slightly different from the implicit 'Default' **Process Control Group** in **BDeploy**. The defaults are: `startType`: `PARALLEL`, `startWait`: `WAIT`, `stopType`: `PARALLEL`.
8. A template defines one or more groups of applications to configure. Each group can be assigned to a physical node available on the target system. Groups can be skipped by not assigning them to a node, so they provide a mechanism to provide logical groups of processes (as result of configuring the applications) that belong together and might be optional. It is up to the user whether a group is mapped to a node, or not. Multiple groups can be mapped to the same physical node.
9. **Instance Templates** can reference **Application Templates** by their `id`. The **Instance Templates** can further refine an **Application Template** by setting any of the valid application fields in addition to the template reference.
10. When referencing an application template, it is possible to define _overrides_ for the template variables (`{{X:...}}`) used in the template. Use provided values will **not** be taken into account for this variable when applying the template, instead the _fixed_ value will be used.
11. A template group contains one or more applications to configure, where each application can consist of process control configuration and parameter definitions for the start command of the resulting process - exactly the same fields are valid as for **Application Templates** - except for the `id` which is not required.
12. A list of operating systems where this application will be applied. If the node is running on an operating system that is not contained in the list, then the application will not be imported. If no list is present, then all operating systems are considered valid.
13. Start command parameters are referenced by their ID, defined in [`app-info.yaml`](#app-infoyaml). If a value is given, this value is applied. If not, the default value is used. If a parameter is optional, it will be added to the configuration if it is referenced in the template, regardless of whether a value is given or not.
14. Using `applyTo`, an application can be restricted to be applied only to certain nodes, running a specified operating system. A list of supported operating systems can be specified. If this is not specified, the application is assumed to be capable of being applied to nodes running any of all supported operating systems.
15. A template group can have either type `SERVER` (default) or `CLIENT`. A group may only contain applications of a compatible type, i.e. only `SERVER` applications in `SERVER` type group. When applying the group to a node, applications will be instantiated to processes according to their supported OS and the nodes physical OS. If a `SERVER` application does not support the target nodes OS, it is ignored.
16. Whether the instance should be started automatically when starting the minion(s). This parameter is optional and will be `false` if not specified.
17. Whether to schedule background uninstallation of old instance versions. This parameter is optional and will be `true` if not specified.

An instance template will be presented to the user when visiting an [Empty Instance](/user/instance/#instance-templates).

!!!warning Warning
`instanceVariables` and `instanceVariableDefaults` have been deprecated (since 7.2.0) in favor of `instanceVariableValues` and [instance-variable-definitions.yaml](#instance-variable-definitionsyaml).
!!!

### Supported `templateVariables` Attributes

!!!info Note
`templateVariables` follows the same scheme as [Supported `templateVariables` Attributes](#supported-templatevariables-attributes) in [`application-template.yaml`](#application-templateyaml) files.
!!!

!!!info Note
Defined `templateVariables` can be used in each `instanceVariables` (and `instanceVariableDefaults`) `value` attribute, and per inline application in each field which is supported by [`application-template.yaml`](#application-templateyaml)s `templateVariables`.
!!!

### Supported `instanceVariables` Attributes

| Attribute      | Description                                                                                                                                                                                                                                                                                                                                 |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `template`     | Allows referencing a collection of template instance variables defined in a single [`instance-variable-template.yaml`](#instance-variable-templateyaml)<br/><br/>:information_source:**NOTE** If this attribute is given, no other attribute may be given, as this item is replaced by the definitions from the instance variable template. |
| `id`           | The unique ID of the instance variable to be created.                                                                                                                                                                                                                                                                                       |
| `value`        | The value with which the instance variable should be created. This value can use template variables defined in the containing [`instance-template.yaml`](#instance-templateyaml).                                                                                                                                                           |
| `description`  | A detailed description of the variable presented to the user in the [Instance Variables](/user/instance/#instance-variables) overview.                                                                                                                                                                                                      |
| `type`         | The type of the variable, the same types as if defining a `parameter` can be used, see [Supported `parameters` attributes](#supported-parameters-attributes).                                                                                                                                                                               |
| `customEditor` | A potentially required custom editor from a plug-in which needs to be used to edit the value of the instance variable, also see [Supported `parameters` attributes](#supported-parameters-attributes).                                                                                                                                      |

### Supported `instanceVariableDefaults` Attributes

| Attribute | Description                                                                                                                                                                                 |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`      | The unique ID of a previously defined instance variable (either directly in the same template, or through an applied [`instance-variable-template.yaml`](#instance-variable-templateyaml)). |
| `value`   | The value to use when applying this instance template.                                                                                                                                      |

### Supported `instanceVariableValues` Attributes

| Attribute | Description                                                                                                                             |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `id`      | The unique ID of an instance variable definition defined in [`instance-variable-definitions.yaml`](#instance-variable-definitionsyaml). |
| `value`   | The value to use when applying this instance template.                                                                                  |

### Supported `processControlGroups` Attributes

| Attribute   | Description                                                                                                                                                                                                                          |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `name`      | The name of the [Process Control Groups](/user/instance/#process-control-groups) to create. This group can be referenced by [`application-template.yaml`](#application-templateyaml) files `preferredProcessControlGroup` attribute. |
| `startType` | The initial **Start Type**, see [Process Control Groups](/user/instance/#process-control-groups).                                                                                                                                    |
| `startWait` | The initial **Start Wait**, see [Process Control Groups](/user/instance/#process-control-groups).                                                                                                                                    |
| `stopType`  | The initial **Stop Type**, see [Process Control Groups](/user/instance/#process-control-groups).                                                                                                                                     |

### Supported `groups` Attributes

`groups` is a list of _template groups_. This groups together a set of [Application Templates](#application-templateyaml) or inline template definitions. Each group has a set of own attributes, as well as a list of templates:

| Attribute      | Description                                                                                                                                                                                                                                                                                                                          |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `name`         | The name of the group. This will be presented to the user, and a user has the possibility to select which groups of the template should be applied to which node in an instance.                                                                                                                                                     |
| `description`  | A description which helps the user in deciding whether to apply a certain group or not.                                                                                                                                                                                                                                              |
| `type`         | Either `SERVER` or `CLIENT` right now. The target node where the group is applied must match this type.                                                                                                                                                                                                                              |
| `applications` | A list of templates. A template can either be a reference to an [`application-template.yaml`](#application-templateyaml) defined template, or - alternatively - can be defined inline. In this case all attributes of an [`application-template.yaml`](#application-templateyaml) apply to a single item in the `applications` list. |

## parameter-template.yaml

A `parameter-template.yaml` allows products to define re-usable blocks of parameters associated to a unique ID. These can then be applied in `app-info.yaml` files. For the user of **BDeploy**, those parameters will appear as if they were defined directly in the [`app-info.yaml`](#app-infoyaml) of the application.

```yaml parameter-template.yaml
id: param.template <1>

parameters: <2>
  - id: tpl-param
    name: "Template Parameter"
    defaultValue: "x"
    longDescription: "A parameter defined in a template"
```

1. The ID can be used to reference the template afterwards from an [`app-info.yaml`](#app-infoyaml).
2. The `parameters` can contain an arbitrary amount of parameter definitions, which follow exactly the same schema as [supported `parameters` attributes](#supported-parameters-attributes) in [`app-info.yaml`](#app-infoyaml).

!!!info Note
Inlining of templates into applications happens **before** anything else. Parameter templates can also reference other parameters (e.g. `{{V:my-param}}`), even if they are not part of this very template. All applications using this parameter would then either have to have (directly or through another template) this `my-param` parameter, **or** will receive a validation warning and need to change the value.
!!!

!!!warning Warning
To be able to use a template, the template needs to also be registered in the [`product-info.yaml`](#product-infoyaml) so it is included at build time.
!!!

## instance-variable-template.yaml

An `instance-variable-template.yaml` works the same as a `parameter-template.yaml` in that it provides common definitions for instance variables, which can be re-used in [`instance-template.yaml`](#instance-templateyaml) files. Those definitions are inlined early on, so variables from `instance-variable-template.yaml` files can do exactly the same things as `instanceVariables` in a [`instance-template.yaml`](#instance-templateyaml).

```yaml instance-variable-template.yaml
id: var.template <1>

instanceVariables: <2>
  - id: "my-instance-var"
    description: "My Instance Variable"
    value: "My Value!"
    type: STRING
```

1. The ID can be used to reference the template afterwards from an [`instance-template.yaml`](#instance-templateyaml).
2. An arbitrary amount of instance variable templates. The schema is the same as [supported `instanceVariables` Attributes](#supported-instancevariables-attributes) in [`instance-template.yaml`](#instance-templateyaml)

!!!warning Warning
To be able to use a template, the template needs to also be registered in the [`product-info.yaml`](#product-infoyaml) so it is included at build time.
!!!

!!!warning Warning
Deprecated in favor of [`instance-variable-definitions.yaml`](#instance-variable-definitionsyaml)
!!!

## instance-variable-definitions.yaml

!!!info Note
There is no actual requirement for the file to be named `instance-variable-definitions.yaml` as it is referenced from the `product-info.yaml` by relative path anyway. Multiple **Instance Variable Definitions** YAML files can exist and be referenced by `product-info.yaml`.
!!!

An `instance-variable-definitions.yaml` provides definitions for instance variables, based on which instance variables will be created. The definition values can be overriden using `instanceVariableValues` in an [`instance-template.yaml`](#instance-templateyaml).

```yaml instance-variable-definitions.yaml
definitions: <1>
  - id: "my-instance-variable-definition"
    name: "My Instance Variable Definition"
    longDescription: "The description for instance variable definition"
    type: "STRING"
    defaultValue: "someDefaultValue"
    groupName: "Instance Variable Definitions Group"
    validateRegex: ^[a-zA-Z]+$
```

1. File consists of a single list of `definitions` containing an arbitrary amount of instance variable definitions.

### Supported `definitions` Attributes

Instance variable definitions support a subset of [supported parameters attributes](/power/product/#supported-parameters-attributes)

| Attribute         | Description                                                                                                                                                                        |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`              | The unique ID of the instance variable definition. Instance variable created from definition will have the same ID.                                                                |
| `name`            | A human readable name of the instance variable used as label in the configuration UI.                                                                                              |
| `longDescription` | An optional human readable description of the instance variable, which is displayed in an info popover next to the instance variable in the Web UI.                                |
| `type`            | Type of instance variable. This defines the type of input field used to edit the variable. Available are `STRING`, `NUMERIC`, `BOOLEAN`, `PASSWORD`, `CLIENT_PORT`, `SERVER_PORT`. |
| `defaultValue`    | A default value for the variable. The default value may contain variable references according to the [Variable Expansion](/power/variables/#variable-expansions) rules.            |
| `groupName`       | An optional group name. The configuration UI may use this information to group variables with the same group name together.                                                        |
| `fixed`           | Whether the instance variable is fixed. This means that the variable can **not** be changed by the user.                                                                           |
| `suggestedValues` | An optional list of suggested values for variable of type STRING (the default). The Web UI will present this list when editing the variable value.                                 |
| `customEditor`    | A potentially required custom editor from a plug-in which needs to be used to edit the value of the instance variable.                                                             |
| `validateRegex`   | An optional regular expression that will be used to validate input on UI instance variables configuration form (not applicable to `BOOLEAN` type).                                 |

## response-file-yaml

Response files are required in order to use instance templates via the CLI. They can be created manually, but there is also a CLI command in the [remote-product tool](../experts/cli#product-management-commands) that may be used to download a blueprint.

```yaml response-file.yaml
name: "Demo Instance"
description: "The Test System's Demo Instance"
productId: "io.bdeploy/demo"
productVersionRegex: "3\\..*"
initialProductVersionRegex: "2\\..*"
templateName: "Default Configuration"
autoStart: false
autoUninstall: true
defaultMappings:
  - group: "Server Apps"
    node: "master"
  - group: "Client Apps"
    node: "Client Applications"
fixedVariables:
  - id: "text-param"
    value: "XX"
  - id: "sleep-timeout"
    value: 10

```

See [supported instance attributes](#supported-instance-attributes) for explanations of the individual fields.

## system-template.yaml

A (freestanding) `system-template.yaml` allows you to specify a broader scoped template than a (product-bound) `instance-template.yaml`. A `system-template.yaml` can reference multiple products, and **Instance Templates** therin to create systems containing of many instances from different products.

```yaml system-template.yaml
name: Test System
description: "A test system with both demo and chat product"

systemVariables: <1>
  - id: test.system.var
    description: "A test system variable"
    value: testValue
    validateRegex: ^[a-zA-Z]+$

templateVariables: <2>
  - id: node-base-name
    name: "The node base name"
    defaultValue: "Node"

instances:
  - name: "Demo Instance" <3>
    description: "The Test System"s Demo Instance"
    productId: "io.bdeploy/demo"
    productVersionRegex: "3\\..*"
    initialProductVersionRegex: "2\\.."
    templateName: "Default Configuration"
    autoStart: false
    autoUninstall: true
  - name: "Chat Instance"
    description: "The Test System's first Chat Instance"
    productId: "io.bdeploy/chat-app"
    fixedVariables: <4>
      - id: app-name
        value: "{{T:node-base-name}}"
    templateName: "Default Configuration"
    defaultMappings: <5>
      - group: "Chat App"
        node: "{{T:cell-base-name}}"
```

1. The single core artifact created on **BDeploy** using a **System Template** is - of course - a **System**. It is possible to define an arbitrary amount of [System Variables](/user/instancegroup/#system-variables) using the template.
2. Template variables in **System Templates** work similar to template variables in [`instance-template.yaml`](#instance-templateyaml). They can be used within the `system-template.yaml` in instance names, `fixedVariables` and `defaultMappings`.
3. A simple instance reference **must** consist of a `name`, a `productId` and a `templateName`, meaning "create an instance with name `name`, from the product `productId` using the template `templateName`".
4. `fixedVariables` allows you to specify a "fixed" value for template variables used in the referenced **Instance Template**. This will skip querying the user for a value for that variable, and instead use this fixed value.
5. `defaultMappings` can be used to pre-assign **Instance Template** groups to available nodes. If a node with the specified name is not available during application of the **System Template**, no node will be preselected by UI and an exception will be thrown by CLI. To specify the **Client Application** node, you can either specify 'Client Applications' or the internal name '\_\_ClientApplications'.

!!!info Note
When applying a **System Template** from the CLI, all mappings need to be provided through `defaultMappings`, and all **Template Variables** of the **System Template** as well as downstream **Template Variables** (required by the individual **Instance Templates**) need to either have a default value or be provided using `fixedVariables`, as the CLI is non-interactive when applying **System Templates**
!!!

### Supported `systemVariables` Attributes

| Attribute         | Description                                                                                                                                                                      |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`              | The unique ID of the system variable to create.                                                                                                                                  |
| `value`           | The pre-assigned value of the variable.                                                                                                                                          |
| `description`     | A human readable description explaining the purpose of each variable.                                                                                                            |
| `type`            | Type of system variable. This defines the type of input field used to edit the variable. Available are `STRING`, `NUMERIC`, `BOOLEAN`, `PASSWORD`, `CLIENT_PORT`, `SERVER_PORT`. |
| `groupName`       | An optional group name. The configuration UI may use this information to group variables with the same group name together.                                                      |
| `fixed`           | Whether the system variable is fixed. This means that the variable can **not** be changed by the user.                                                                           |
| `suggestedValues` | An optional list of suggested values for variable of type STRING (the default). The Web UI will present this list when editing the variable value.                               |
| `customEditor`    | Reserved, currently not supported.                                                                                                                                               |
| `validateRegex`   | An optional regular expression that will be used to validate input on UI system variables configuration form (not applicable to `BOOLEAN` type).                                 |

### Supported `templateVariables` Attributes

!!!info Note
`templateVariables` follows the same scheme as [supported `templateVariables` attributes](#supported-templatevariables-attributes) in [`application-template.yaml`](#application-templateyaml) files.
!!!

!!!info Note
Defined `templateVariables` can be used in each `instances` `name`, `description` and `defaultMapping` `node` attributes. `fixedVariables` on each `instances` value can be used to propagate values further down the line into [`instance-template.yaml`](#instance-templateyaml).
!!!

### Supported `instance` Attributes

Each element provides a description of an instance to be created from a specific product and a specific instance template.

| Attribute                    | Description                                                                                                                                                                                                                                                                                                                                                      |
|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`                       | The name of the instance to create. May use **Template Variables** from the **System Template**.                                                                                                                                                                                                                                                                 |
| `description`                | Describes the purpose or meaning of the to-be-created instance.                                                                                                                                                                                                                                                                                                  |
| `productId`                  | The **ID** of the product to be used. Note that this is not the **Name**. It corresponds to the `product` field in [`product-info.yaml`](#product-infoyaml).                                                                                                                                                                                                     |
| `productVersionRegex`        | An optional regular expression which is used to is used to filter newer product versions for product update suggestions. Serves as initialProductVersionRegex, if initialProductVersionRegex is not provided.                                                                                                                                                    |
| `autoStart`                  | Whether the instance should be started automatically when starting the minion(s). If present, will override the value from the instance template. This parameter is optional, and if not specified in either template, will have the value `false`.                                                                                                              |
| `autoUninstall`              | Whether to schedule background uninstallation of old instance versions. This parameter is optional, and if not specified in either template, will have the value `true`.                                                                                                                                                                                         |
| `initialProductVersionRegex` | An optional regular expression which narrows down allowable versions of the specified product when creating an instance from a template. The newest matching version will be used to create the instance. Useful in case multiple versions of a product exist on a server, and only a certain one is desired. Otherwise, the newest product version is selected. |
| `templateName`               | The name of the **Instance Template** to apply to create this instance. This template must exist in the selected product version.                                                                                                                                                                                                                                |
| `defaultMappings`            | Pairs of `group` and `node` attributes which specify which **Instance Template** `group` should be applied to which node. In case the specified node does not exist on the target server, the mapping is unset by UI and an exception is thrown by CLI.                                                                                                          |
| `fixedVariables`             | Pairs of `id` and `value` attributes which set **Template Variables** of the referenced **Instance Template** to a fixed value instead of querying a value from the user during application.                                                                                                                                                                     |

## product-validation.yaml

The `product-validation.yaml` file contains references to all files that should be sent to the server when performing a product pre-validation. This validation can be used to verify that the product and all its applications contain valid **BDeploy** YAML.

The content of this file is very straight forward:

```yaml product-validation.yaml
product: product-info.yaml

applications:
  my-application: app1/src/main/dist/app-info.yaml
  other-application: app2/src/main/dist/app-info.yaml
```

This file can be passed to the `remote-product-validation` CLI command, as well as to the `BDeployValidationTask` Gradle task.

# Building a Product

Now that you have a well-defined **Product** with one or more **Applications**, you will want to build/package that **Product** to be usable with **BDeploy**.

## Via ZIP File and Web UI

The well-defined **Product** directory including **Applications** can be zipped and imported directly from the web interface.

The following conditions must be fulfilled for a successful import:

- ZIP files must be self-contained, e.g. only relative paths are allowed and no leaving of the zipped structure via ".." paths.
- YAML files must follow standard naming (product-info.yaml).

## Via CLI

Once you have a `product-info.yaml` with it's `product-version.yaml` and all the `app-info.yaml` files in their respective **Application** directories, you can use the CLI to import the product as a single piece.

- Use `bdeploy product` to import the product by specifying a local **BHive** and the `product-info.yaml` to import from.
- Use `bhive push` to push the resulting **Product Manifest** from the local **BHive** to an **Instance Group** on a remote **BDeploy** server.

## Via Gradle

**BDeploy** provides a [**Gradle** plugin](https://plugins.gradle.org/plugin/io.bdeploy.gradle.plugin). This plugin can be used to build a product out of your application.

Given a sample Java application which has been created from the default gradle template using `gradle init`, these are the changes you need to build a **BDeploy** product for this single application. For this demo, the application is named `test`.

!!!info Note
Add the below code to your _existing_ `build.gradle`.
!!!

```groovy build.gradle
plugins {
  ...
  id 'io.bdeploy.gradle.plugin' version '3.1.1-1' <1>
}

version = '1.0.0-SNAPSHOT' <2>

ext { <3>
  buildDate = new Date().format('yyyyMMddHHmmss')
  buildVersion = project.version.replaceAll('SNAPSHOT', buildDate)
}

task validateProduct(type: io.bdeploy.gradle.BDeployValidationTask, dependsOn: installDist) { <4>
  validationServer {
    useLogin = true
  }

  validationYaml = file('bdeploy/product-validation.yaml')
}


task buildProduct(type: io.bdeploy.gradle.BDeployProductTask, dependsOn: installDist) { <5>
  repositoryServer {
    useLogin = true
  }

  product {
    version = project.ext.buildVersion
    productInfo = file('bdeploy/product-info.yaml')

    applications {
      test {
        yaml = new File(installDist.destinationDir, 'app-info.yaml')
      }
    }

    labels.put('buildDate', project.ext.buildDate)
  }
}

task zipProduct(type: io.bdeploy.gradle.BDeployZipTask, dependsOn: buildProduct) { <6>
  of buildProduct
  output = new File(buildDir, 'product-' + project.ext.buildVersion + '.zip');
}


task pushProduct(type: io.bdeploy.gradle.BDeployPushTask, dependsOn: buildProduct) { <7>
  of buildProduct

  target.servers {
    myServer { <8>
      useLogin = true
      instanceGroup = project.getProperty('instanceGroup')
    }
  }
}

...
```

1. Applies the plugin **BDeploy** gradle plugin.
2. Sets the project version. **Gradle** does not strictly require a version, and uses 'unspecified' as default. **BDeploy** requires _some_ sort of version, and setting it for the whole project is good practice.
3. Calculate a build date, which will be substituted instead of the `SNAPSHOT` in the version. This is optional, you could just plainly use the version set. The actual `buildVersion` used later when building the product is derived from the project version and the `buildDate`.
4. The `BDeployValidationTask` can be used to validate product information before actually building the product. The [`product-validation.yaml`](#product-validationyaml) file must contain a reference to the `product-info.yaml` used, as well as references to all `app-info.yaml` files.
5. This task will actually build the product with the configured version. The actual data about the product is loaded from `bdeploy/product-info.yaml`, which we will create in a second. Note that this task depends on `installDist`, which will unpack the binary distribution of the application in this project into a folder, so **BDeploy** can import the individual files. Depending on the type of application and the way it is built, there might be different ways to achieve this.  
   The `repositoryServer` will be queried for additionally specified `runtimeDependencies` at build time. Those dependencies will be downloaded and embedded into the final product.
6. If `buildProduct` built a product, this task will package it as a ZIP file. Note that a ZIP will always contain _all of_ the product, whereas `pushProduct` can push only required deltas which are not present on the target server.
7. The `pushProduct` task can push required deltas to one or more configured target servers. The server configuration is the same as for all other `..Server` blocks (see note below). In addition the target `instanceGroup` **must** be specified for pushing.
8. Multiple target servers can be specified in the `target.servers` section. The plugin will push to each of them.

!!!info Note
All `...Server` blocks can set `useLogin = true` to use local logins created on the system using the `bdeploy login` command. You can provide a login name using `login = xx`, or specify `uri` and `token` instead of `useLogin` to have full control. Where `useLogin` is valid, `loginStorage` can also be used to set a different directory where **BDeploy** logins are stored and can be read from.
!!!

Next we need the required descriptors for the product and the application. For this sample, the information will be the bare minimum, please see [`app-info.yaml`](#app-infoyaml) and [`product-info.yaml`](#product-infoyaml) for all supported content.

Lets start off with the [`app-info.yaml`](#app-infoyaml), which describes the `test` application.

!!!info Note
This file **must** be part of the binary distribution of an application and reside in its root directory. To achieve this, the simplest way (using the gradle `application` plugin) is to put the file in the subdirectory `src/main/dist` in the project folder.
!!!

```yaml src/main/dist/app-info.yaml
name: Test Application

supportedOperatingSystems: <1>
  - LINUX
  - WINDOWS

startCommand:
  launcherPath: "{{M:SELF}}/bin/test{{WINDOWS:.bat}}" <2>
```

1. By default, the **BDeploy** plugin will make this application available for **all** the supported platforms specified in `app-info.yaml`. If required (usually it is not) you can configure a _different_ set of Operating Systems to build for in the `test` application configuration in `build.gradle` by adding a set of operating system literals (e.g. 'WINDOWS', 'LINUX') to the `os` list of the application.
2. This demo `app-info.yaml` only defines the path to the launcher, which for this demo project (named `test`) is `bin/test` on `LINUX`, and `bin/test.bat` on `WINDOWS`.

Finally, we need a [`product-info.yaml`](#product-infoyaml) describing the product itself. We'll put this file into a `bdeploy` subfolder. This is not required, it can reside anywhere in the project. You just need to adapt the path to it in the `build.gradle`.

!!!info Note
The reason why you want to put this file into a separate folder is because it allows to reference various other files by relative path. Those files (and folders) must reside next to the [`product-info.yaml`](#product-infoyaml). Over time this can grow, and may clutter your source folders if you do not separate it.
!!!

```yaml bdeploy/product-info.yaml
name: Test Product
product: io.bdeploy/test <1>
vendor: BDeploy Team

applications:
  - test <2>

versionFile: product-version.yaml <3>
```

1. This is the unique ID of the product. This is basically a 'primary key' and should not change over time.
2. The [`product-info.yaml`](#product-infoyaml) needs to list included applications. These applications also need to be available from the `product-version.yaml`.
3. The `versionFile` parameter **must** be set. If the relative path given here does **not** exist, the **BDeploy** **Gradle** plugin will generate this file for you, using the given version and applications. Otherwise you can provide this file and have more manual control over applications. In case the plugin generates the file for you, it will be deleted right after the build.

That's all that is required to build a product. You can now run `./gradlew zipProduct` on the CLI to try it out. The result will be a `build/product-1.0.0-XXX.zip` where `XXX` is the `buildDate` we set previously. The content of the ZIP file is a **BHive**, which is the internal data format used by **BDeploy**. You can upload this product to any **BDeploy** server using its Web UI.

!!!info Note
The `build` folder also contains the **BHive** in unzipped form in the `build/productBHive` folder. This folder is temporary but will hold all product versions built since the last `./gradlew clean`. You can use this **BHive** for manual pushing.
!!!

!!!info Note
The `pushProduct` task will do the same thing (build the product) but then push it to a target server. For this, you need to specify the `server`, `token` and `instanceGroup` project properties to match your setup. You can get a token by using the `Create Token...` action on the user menu in **BDeploy**. Make sure to create a _full token pack_ for this tool to work.
!!!

!!!warning Warning
Using `./gradlew clean buildProduct` you can build the **same** product version over and over again. However once pushed to a remote server, the same product version **must not** be reused. If you try to build and push the **same** version more than once, the server will silently ignore your attempt to push, as it assumes that it already has all the content (it has a product with this version already, and all artifacts are assumed to be immutable in **BDeploy**).
!!!

## Via Eclipse TEA

**BDeploy** provides integration into [Eclipse TEA](https://www.eclipse.org/tea/). Using this integration, you can easily export **Eclipse RCP** based products as **Applications** and bundle them into a custom **Product**.

Once you have required files, select **TEA > TEA Build Library > Build BDeploy Product...**. You will be prompted which **Product** to build and where to put the resulting product. You can choose to create a self-contained ZIP, or to push deltas to a selected server.

:::{align=center}
![TEA Integration Product Build](/images/TEA_build_product.png){width=480}
:::

You can configure multiple servers by using the [ **Add** ], [ **Delete** ] and [ **Edit** ] buttons.

:::{align=center}
![TEA BDeploy Server configuration](/images/TEA_edit_server.png){width=480}
:::

Enter a description and a URL. You will then be able to use the [ **Login** ] button to create a token for the server.

:::{align=center}
![TEA BDeploy Login](/images/TEA_login.png){width=480}
:::

Now you can use the [ **Load Groups** ] to fetch a list of existing instance groups from the server to choose from. Finally, use the verify button to check whether the entered information is correct.

When confirming the build dialog, on first run you will be prompted to login to the Software Repositories **BDeploy** server configured in the TEA **BDeploy** preferences.

Since product builds are stored in the workspace, you can choose to re-push a previous build of the product (to the same or another server). Select **TEA > TEA Build Library > Push BDeploy Product...** to do so. You will be presented a list of available local product versions and the configured **BDeploy** servers.

:::{align=center}
![TEA Integration Product Push](/images/TEA_push_product.png){width=480}
:::

### `products.yaml`

!!!info Note
There is no actual requirement for the file to be named `products.yaml`. This is just the default, but you can specify another name in the Eclipse TEA preferences.
!!!

This file is required and lists the [`product-build.yaml`](#product-buildyaml) files which are available to the integration.

```yaml products.yaml
products:
  "Product One": "prod-1-build.yaml"
  "Product Two": "prod-2-build.yaml"
```

The path to the `products.yaml` has to be configured in the **Eclipse TEA** preferences.

:::{align=center}
![TEA Integration Products Preference](/images/TEA_preferences_products.png){width=480}
:::

The preferences also allow to configure a **BDeploy** server whos [Software Repositories](/power/runtimedependencies/#software-repositories) are used during resolution of [Runtime Dependencies](/power/runtimedependencies/#runtime-dependencies). You will be asked to log into this server once when starting a product build.

### `product-build.yaml`

This file references a [`product-info.yaml`](#product-infoyaml) file and describes how to build the actual applications referenced in the `product-info.yaml`.

```yaml product-build.yaml
productInfoYaml: my-prod-info.yaml

applications:
  - name: my-app1
    type: RCP_PRODUCT
    includeOs: [WINDOWS, LINUX]
    application:
      product: App1ProdBuild

  - name: my-app2
    type: RCP_PRODUCT
    includeOs: [WINDOWS, LINUX]
    application:
      product: App2ProdBuild
```

The value for `applications.application.product` is **Eclipse TEA** specific and references the **Eclipse TEA** product _alias_ property.
