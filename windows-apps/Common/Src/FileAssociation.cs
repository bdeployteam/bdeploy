using Microsoft.Win32;
using System;
using System.Globalization;

namespace Bdeploy.FileAssoc {
    /// <summary>
    /// Provides API to create or delete associations of a given executable with the .bdeploy extension.
    /// https://docs.microsoft.com/en-us/windows/desktop/shell/fa-sample-scenarios
    /// </summary>
    public class FileAssociation {
        [System.Runtime.InteropServices.DllImport("Shell32.dll")]
        private static extern int SHChangeNotify(int eventId, int flags, IntPtr item1, IntPtr item2);

        private readonly string applicationId;
        private readonly string fileExtension;

        public FileAssociation(string applicationId, string fileExtension) {
            this.applicationId = applicationId;
            this.fileExtension = fileExtension;
        }

        public void CreateBDeployLauncherAssociaton(bool forAllUsers, string pathToLauncher) {
            CreateAssociation(forAllUsers, pathToLauncher, pathToLauncher, "BDeploy Application");
        }

        /// <summary>
        /// Creates or updates the programmatic identifier and associates the file extension with it.
        /// </summary>
        /// <param name="pathToExecuteable">The absolute path to the launcher</param>
        public void CreateAssociation(bool forAllUsers, string pathToExecuteable, string pathToIcon, string friendlyName) {
            using (RegistryKey rootKey = GetRootKey(forAllUsers)) {
                // Create or update entry
                using (RegistryKey key = rootKey.CreateSubKey(applicationId)) {
                    key.SetValue("FriendlyTypeName", friendlyName);
                    key.SetValue("PreviewDetails", "prop:System.ItemNameDisplay;System.ItemTypeText;System.DateCreated;System.DateModified;System.FileOwner;System.Size;System.FileAttributes;");

                    using (RegistryKey commandKey = key.CreateSubKey("Shell\\Open\\Command")) {
                        commandKey.SetValue("", pathToExecuteable + " \"%1\" %*");
                    }
                    using (RegistryKey iconKey = key.CreateSubKey("DefaultIcon")) {
                        iconKey.SetValue("", pathToIcon);
                    }
                }

                // Associate the extension with us
                using (RegistryKey key = rootKey.CreateSubKey(fileExtension)) {
                    key.SetValue("", applicationId);
                }
            }

            // Refresh explorer to pickup changes
            RefreshExplorer();
        }

        /// <summary>
        /// Removes the programatic identifier
        /// </summary>
        public void RemoveAssociation(bool forAllUsers) {
            using (RegistryKey rootKey = GetRootKey(forAllUsers)) {
                // Remove ProgID entry
                rootKey.DeleteSubKeyTree(applicationId, false);

                // Remove file extension if it still points to us and there are no other entries
                using (RegistryKey key = rootKey.CreateSubKey(fileExtension)) {
                    string value = (string)key.GetValue("");
                    if (value == applicationId && key.SubKeyCount == 0) {
                        rootKey.DeleteSubKeyTree(fileExtension, false);
                    }
                }
            }

            // Refresh explorer to pickup changes
            RefreshExplorer();
        }

        private static RegistryKey GetRootKey(bool forAllUsers) {
            RegistryHive hive = forAllUsers ? RegistryHive.LocalMachine : RegistryHive.CurrentUser;
            using (RegistryKey root = RegistryKey.OpenBaseKey(hive, RegistryView.Registry64)) {
                return root.OpenSubKey("Software\\Classes", true);
            }
        }

        /// <summary>
        /// Notify Windows Explorer about the changed association.
        /// </summary>
        private static void RefreshExplorer() {
            SHChangeNotify(0x8000000, 0x1000, IntPtr.Zero, IntPtr.Zero);
        }
    }
}
