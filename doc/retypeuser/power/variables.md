---
order: 3
icon: arrow-both
---

# Variable Expansions

**BDeploy** provides a mechanism for variable expansion, used in [Link Expressions](/user/instance/#link-expressions) and [Configuration Files](/user/instance/#configuration-files). Variable expansion can happen at the following locations:

- Launcher path specified in [`app-info.yaml`](/power/product/#app-infoyaml)
- Any parameter value - either user-set value or the `defaultValue` specified in [`app-info.yaml`](/power/product/#app-infoyaml)
- Any configuration file content
- Most of the [HTTP Endpoints](/power/product/#supported-endpointshttp-attributes) attributes
- Instance Variables
- System Variables (limited to certain expansions which are available globally)

Any of the above will be processed _as late as possible_, i.e. on the target node, right before writing the final content to the target directories.

The general syntax for variables is `{{TYPE:VARNAME:SUBVAR}}`. There are various types, usually denoted by a single character. The following section gives an overview of types, variables, and possible sub-variables. Additionally, limited arithmetic expressions (only `+` and `-`) can be added as last component, e.g. `{{X:myvar:+3}}`. Arithmetic expressions may not be available depending on the data type used at the specific location (e.g. not available if the variable in question is of type `STRING`).

!!!info Note
**BDeploy** has a _link expression editor_ built in. All elements in the UI (except the configuration file editor) can be switched from _plain value_ editor to _link expression_ editor, which gives rich content assist for link expressions (which contain one or more variable expansions). The configuration file editor uses rich content assist by default on [ **CTRL** ] + [ **Space** ] if the current word starts with `{{`.
!!!

## M: Manifest Reference

Used whenever it is required to get the absolute installation path to another manifest’s path on the target system. The name of the variable indicates the manifest which should be referenced. An optional tag - separated with a ':' - can be added to refer to a specific version.

```
{{M:<Name of Manifest>:<Optional Manifest Tag>}}
```

| Variable            | Description                                                                                                                                                                                                                 |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| {{M:adoptium/jre8}} | Expands to the absolute path where the the manifest named 'adoptium/jre8' is installed on the server. The exact version is specified through a _runtime dependency_ in the [`app-info.yaml`](/power/product/#app-infoyaml). |
| {{M:SELF}}          | Expands to the absolute path where the manifest is installed on the server which contains the `app-info.yaml` for this application. This substitution shortcut is only supported in `app-info.yaml` files.                  |

## P: Deployment Path

Used to expand to one of the special directories that are defined.

```
{{P:<PATH_ID>}}
```

| Variable                      | Description |
| ----------------------------- | --- |
| {{P:ROOT}}                    | Root directory of the application. This is the parent directory of DATA and BIN. |
| {{P:DATA}}                    | Directory shared by multiple deployments of the same instance. |
| {{P:BIN}}                     | Directory where all binaries are stored. |
| {{P:CONFIG}}                  | Directory where all configuration files are stored. |
| {{P:RUNTIME}}                 | Directory with runtime data (e.g. stdout/stderr capture files) is stored. |
| {{P:INSTANCE_MANIFEST_POOL}}  | Directory where applications that cannot be pooled over multiple instances are pooled. |
| {{P:MANIFEST_POOL}}           | Directory where applications that can be pooled over multiple instances are pooled. |
| {{P:LOG_DATA}}                | Configurable directory for log files on the server. The log data directory is shared by all instances, however, each instance has its own subdirectory therein. This expansion references the unique subdirectory applicable for the current instance. Configuration can be done via the [cli](/experts/cli/#initialization-and-local-configuration-management-commands). If not configured, the expansion defaults to be equal to {{P:DATA}}. |

## V: Parameter Value

Used to reference a parameter within the same application or withing any other application from the same instance. Typically used when one application publishes a service on a specific port (_server_) and other applications (_clients_) should then connect to this port. The configuration of the _client_ applications would then refer to the port of the _server_ application. Thus, when changing the port, only the configuration of the _server_ application has to be edited.

```
{{V:<PARAM_ID>}} - Refers to a parameter defined in the same application
{{V:<APP_NAME>:<PARAM_ID>}} - Refers to a parameter defined in the application with the given name
```

| Variable                   | Description                                                                                     |
| -------------------------- | ----------------------------------------------------------------------------------------------- |
| {{V:my.param.id}}          | Takes the value from the _my.param.id_ parameter defined in the same application.               |
| {{V:MyServer:my.param.id}} | Takes the value from the parameter _my.param.id_ that is defined in the _MyServer_ application. |

!!!warning Warning
Beware that changing the name of an application will break the parameter reference mechanism. There is no mechanism that automatically adapts the configuration of applications which refer to values of other applications. This must be done manually if required.
!!!

## I: Instance Value

Used to expand to values related to the instance containing the parameter's process.

```
{{I:<VAR>}}
```

| Variable                      | Description                                                                       |
| ----------------------------- | --------------------------------------------------------------------------------- |
| {{I:SYSTEM_PURPOSE}}          | The purpose of the instance. Allowed values: `PRODUCTIVE`, `TEST`, `DEVELOPMENT`. |
| {{I:ID}}                      | The ID of the instance.                                                           |
| {{I:TAG}}                     | The tag (i.e. 'version') of the instance.                                         |
| {{I:NAME}}                    | The name of the instance.                                                         |
| {{I:PRODUCT_ID}}              | The name of the 'MANIFEST' keys name of the configured product.                   |
| {{I:PRODUCT_TAG}}             | The tag (i.e. 'version') of the configured product.                               |
| {{I:DEPLOYMENT_INFO_FILE}}    | The path to the deployment info file.                                             |

## A: Application Value

Used to expand to values related to the application containing the parameter's.

```
{{A:<VAR>}}
```

| Variable   | Description                  |
| ---------- | ---------------------------- |
| {{A:ID}}   | The ID of the application.   |
| {{A:NAME}} | The name of the application. |

## H: Minion Properties

Used to expand to properties of the minion where the application is deployed.

```
{{H:<VAR>}}
```

| Variable       | Description                                                                     |
| -------------- | ------------------------------------------------------------------------------- |
| {{H:HOSTNAME}} | Expands to the hostname of the target minion where the application is deployed. |

!!!warning Warning
Beware that due to the nature of variable expansion (the point in time this happens), `HOSTNAME` may not be what you expect, _especially_ on global parameters used by multiple processes (it can be a different hostname for each process, if they are configured to different nodes). Even more precaution is required when using `HOSTNAME` on client applications, as it will expand to the _clients_ hostname.
!!!

## Operating System

Enables conditional output of text based on the current operating system. The name of the variable refers to the name of the operating system. Using
this variable allows the usage of different arguments for different operating systems while still using a single YAML file.  
Accepted values are: WINDOWS, LINUX, LINUX_AARCH64

```
{{OSNAME:<conditional output>}}
```

| Variable             | Description                                             |
| -------------------- | ------------------------------------------------------- |
| {{LINUX:java}}       | Expands to _java_ on _Linux_.                           |
| {{WINDOWS:java.exe}} | Expands to _java.exe_ on _Windows_.                     |
| java{{WINDOWS:.exe}} | Expands to _java_ on Linux and _java.exe_ on _Windows_. |

## ENV: Environmental Values

Enables access to environmental variables defined in the operating system. The name of the variable refers to the name of the environmental variable.

| Variable            | Description                                                                                                     |
| ------------------- | --------------------------------------------------------------------------------------------------------------- |
| {{ENV:MY_VARIABLE}} | Expands to the value of the environmental variable when the application is **installed** on the node or client. |

Variables are replaced with their actual values when the process is installed on the target minion node. This might not always be desired. Especially for client applications it can be useful to do the actual replacing when the process is launched. This is can be achieved by prefixing the actual variable with the [DELAYED](#delayed-delaying-evaluation) prefix. This enables that different users of the client application are getting different parameter values depending on the value of the environmental variable.

## X: Instance and System Variables

Used to expand to instance and system variables. The same prefix is used for both. System Variables take precedence over Instance Variables in expansions, if both have a variable with the same ID.

```
{{X:<VAR>}}
```

[Instance Variables](/user/instance/#instance-variables) are defined in the [Instance Settings](/user/instance/#instance-configuration) and [System Variables](/user/instancegroup/#system-variables) are defined on [Systems](/user/instancegroup/#systems) level in the **Instance Group**.

## IF: Conditional Expansion

The conditional expansion can be used to convert a boolean expression to any given value. The format of the conditional is

```
{{IF:condition?value-if-true:value-if-false}}
```

The condition is any other variable expansion _without_ the curly braces, like for instance `X:MyVar` to resolve the instance variable `MyVar`. The result of the expression is expected to be boolean, however non-empty strings will be treated as `true` as well. The values the expression expands to can be of arbitrary type, and need to be plain values, i.e. they can _not_ contain variable expansions.

Some examples:

```
{{IF:V:use-auth?SchemeBasic:SchemeNone}}
{{IF:X:instance-bool?ValueIfTrue:ValueIfFalse}}
```

## DELAYED: Delaying Evaluation

| Variable               | Description                                                                                      |
| ---------------------- | ------------------------------------------------------------------------------------------------ |
| {{DELAYED:<variable>}} | Expands to the value of the variable when the application is **launched** on the node or client. |

For example:

| Variable                    | Description                                                                                                     |
| --------------------------- | --------------------------------------------------------------------------------------------------------------- |
| {{ENV:MY_VARIABLE}}         | Expands to the value of the environmental variable when the application is **installed** on the node or client. |
| {{DELAYED:ENV:MY_VARIABLE}} | Expands to the value of the environmental variable when the application is **launched** on the node or client.  |

## FILEURI:

Takes a path and transforms it into a file URI. This transformation is operating system dependant.

| Variable  | Description                                    |
| --------- | ---------------------------------------------- |
| {{FILEURI:}}  | Transforms the given path into a file URI. |

## Escaping Special Characters

Allows escaping of special characters.

| Variable  | Description                                       |
| --------- | ------------------------------------------------- |
| {{XML:}}  | Escapes characters that could corrupt XML files.  |
| {{JSON:}} | Escapes characters that could corrupt JSON files. |
| {{YAML:}} | Escapes characters that could corrupt YAML files. |
