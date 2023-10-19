---
order: 4
icon: device-desktop
---
# Development Setup

BDeploy uses the **Eclipse IDE** for backend- and **VSCode** for frontend-development. You will need:

* Get the latest [OpenJDK 17](https://adoptium.net/).
* Get the latest [Eclipse IDE](https://www.eclipse.org/downloads/) including Java 17 support.
    * You need to have the [Launch Configuration DSL (LcDsl)](https://marketplace.eclipse.org/content/launch-configuration-dsl) extension installed.
* [VSCode](https://code.visualstudio.com/download)
    * You need to have the [Angular Essentials](https://marketplace.visualstudio.com/items?itemName=johnpapa.angular-essentials) extension installed.
* [Node.js](https://nodejs.org/de) including npm, currently 18.x LTS.
    * Some people prefer to use [NVM](https://github.com/nvm-sh/nvm)
* [GIT](https://git-scm.com/downloads)

## Environment Setup

* You will want to set the `JAVA_HOME` environment variable to point to the path where you extracted the **OpenJDK 17** package to assure that the build picks up the correct Java.
* Make sure that the `npm` command works from the command line where you will run `gradle` builds (`cmd.exe` on Windows, `bash` on Linux).
* Install the **Angular CLI** globally for easier working by running `npm install -g @angular/cli`.

!!!info Note
You need root permissions on Linux if you installed NodeJS (and NPM) through your distribution to be able to globally install the **Angular CLI**.
!!!

## Repository and Gradle Build

This documentation will assume the path `/work/bdeploy` to be available, if not substitute it with any other path that works for you.

* Clone the repository: `cd /work/bdeploy && git clone https://github.com/bdeployteam/bdeploy.git`
* Change to the repository directory: `cd bdeploy`
* Start the `gradle` build
    * `./gradlew build`
    * The build should conclude with a `BUILD SUCCESSFUL` message

The `gradle` build will build test and package BDeploy. You can find build artifacts after the build here:

* `./launcher/build/distributions` - Distributable packages of the launcher application which is used to start client applications.
* `./minion/build/distributions` - Distributable packages of the main BDeploy binaries. They contains the **start** (which runs **BDeploy** **master** and **node** servers) command as well as all CLI commands available to BDeploy Administrators. The distribution also contains all supported launcher packages as nested ZIP files.
* `./interfaces/build/libs/bdeploy-api-**-all.jar` - The distributable API bundle including all external dependencies. This can be used to create additional integrations for BDeploy.

Additionally, documentation deliverables can be found in the `./doc/build/docs/dev` and `./doc/build/docs/user` directories (developer and end-user documentation respectively).

## Eclipse Workspace

To be able to build, start and debug BDeploy backend applications from the Eclipse IDE, you need to perform some extra setup steps:

* On the command line (see [Repository and Gradle Build](/devenv/#repository-and-gradle-build)) generate the **Eclipse IDE** project files by running `./gradlew eclipse`.
* Start the **Eclipse IDE** - choose a workspace, e.g. `/work/bdeploy/workspace`.
* Open the **Git Repositories** view.
* Click **Add existing local repository**, browse to the repository location (e.g. `/work/bdeploy/bdeploy`) and add it.
* Right click the repository in the **Git Repositories** view and select **Import Projects...**.
    * Select all projects with type **Eclipse project**
    * **Don't** select the projects in the _eclipse-tea_ folder.
    * **Don't** select projects of type **Maven**.

This results in a complete BDeploy workspace setup which compiles automatically.

### Running BDeploy from Eclipse

BDeploy uses **LcDsl** launch configurations to run binaries. You can find launch configurations in the **Launch Configurations** view.

* Find and run (hint: right click) the `Master-Init` launch configuration.
    * This will initialize a BDeploy **master root** inside the chosen workspace directory, e.g. `/work/bdeploy/workspace/runtime/master`.
    * A default user will be created: username = admin, password = admin.
* Finally find and run the `Master` launch configuration to spin up a BDeploy **master**.

!!!info Note
The BDeploy master will host the Web UI also when started from the **Eclipse IDE**, but it will not work due to slightly different setup. You must use **VSCode** to host a matching Web UI for the backend run from **Eclipse**.
!!!

## Running BDeploy's Web UI from VSCode

To spin up a matching frontend for the master started in [Running BDeploy from Eclipse](/devenv/#running-bdeploy-from-eclipse) you need to start the **Angular** application from within **VSCode**.

* On the command line, navigate the the `./ui/webapp` directory in the repository (e.g. `/work/bdeploy/bdeploy/ui/webapp`) and run **VSCode** in the current directory using `code .`
* Open a terminal in **VSCode** and run `ng serve` to start the **Angular** development server. This will take a while to compile the Web UI.
    * The terminal can be opened using **Terminal** > **New Terminal**.
    * The application will be started at [http://localhost:4200](http://localhost:4200) by default.

!!!warning Warning
BDeploy's backend is HTTPS **only**. It uses a self-signed certificate by default. This will require to accept the certificate in the browser before **any** communication (especially from within the Web UI) can happen.

This makes it necessary to open a second tab in the browser and navigate to [https://localhost:7701](https://localhost:7701) to accept the security exception before the Web UI can communicate with the backend properly. Note that this URL will also load the Web UI once the security exception is in place, but will fail to start (see note above).
!!!

## Building for other platforms

You can build distribution packages for other platforms by installing their respective JDKs. You need to specify those JDKs as properties during the build. To simplify the process, you can create these entries in `~/.gradle/gradle.properties`:

```properties ~/.gradle/gradle.properties
systemProp.win64jdk=/path/to/jdks/windows/jdk-17.0.1+12
systemProp.linux64jdk=/path/to/jdks/linux/jdk-17.0.1+12
#systemProp.mac64jdk=/path/to/jdks/mac/jdk-17.0.1+12/Contents/Home
```

!!!info Tip
Of course you need to download those JDKs and adapt the paths to your environment.
!!!