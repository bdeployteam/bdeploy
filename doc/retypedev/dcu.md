---
order: 11
---
# DCU (Deployment Control Unit)

The DCU's `InstanceNodeController` is responsible for consuming an `InstanceNodeManifest`. An `InstanceNodeManifest` is an artificial BHive manifest created by a configuration application, typically the web UI.

The DCU assumes that any referenced BHive `MANIFEST` is locally available, i.e. has been pushed to the BHive associated with the DCU before passing a reference to it to the DCU.

Note that the DCU does not know about multiple nodes in a system. It assumes that everything is local, and so do all data structures in the DCU. The minion controlling the local DCU may have additional higher level structures to allow multiple DCUs distributed among nodes deploying for a single `InstanceManifest`.

## Backends

Right now, there is only a single DCU, which has the nodes physical disc as manifestation target.

The architecture is designed in a way to be able to later on have plug-able DCUs, which assume responsibility for a certain node type. This allows to implement specific DCUs later and mix and match them on a node level. For instance a special "Kubernetes" node could allow to drop applications into a cluster, having the cluster side-by-side with classic local application installation (but sharing configuration data, ...).