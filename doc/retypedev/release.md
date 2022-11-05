---
order: 2
icon: package-dependents
---
# Release Procedure

## Preparation

Make sure to run a manual test and check at least this before releasing:

* Setup a fresh server.
* Create an instance with a client application.
* Download the installer for the client and check if it is working as intended.

The rest is covered pretty well by automated tests already.

## Branches, Tags & GitHub Release

!!!info Note
The steps outlined in this chapter are usually encapsuled in a single automated release job (Jenkins, ...).
!!!

Prerequisites:

* Know the version you want to release (i.e. `RELEASE_VERSION`)
* Know the "next" version, which will be the one set as active version **after** the release (i.e. `NEXT_VERSION`).
* A GitHub account (i.e. `GH_USER`) and a token (i.e. `GH_TOKEN`) with the permission to create and update a release on the **BDeploy** GitHub repository.
* A SonaType account (i.e. `SONATYPE_USER`) and a token (i.e. `SONATYPE_TOKEN`) with the permission to upload and release artifacts to `oss.sonatype.org` targeting maven central.
* A GPG Key which is registered with sonatype which can be used to sign the application JAR files for upload to maven. You need the key file (i.e. `GPG_FILE`), the ID of the key (i.e. `GPG_ID`) and the password to the key file (i.e. `GPG_PASS`).
* A clone of the repository - since right now an internal repository is used as well as the GitHub repository, you need a clone of the internal repository
* An empty directory where JDKs can be downloaded to. Set the path in the environment variable `JDK_DL_ROOT`

Steps:

* Set the environment variable with the according data prepared in the prerequisites: `GH_USER`, `GH_TOKEN`, `SONATYPE_USER`, `SONATYPE_TOKEN`, `GPG_FILE`, `GPG_ID` and `GPG_PASS`. Also set `RELEASE_VERSION` and `NEXT_VERSION`, or pass the values directly to the command.
* Execute `./release.sh ${RELEASE_VERSION} ${NEXT_VERSION}` in the repository. Make sure that the repository has the current master branch checked out and that it is ready to be released.

This will:

* Download the most current version of the JDK to be used.
* Set the release version in the source repository.
* Run the build with all tests but not the binary release tests.
* Updates documentation screenshots from the UI tests.
* Publish artifacts to the `oss.sonatyp.org` server.
  * Note: A separate release step has to be performed manually later, see below.
* Commit changed files (version, screenshots, test-data) locally (i.e. 'Release $\{RELEASE_VERSION}').
* Push the commit to the GitHub repositories master branch.
* Publish the built artifacts to a newly created GitHub release.
* Set the next version in the source repository.
* Add the just-published version to the versions to be tested in binary release tests.
* Run the build using the new version without any tests but the binary release test - which will now verify that updating to the "next" version is possible from the just-released release.
* Commit changed file (version, test-data) locally (i.e. 'Update to $\{NEXT_VERSION}').

!!!warning Warning
`release.sh` will push the release commit to GitHub, but will **not** push any commit to the origin of the repository. If the internal repository is used, you need to push there manually. Also the update to `NEXT_VERSION` is not pushed at all, and needs to be pushed separately.
!!!

!!!warning Warning
Before pushing to the internal repository, make sure to execute a `git fetch --all --tags`, since GitHub will create the release tag for us in the GitHub repository automatically.
!!!

## Release notes

While `release.sh` is running, you can gather release notes by looking at the individual commits that happened since the last release. This is most easily done by looking at the output of:

```
git log --no-merges v3.5.0..
```

assuming that `3.5.0` was the last release. Scroll to the bottom of the output and work your way upwards commit after commit. Not **every** commit needs mentioning in the release notes, but quite often nearly every commit ends up in some or another way in the release notes.

## MavenCentral release

After running `release.sh` and pushing all the commits to their respective targets, `oss.sonatype.org` will contain a new staging repositories for the maven artifacts published by the build.

:::{align=center}
![Staging Repository](/images/staging-repo.png){width=480}
:::

You need to:

* Log in to see anything useful.
* Navigate to **Staging Repositories**.
* Select the `iobdeploy-XXXX` repository where XXXX is any number.
* In the lower part of the screen, go to **Content** and check whether the content of the repository looks complete and OK.
* Select the repository in the upper part of the screen and click **Close**.
* Wait a few minutes and refresh the view using the **Refresh** button.
* Once enabled, click the **Release** button while having the repository selected. You can leave the "Drop automatically" checked, this way nothing has to be done after clicking OK anymore.

This will release the new version to maven central. This can take a few minutes, up to half an hour. Also the maven central index can take up to 24 hours to refresh - this is what is used to display data on the maven central homepage. Thus it may be that you cannot find the new version on the homepage, but can already download it using Maven/Gradle.

## Documentation Update

After the release has been made, we need to update the documentation on the official homepage.

Prerequisites:

* The **BDeploy** main repository, having the `Release X.X.X` release commit/tag checked out (!).
* A clone of the official **BDeploy** homepage repository.

In the **BDeploy** source repository, change into the `doc` directory and run

```
../gradlew build
```

This will create the documentation artifacts in the `build/docs/` subdirectory, `dev` and `user` for the developer and user documentation respectively.

Change in each directory, open `index.html` and verify that the correct release version number can be seen on the documentation index.

Change to the **BDeploy** homepage repository and delete the `dev` and `user` directories completely.

Copy the `dev` and `user` directories from the **BDeploy** source repositories `doc/build/docs/` directory to the homepage repository. Commit the change and push it to the origin repository. The rest is done automatically by GitHub.

## Build Tool Integration Plugin Update

The **BDeploy** source repository also hosts various build tool integrations as well as test projects for some features (plugins, build tools). After the release they need to be updated as well.

Since they need to have maven artifacts published earlier, you need to make sure that those are already available from maven central.

* `plugins/build-tool-gradle` - the Gradle integration.
* `plugins/gradle-plugin-test-project` - a test project for the Gradle integration.
* `plugins/bdeploy-demo-plugin` - a simple demo **BDeploy** plugin using the public API.

Last but not least, there is also `plugins/build-tool-tea` - the Eclipse TEA integration. This needs to be updated separately in an Eclipse TEA enabled workspace.

For all the others, updating is done using `gradle-upgrade-interactive`. You need to have that installed globally using npm:

```
npm install -g gradle-upgrade-interactive
```

Once this is available, `cd` into each of the directories and run `gradle-upgrade-interactive`. You will be presented a list of things to update in the given plugin. Select all of them and confirm.

!!!info Note
In `gradle-plugin-test-project` you will not see a **BDeploy** API jar update, as this project uses **BDeploy** only indirectly through the `build-tool-gradle` project - this is OK.
!!!

Now build each of the projects, and confirm that everything is OK with the new **BDeploy** release. Finally commit the changes, and you're done.

### Publish Gradle Plugin

You will need to have a gradle account and the permission to publish in the `io.bdeploy` namespace.

Make sure to setup gradle.properties in your home directory according to the instructions on the gradle manuals (i.e. set the `gradle.publish.key` and `gradle.publish.secret` properties).

Execute `./gradlew publishPlugins` in the `plugins/build-tool-gradle` folder.

Make sure to check on the plugin portal if the new version was published.
