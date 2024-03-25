---
order: 4
icon: file-badge
---

# Custom Certificate

By default **BDeploy** uses a self signed certificate for both TLS (HTTPS) traffic encryption as well as token issuing. Tokens can be issued on the command line and from the User menu in the Web UI. These tokens are used to authenticate and authorize any actions performed remotely (command line, third party software, Web UI, client application launcher, etc.).

!!!warning Warning
By re-generating the certificate **BDeploy** uses, all existing tokens will lose their validity.
!!!

You can use the `certificate` tool to re-generate the internal certificate to intentionally invalidate all tokens and logins, or (which is the more common use case), you can set or update a dedicated HTTPS certificate which is then used to secure HTTPS communication only, but keep all issued tokens intact. Also tokens issued in the future will keep their validity when a custom HTTPS certificate is in use and updated later.

```
bdeploy certificate --https=<path-to-pem> --root=<path-to-root>
```

In case this is no longer wanted, you can revert to use the self-signed certificate for HTTPS as well again, by using the `--removeHttps` switch:

```
bdeploy certificate --removeHttps --root=<path-to-root>
```

The `bdeploy certificate` command also offers commands to re-generate the internal certificate (`--regenerate`), revert changes to both types of certificates (`--revert`, `--revertHttps`), export certificates (`--export`, `--exportHttps`) and re-generate master tokens in case they are lost (`--exportToken`).
