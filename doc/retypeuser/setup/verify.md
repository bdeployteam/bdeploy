---
order: 1
---
# Verify Setup

Make sure that all **BDeploys** services are running on the according physical nodes by starting the created services or by starting them manually.
To verify that the setup is OK, run on the master:

```
bdeploy login --add=MyServer --remote=https://localhost:7701/api
bdeploy remote-master --minions
```

This will provide output like this:

```
┌────────────────────────────────────────────────────────────────────────────────────────────┐
│ Minions on https://localhost:7701/api                                                      │
├──────────────────────┬──────────┬───────────────────────────┬────────────┬─────────────────┤
│ Name                 │ Status   │ Start                     │ OS         │ Version         │
├──────────────────────┼──────────┼───────────────────────────┼────────────┼─────────────────┤
│ master               │ ONLINE   │ 19/10/21, 11:36 AM        │ WINDOWS    │ 4.0.0           │
│ node                 │ OFFLINE  │ -                         │ WINDOWS    │ 4.0.0           │
└──────────────────────┴──────────┴───────────────────────────┴────────────┴─────────────────┘
```

If any of the nodes is marked offline, the according minion on the respective node is not reachable from the master node.

!!!info Note
`bdeploy login` will prompt for user and password, create a local session, and use that session on subsequent commands. This login session will keep validity until removed using e.g. `bdeploy login --remove=MyServer`. The _active_ login can be set using `bdeploy login --use=<loginName>` (to set the default permanently) or individually/temporary per command using the `--useLogin=<loginName>` option.
!!!