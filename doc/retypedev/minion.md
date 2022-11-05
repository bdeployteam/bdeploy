---
order: 9
icon: server
---
# Minion (master/node) Server

The minion is the main BDeploy deliverable. It contains all the command tools as well as the server for remote services and the configuration web UI.

Use `bdeploy init ...` to initalize a minion root, then use `bdeploy start ...` to launch a master (including web UI) or a headless node (controlled by a master) from that root, depending on the mode given during `init`.
 