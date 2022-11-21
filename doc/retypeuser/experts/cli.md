---
order: 3
icon: terminal
---
# Command Line Interface

**BDeploy** provides a rich CLI which allows automating most interactions with **BDeploy**, e.g. for use in build pipelines, etc.

The same binaries used to run a **BDeploy** server provide a set of _commands_ along with available _options_. This chapter describes the available _commands_. The _options_ are well-described when calling the according _command_ with the `--help` _option_.

## Common Options

Option | Description
---    | ---
`-q` | Quiet - no progess reporting on long running operations.
`-v` | Verbose - some tools may include additional information.
`-vv` | Very verbose - enable progress reporting for every tool, show timing summary at the end.
`-o FILE` | Redirect output to the given `FILE`.
`-op FILE` | Redirect progress reporting output to the given `FILE`.
`--csv` | Output any data in CSV format.
`--json` | Output any data in JSON format.

## BDeploy CLI

### Initialization and local configuration management commands

Command | Description
---    | ---
`certificate` | Manage the server certificate (export the existing, import a new). This is mainly used to import a properly signed certificate to the _master_, which serves the Web UI using this certificate over HTTPS. **BDeploy** itself does not require a properly signed certificate for internal operation to guarantee [Security](/experts/security/#security).
`cleanup` | Manage the schedule at which the _master_ performs background cleanup operations on all _nodes_ (including himself).
`init` | Initializes a _root_ directory for running a server with the `start` command. The init command can be instructed to initialize the directory to run a **master** or a headless **node** when running the `start` command.
`config` | Allows changes to the basic configuration like hostname, port or mode. The `init` command stores the given hostname, used later on for connections to self and also for variable expansion. If the hostname changed or is no longer valid, this tool can update it. Please refer to [Migrating between Modes](/user/central/#migrating-between-modes) in case you need to change the minion mode.
`storage` | Manages available _storage locations_. A _storage location_ is a folder where the **BDeploy** _master_ puts the **BHives** required to store data for **Instance Groups** and **Software Repositories**.

### Local session and scripting commands

Command | Description
---     | ---
`login` | Manages local login sessions to remote servers. This command eliminates the need to manage URIs and tokens yourself. Login sessions are managed per user, can be created, switched and removed. Validity of the login session is the same as for the Web UI. Subsequent commands will use the configured remote login session to perform remote communication.<br/><br/>:warning:**WARNING** Since login sessions are persistent, it can be easy to confuse which session/server is currently worked with. Always verify that the correct session is actively used by commands when performing modifications on the server, e.g. deleting an **Instance**.
`shell` | Provides an interactive shell which can execute tools. The shell can also be fed with a script file which contains a series of commands.

### Product management commands

Command | Description
---     | ---
`product` | List or create **Products** locally. Fetches required dependencies from the given remote **BDeploy** server. Has the ability to push the resulting **Product** to the target **BDeploy** server.

### Remote server management commands

Command | Description
---     | ---
`remote-central` | Manage remote configuration related to _managed_ servers on a _central_ server. Also allows to attach _managed_ servers to a _central_ server.
`remote-data-files` | Remotely manage data files for a given instance.
`remote-deployment` | Manage **Instance** deployment (_install_, _activate_, _uninstall_, _updateTo_ (product version)) on a remote **BDeploy** server.<br/><br/>:information_source:**NOTE** The `--updateTo` command will only work if the new product version can be updated to without manual changes. If for example a mandatory parameter is added, or a configured application is removed from the product, the update will fail.
`remote-group` | Manage **Instance Groups** on a remote **BDeploy** server.
`remote-instance` | Query and manage **Instances** on the remote **BDeploy** server. Can be used to **export** (to _ZIP_) and **import** (from _ZIP_) **Instances** locally.
`remote-master` | Query and manage system information on a remote **BDeploy** server. Allows to update both the **BDeploy** system software as well as the **BDeploy** launcher binaries.
`remote-node` | Manage **nodes** attached to the remote **BDeploy** server.
`remote-plugin` | Manage plugins available on the remote **BDeploy** server or install new ones from local `jar` files.
`remote-process` | Query and manage application processes managed by **BDeploy** on a remote **BDeploy** server.
`remote-product` | Query and manage products available on the given **Instance Group** on a remote **BDeploy** server.
`remote-product-validation` | Uses a [`product-validation.yaml`](/power/product/#product-validationyaml) file to perform a remote pre-validation of a product to be built in the future.
`remote-repo` | Query and manage **Software Repositories** on a remote **BDeploy** server.
`remote-system` | Manage systems on a remote **BDeploy** server.
`remote-user` | Manages users on a remote **BDeploy** server.

### Server commands

Command | Description
---     | ---
`start` | Runs the **BDeploy** _minion_ in the mode determined by the given root directory. Requires a root directory initialized with the `init` command.<br/><br/>:information_source:**NOTE** A _master_ is always a _node_ as well. The _master_ just has the additional capability to control other _nodes_, and provides the configuration Web UI.

### Utility commands

Command | Description
---     | ---
`bhive` | Wraps around to the [BHive CLI](/experts/cli/#bhive-cli). Can be used to access **BHive** CLI commands if the **BHive** stand-alone binaries are not available. Usage: `bdeploy bhive <command>`.
`payload` | Internal use only.
`schema` | Used to generate YAML schemas and validate input files against those schemas.
`verify-signature` | Verifies whether the signature on a signed executable is deemed valid.

## BHive CLI

**BHive** is the underlying storage used by **BDeploy**. **BDeploy** serves **BHives** for all minions (_master_ and _node_), and has additional **BHives** per **Instance Group** and **Software Repository** on the _master_.

**BHive** itself is does not know about **BDeploy**, it is 'just' a dumb storage backend (which is responsible for de-duplicated, distributed, fail-tolerant (failure-recoverable) storage of file contents).

Much like Git, **BHive** only knows two commands that actually perform remote communication: `fetch` and `push`. All other commands are performing their work locally.

### Analysis and maintenance commands

Command | Description
---     | ---
`fsck` | Performs a file system check (_fsck_). This involves resolving all inter-**Manifest** dependencies, as well as re-hashing all objects in the underlying storage to assert that all objects in the storage are valid. <br/><br/>Also allows to fix found errors (by deletion). After this, missing **Manifests** must be re-pushed from a **BHive** which still has the required objects.
`manifest` | Manage existing **Manifests** in a given **BHive**.
`prune` | Remove unreferenced objects from the given **BHive** to free up disc space.
`token` | Allows generation of new _access tokens_, see [Security](/experts/security/#security).
`tree` | Read and diff **Manifests** from the given **BHive**. Allows to compare the contents of **Manifests**, view differences and the estimated data transfer required to perform a delta 'update' if a potential remote **BHive** already has one of them.

### Filesystem interaction commands

Command | Description
---     | ---
`export` | Reads a **Manifest** from the given **BHive** and writes it's content to a given target folder.
`import` | Import a given folder into a given **BHive** and associate the given **Manifest** key with it.

### Remote server interaction commands

Command | Description
---     | ---
`fetch` | Fetches the given **Manifests** from a given remote **BHive**.
`push` | Push the given **Manifests** to the given remote **BHive**

### Server commands

Command | Description
---     | ---
`serve` | Serves one or more given **BHives** over the network. The same thing as **BDeploy** does internally, provided as CLI tool for maintenance reasons.

## Launcher CLI

Command | Description
---     | ---
`launcher` | Reads a given `.bdeploy` file, which describes all required information for the launcher to contact a **BDeploy** server and download a _client_ application.
`uninstaller` | Uninstalls a given application and cleans up in the local storage.

## Environment Variables

**BDeploy** and **BHive** CLIs provide a set of environment variables that allow you to provide environment defaults for certain command line arguments.

Each command will include information for the according environment fallback in it's help output, for instance:

```
$ bhive push --help
Help:

Usage: PushTool <args...>
               --token=ARG: Token for the remote access. Can be given alternatively to a keystore.
                            (Environment variable 'BDEPLOY_TOKEN' is used as fallback if not given)
              --remote=ARG: URI of remote BHive. Supports file:, jar:file:, bhive:
                            (Environment variable 'BDEPLOY_REMOTE' is used as fallback if not given)
              ...
```

Variable | Description
---      | ---
`BDEPLOY_LOGIN` | Specifies the name of a stored login session (`bdeploy login`) to use. This overrides the currently active login session if there is one.
`BDEPLOY_REMOTE` | URL to the remote **BDeploy** server which commands should connect to, e.g. `https://hostname:7701/api`.
`BDEPLOY_ROOT` | The root directory to use for `init` and `start` (primarily).
`BDEPLOY_TOKEN` | The actual _security token_ used to access the remote **BDeploy** server.
`BDEPLOY_TOKENFILE` | A file containing the _security token_ (as text content) used to access the remote **BDeploy** server.
`BHIVE` | Path to the **BHive** to operate on for local commands (e.g. `import`, `export`).
`REMOTE_BHIVE` | The name of the remote **BHive**. In case of **BDeploy** this is usually the name of an **Instance Group** or **Software Repository**.
