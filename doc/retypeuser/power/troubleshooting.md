---
order: 1
icon: tools
---
# Troubleshooting

**BDeploy** provides users with several troubleshooting options to address potential problems. 

## Verify and Reinstall

If you suspect that the (server or client) application may be corrupted, you can initiate the verification process by clicking on the  [ **Verify** ] button. This action will assess the integrity of the application files, providing you with the feedback about application state. The result will indicate how many files have been modified, how many are missing, and how many remain unmodified. If the verification reveals that the application has indeed been corrupted, the recommended course of action is to reinstall the application to ensure its proper functionality. For that click on [ **Reinstall** ] button.

!!!info Note
For server applications [ **Reinstall** ] button will appear in verification result dialog in case missing or modified files are detected.  
For client applications [ **Reinstall** ] is always available from the top bar and context menu.
!!!

!!!warning Warning
Applications can be pooled, i.e. the *same* installation directory might be used for multiple **Instances** in the same **Instance Group**, depending on the applications pooling configuration. Be careful when reinstalling applications, as they might still be in use.
!!!

## Repair and Prune
Additionally, if certain **BHive** operations within **BDeploy** cease to work as expected, it may be due to issues with the associated **BHives**. To address this, users can utilize the [ **Repair and Prune Unused Objects** ] button, which initiates a filesystem consistency check (FSCK) and prune operations.  
FSCK operation identifies and repairs any corrupted files within the **BHives**, potentially resolving the operational issues you have encountered.  
Pruning any unreferenced objects from a **BHive** will clean left-overs from cancelled previous operations, which could potentially be broken and consume unnecessary disc space.
