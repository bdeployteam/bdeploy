---
order: 1
icon: tools
---

# Troubleshooting

**BDeploy** provides users with several troubleshooting options to address potential problems.

## Verify Installation and Log

As a first step, you should always verify the installation and the log contents. The first part is verifying that the
data and the binary directory are separate.

This is an example of how it *should* be:

```
/opt
  - /bdeploy
    - /bdeploy
      - /bin
      - /lib
      - ...
    - /data
      - /etc
      - /hive
      - /log
      - /storage
      - ...
```

!!!warning Warning
The binaries (`bin`, `lib`, ...) are *separate* from the data directory (`hive`, `storage`). This is important as
updates to BDeploy *change* the contents of the binaries. This could potentially interfere with the data directory if it
would reside within the same directory!
!!!

The second part is to check the content of the log directory. `audit.log` will contain a brief sequence of 'modifying'
and generally failed operations. This means it is a good first place to see things that went wrong at a glance. You can
use the timestamp found in the `audit.log` to further continue looking for detailed information in `server.log`.

Generally `server.log` will contain exceptions and stack traces for issues that occured. In case you are reporting an
issue to the BDeploy Team, please make sure to attach both `audit.log` and `server.log` to facilitate troubleshooting on
our side.

## Connection to `MANAGED`

Sometimes it is hard to wrap your head around how the network between `CENTRAL` and `MANAGED` servers works. Connecting
a `MANAGED` to a `CENTRAL` server involves dropping the server information card on the `CENTRAL` server, which will
prefill all the input fields with values (URL, Port) *as known by the `MANAGED`*. This can differ vastly from how the
`CENTRAL` server *reaches* the `MANAGED`.

Thus, when connecting a `MANAGED` server make sure to **change** the prefilled values so they reflect how the `CENTRAL`
server will actually be able to reach the `MANAGED` server. This may include using IP addresses, depending on the setup
even *different* IP addresses from what the server has if there are VPNs and NATs in place.

A good indicator whether the connection will be fine is if you can ping the target server *from* the `CENTRAL`
directly (via CLI, not BDeploy).

## Corrupted Installations

If you suspect that the server or client application may be corrupted, you can initiate the verification process by clicking on the [ **Verify** ] button. This action will assess the integrity of the application files and provide you with feedback about the application state. The result will indicate how many files have been modified, how many are missing, and how many remained unmodified. If the verification reveals that the application has indeed been corrupted, the recommended course of action is to reinstall the application to ensure its proper functionality. For that click on the [ **Reinstall** ] button.

!!!info Note
For server applications the [ **Reinstall** ] button will appear in a verification result dialog in case missing or modified files are detected.  
For client applications the [ **Reinstall** ] is always available from the top bar and context menu.
!!!

!!!warning Warning
Applications can be pooled, i.e. the _same_ installation directory might be used for multiple **Instances** in the same **Instance Group**, depending on the applications pooling configuration. Be careful when reinstalling applications, as they might still be in use.
!!!

## General BHive Failures

Failing BHive (the underlying storage of BDeploy) operations will typically show the text `Operation on hive ... failed`
either in the UI or the logs.

If certain **BHive** operations within **BDeploy** cease to work as expected, it may be due to issues caused by
interrupted operations, network failures, anti-virus software keeping locks on files while checking or even removing
objects (depending on the product deployed with BDeploy, not BDeploy itself). To address this, users can utilize the [ *
*Repair and Prune Unused Objects** ] button, which
initiates a filesystem consistency check (FSCK) and prune operations.  
FSCK operation identifies and repairs any corrupted files within the **BHives**, potentially resolving the operational
issues you have encountered.
Pruning any unreferenced objects from a **BHive** will clean leftovers from cancelled and/or failed previous operations,
which could potentially be broken (and thus make subsequent operations fail as well) and consume unnecessary disc space.

## HTTPS Certificates

Certificates in general can be a tedious topic. However, there is no way around learning a bit about how they function.
The BDeploy team suggests that you at least are familiar (i.e. read up about) those topics and terms:

* PEM
* Certificate Chain
* RSA Private Key
* Key Encryption
* PKCS (#7 and #12)
* Certificate Authority

Once you know about those, make sure you convert any certificate & private key combo to a *single* PEM file containing:

```
[Main Certificate]
[Certificate Chain {1}]
[Certificate Chain {...}]
[RSA Private Key (unencrypted)]
```

This file, if prepared correctly, can be fed to BDeploy to install the certificate. All the conversions can be done e.g.
with the `openssl` command line tooling.

!!!warning Warning
The certificate **must** be signed by an authority which is **trusted**. This includes both the browser that will access
BDeploy and the **Java VM** used by BDeploy. This is typically the case for all large official CAs, however self signed
certificates issued by a private CA will cause issues and more work to get them to work.
!!!

## Connection closed/refused

Typically `Connection refused`, `Connection closed`, `Connection reset` and other connection related issues are *not*
related to BDeploy. Please troubleshoot the network connection(s) between the involved parties. 