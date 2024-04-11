---
order: 6
icon: mail
---

# E-Mail Sending and Receiving

**BDeploy** can use an alternative way of transferring configurations (and changes to them) from a `MANAGED` to a `CENTRAL` server. There is currently only one implementation to that, and that is sending an E-Mail with the configuration changes securely signed and attached to that E-Mail.

## Sending

E-Mail sending must be configured on the `MANAGED` server. The server will send out an E-Mail whenever the _Save_ button of an **Instance Configuration** is pressed. The **Mail Sending Configuration** includes the commonly known suspects such as server URL, username and password, but it also requires two additional configurations to be able to transfer configurations specifically: The target recipient of the E-Mail and the name of _this_ `MANAGED` server as configured on the `CENTRAL` server. This is so that the `CENTRAL` - when receiving an E-Mail - can determine which `MANAGED` server sent the E-Mail. This input field needs to correspond to the _Name_ field of the server configuration on the `CENTRAL`.

:::{align=center}
![Mail Sending Configuration](/images/Doc_Admin_Mail_Sending.png){width=480}
:::

!!!info Note
The recipient must be the mailbox which is configured on the `CENTRAL` server in the **Mail Receiving Configuration**.
!!!

## Receiving

The `CENTRAL` server can connect to a configured mailbox via **IMAP** and check for new incoming E-Mails. Those are processed automatically, and enclosed configuration changes are applied to the stored configuration on the `CENTRAL`.

:::{align=center}
![Mail Receiving Configuration](/images/Doc_Admin_Mail_Receiving.png){width=480}
:::
