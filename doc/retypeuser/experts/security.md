---
order: 2
icon: shield-check
---
# Security

**BDeploy** uses HTTPS everywhere along with advanced _security tokens_ which allow mutual authentication for every request. Think of it as a combination of _JWT_ and mutual certificate based authentication.

This mechanism is used for _every_ remote communication, especially for every remote communication which would cause a state change in **BDeploy**. There are some endpoints in the Web UI backend which must be unsecured by design (e.g. the one performing authentication and issuing the _security token_ for all following remote communication).

As a consequence, a _security token_ is required for all CLI commands that communicate with a remote **BDeploy** server, when registering a _node_ with a _master_ minion (as they communicate), and for all toolings which communicate otherwise with **BDeploy** (e.g. build integrations which fetch dependencies and push **Products** to **BDeploy**).

## Local account security

**BDeploy** implements the [OWASP ASVS Password Security Requirements](https://github.com/OWASP/ASVS/blob/master/4.0/en/0x11-V2-Authentication.md#v21-password-security) with a single exception.

No.   | Fulfilled | Description
---   | ---        | ---
2.1.1 | Yes        | Minimum password length must be 12 characters.
2.1.2 | Yes        | Permit >64 characters, but not >128.
2.1.3 | Yes        | Password not truncated, consecutive spaces *may* be collapsed (they are not).
2.1.4 | Yes        | Allow any printable Unicode character in password (emoji, spaces, etc.).
2.1.5 | Yes        | Users can change their password.
2.1.6 | Yes        | Password change requires old and new password.
2.1.7 | **No**     | New passwords checked against set of breached passwords.
2.1.8 | Yes        | Strength meter for password stregth hint.
2.1.9 | Yes        | There should be no specific requirement for password composition.
2.1.10| Yes        | No periodic credential rotation of password history requirements.
2.1.11| Yes        | Pasting passwords from password manager should work.
2.1.12| Yes        | Hidden password can be shown temporarily while entering it.

## Certificates

**BDeploy** by default generates a self-signed certificate which is used to secure both the internal communication and the Web UI (HTTPS).

It is possible to re-generate the internal certificate in case there is a suspected token leak.

!!!warning Caution
Exchanging the certificate will currently invalidate all issued _security tokens_. The ones issued to authenticated users, as well as the ones used to register **BDeploy** minions with other **BDeploy** servers.
!!!

It is also possible to exchange *just* the HTTPS certificate. This will keep all issued tokens valid while allowing to secure HTTPS communication with a trusted, proper, official certificate.

!!!warning Caution
It **must** be assured that if this is done, there is *always* a valid certificate for HTTPS installed and updated before the current one looses validity, as the HTTPS certificate is not only used for browsers (frontend), but *also* for backend communication. Thus if the HTTPS certificate expires, BDeploy will essentially stop working.
!!!