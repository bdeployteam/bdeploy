---
order: 12
---
# PCU (Process Control Unit)

The PCU consumes a pre-rendered (meaning all parameter value variables are resolved, all paths are absolute, etc.) configuration to control and run locally.

The PCU does not make any assumptions about locations, etc. It relies completely on external sources to prepare a `ProcessGroupConfiguration` and the according `ProcessConfiguration` per configured application.
