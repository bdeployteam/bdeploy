---
order: 6
icon: mail
---
# E-Mail Sending and Receiving

**BDeploy** can use an alternative way of transferring configuration (and changes to them) from a `MANAGED` to a `CENTRAL` server. There is currently only one implmenetation to that, and that is sending an E-Mail with the configuration changes securely signed and attached to that E-Mail.

## E-Mail Sending

E-Mail sending must be configured on the `MANAGED` server. The server will send out an E-Mail whenever pressing the *Save* button on an **Instance Configuration**. The **Mail Sending Configuration** includes mostly the commonly known suspects as server URL, username and password, but also requires two additional configurations to be able to transfer configurations specifically: The target receipient of the E-Mail and the name of *this* `MANAGED` server as configured on the `CENTRAL` server. This is so that the `CENTRAL` - when receiving an E-Mail - can find out which `MANAGED` server sent this E-Mail. This input field needs to correspond to the *Name* field of the server configuration on the `CENTRAL`.

:::{align=center}
![Mail Sending Configuration](/images/Doc_Admin_Mail_Sending.png){width=480}
:::

!!!info Note
The receipient should be the mailbox which is configured on the `CENTRAL` server in the **Mail Receiving Configuration**
!!!

## E-Mail Receiving

The `CENTRAL` server can connect to a configured mailbox via **IMAP** and check for new incoming E-Mails. Those are processed automatically, and enclosed configuration changes are applied to the stored configuration on the `CENTRAL`.

:::{align=center}
![Mail Receiving Configuration](/images/Doc_Admin_Mail_Receiving.png){width=480}
:::
