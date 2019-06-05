using System;
using Microsoft.Win32;

namespace Bdeploy.FileAssoc
{
    /// <summary>
    /// Provides API to create or delete associations of a given executable with the .bdeploy extension.
    /// https://docs.microsoft.com/en-us/windows/desktop/shell/fa-sample-scenarios
    /// </summary>
    class FileAssociation
    {
        [System.Runtime.InteropServices.DllImport("Shell32.dll")]
        private static extern int SHChangeNotify(int eventId, int flags, IntPtr item1, IntPtr item2);

        private static readonly string PROG_ID = "BDeploy.Launcher.1";
        private static readonly string FILE_EXT = ".bdeploy";

        /// <summary>
        /// Creates or updates the programmatic identifier (ProgID) and associates the .bdeploy file extension with it.
        /// </summary>
        /// <param name="path">The absolute path to the launcher</param>
        public static void CreateAssociation(string path)
        {
            using (RegistryKey key = Registry.CurrentUser.CreateSubKey("Software\\Classes"))
            {
                DoCreateAssociation(key, path);
            }
        }

        /// <summary>
        /// Creates or updates the programmatic identifier (ProgID) and associates the .bdeploy file extension with it.
        /// </summary>
        /// <param name="path">The absolute path to the launcher</param>
        public static void CreateAssociationForAllUsers(string path)
        {
            using (RegistryKey key = Registry.ClassesRoot)
            {
                DoCreateAssociation(key, path);
            }
        }

        /// <summary>
        /// Removes the programatic programmatic identifier (ProgID).
        /// </summary>
        public static void RemoveAssociation()
        {
            using (RegistryKey key = Registry.CurrentUser.CreateSubKey("Software\\Classes"))
            {
                DoRemoveAssociation(key);
            }
        }

        /// <summary>
        /// Removes the programatic programmatic identifier (ProgID).
        /// </summary>
        public static void RemoveAssociationForAllUsers()
        {
            using (RegistryKey key = Registry.ClassesRoot)
            {
                DoRemoveAssociation(key);
            }
        }

        /// <summary>
        /// Notify Windows Explorer about the changed association.
        /// </summary>
        private static void RefreshExplorer()
        {
            SHChangeNotify(0x8000000, 0x1000, IntPtr.Zero, IntPtr.Zero);
        }

        private static void DoCreateAssociation(RegistryKey rootKey, string path)
        {
            // Create or update ProgID entry
            using (RegistryKey bDeployKey = rootKey.CreateSubKey(PROG_ID))
            {
                bDeployKey.SetValue("FriendlyTypeName", "BDeploy Application");

                using (RegistryKey commandKey = bDeployKey.CreateSubKey("Shell\\Open\\Command"))
                {
                    commandKey.SetValue("", path + " \"%1\"");
                }
                using (RegistryKey iconKey = bDeployKey.CreateSubKey("DefaultIcon"))
                {
                    iconKey.SetValue("", path);
                }
            }

            // Associate the extension with us
            using (RegistryKey bDeployKey = rootKey.CreateSubKey(FILE_EXT))
            {
                bDeployKey.SetValue("", PROG_ID);
            }

            // Refresh explorer to pickup changes
            RefreshExplorer();
        }

        private static void DoRemoveAssociation(RegistryKey rootKey)
        {
            // Remove ProgID entry
            rootKey.DeleteSubKeyTree(PROG_ID, false);

            // Remove file extension if it still points to us and there are no other entries
            using (RegistryKey bDeployKey = rootKey.CreateSubKey(FILE_EXT))
            {
                string value = (string)bDeployKey.GetValue("");
                if (value == PROG_ID && bDeployKey.SubKeyCount == 0)
                {
                    rootKey.DeleteSubKeyTree(FILE_EXT, false);
                    RefreshExplorer();
                    return;
                } else
                {
                    RefreshExplorer();
                }
            }
        }
    }


}
