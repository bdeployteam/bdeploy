---
order: 2
icon: shield-check
---
# Security

**BDeploy** uses HTTPS everywhere along with advanced _security tokens_ which allow mutual authentication for every request. Think of it as a combination of _JWT_ and mutual certificate based authentication.

This mechanism is used for _every_ remote communication, especially for every remote communication which would cause a state change in **BDeploy**. There are some endpoints in the Web UI backend which must be unsecured by design (e.g. the one performing authentication and issuing the _security token_ for all following remote communication).

As a consequence, a _security token_ is required for all CLI commands that communicate with a remote **BDeploy** server, when registering a _node_ with a _master_ minion (as they communicate), and for all toolings which communicate otherwise with **BDeploy** (e.g. build integrations which fetch dependencies and push **Products** to **BDeploy**).

## Certificates

**BDeploy** by default generates a self-signed certificate which is used to secure both the internal communication and the Web UI (HTTPS).

It is possible to exchange this certificate with a proper one if the security warning in the Web UI is undesirable.

!!!warning Caution
Exchanging the certificate will currently invalidate all issued _security tokens_. The ones issued to authenticated users, as well as the ones used to register **BDeploy** minions with other **BDeploy** servers.
!!!

!!!info Note
Current swapping is possible only manually, proper CLI support pending.
!!!