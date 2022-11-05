---
order: 6
icon: versions
---
# Update of existing Installations

In case you already have an existing installation of **BDeploy**, you can easily update this installation. An update can be performed in two different ways: through the command line (even remotely) or through the Web UI.

For more information about the Web UI, please see [BDeploy Update](/experts/system/#bdeploy-update).

To remotely update an existing server, use a command similar to the following:

```
bdeploy login --add=MyServer --remote=https://host:port/api
bdeploy remote-master --update=<path-to-update.zip> --useLogin=MyServer
```

Updating a local server through the command line is done in the same way.

!!!info Note
Existing **nodes** don't need a separate update. They need to be online when applying the update to the **master**. The update is applied to all online **nodes** as well when applying it to the **master**.
!!!