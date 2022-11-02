---
order: 4
---
# Custom Certificate

By default **BDeploy** uses a self signed certificate for both TLS (HTTPS) traffic encryption as well as token issuing. Tokens can be issued on the command line and from the User menu in the Web UI. These tokens are used to authenticate and authorize any actions performed remotely (command line, third party software, Web UI, etc.).

!!!warning Warning
By changing the certificate **BDeploy** uses, all existing tokens will lose their validity.
!!!

You can use the `certificate` tool to switch to another ceritificate:

```
bdeploy certificate --update=<path-to-pem> --root=<path-to-root>
```