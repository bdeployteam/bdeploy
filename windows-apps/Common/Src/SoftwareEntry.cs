using Microsoft.Win32;
using System;

namespace Bdeploy.Shared {

    /// <summary>
    /// Provides static helpers to manage registry entries related to the "Apps & Features" section of the control center. 
    /// </summary>
    public class SoftwareEntry {

        /// <summary>
        /// Path inside HKCU where all apps of the current user are stored.
        /// </summary>
        public static readonly string UNINSTALL_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\";

        /// <summary>
        /// Creates the required enttries so that the given app is listed in the control center.
        /// </summary>
        /// <param name="appUid"></param>
        /// <param name="data"></param>
        public static void Create(String appUid, SoftwareEntryData data) {
            using (RegistryKey key = Registry.CurrentUser.CreateSubKey(UNINSTALL_KEY + "\\BDeploy-App-" + appUid)) {
                SetIfNotNull(key, "DisplayName", data.DisplayName);
                SetIfNotNull(key, "DisplayIcon", data.DisplayIcon);
                SetIfNotNull(key, "DisplayVersion", data.DisplayVersion);
                SetIfNotNull(key, "Publisher", data.Publisher);
                SetIfNotNull(key, "UninstallString", data.UninstallString);
                SetIfNotNull(key, "QuietUninstallString", data.QuietUninstallString);
                SetIfNotNull(key, "InstallLocation", data.InstallLocation);
                SetIfNotNull(key, "InstallDate", data.InstallDate);
                SetIfNotNull(key, "DesktopShortcut", data.DesktopShortcut);
                SetIfNotNull(key, "StartMenuShortcut", data.StartMenuShortcut);
                if (data.noModifyAndRepair) {
                    key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                    key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
                } else {
                    key.DeleteValue("NoModify");
                    key.DeleteValue("NoRepair");
                }
            }
        }

        /// <summary>
        /// Reads the entry for the given application.
        /// </summary>
        /// <param name="appUid">Unique identifier of the application</param>
        /// <param name="data">Data stored in the registry or null</param>
        public static SoftwareEntryData Read(String appUid) {
            using (RegistryKey key = Registry.CurrentUser.CreateSubKey(UNINSTALL_KEY)) {
                using (RegistryKey appKey = key.OpenSubKey("BDeploy-App-" + appUid)) {
                    if (appKey == null) {
                        return null;
                    }
                    SoftwareEntryData entry = new SoftwareEntryData();
                    entry.DisplayName = (string)appKey.GetValue("DisplayName");
                    entry.DisplayIcon = (string)appKey.GetValue("DisplayIcon");
                    entry.DisplayVersion = (string)appKey.GetValue("DisplayVersion");
                    entry.Publisher = (string)appKey.GetValue("Publisher");
                    entry.UninstallString = (string)appKey.GetValue("UninstallString");
                    entry.QuietUninstallString = (string)appKey.GetValue("QuietUninstallString");
                    entry.InstallLocation = (string)appKey.GetValue("InstallLocation");
                    entry.InstallDate = (string)appKey.GetValue("InstallDate");
                    entry.DesktopShortcut = (string)appKey.GetValue("DesktopShortcut");
                    entry.StartMenuShortcut = (string)appKey.GetValue("StartMenuShortcut");
                    int noModify = (int)appKey.GetValue("NoModify", 0);
                    int noRepair = (int)appKey.GetValue("NoRepair", 0);
                    if (noModify == 1 && noRepair == 1) {
                        entry.noModifyAndRepair = true;
                    }
                    return entry;
                }
            }
        }

        /// <summary>
        /// Removes the entries related to the given application from the registry.
        /// </summary>
        /// <param name="appUid">Unique identifier of the application</param>
        public static void Remove(String appUid) {
            using (RegistryKey key = Registry.CurrentUser.CreateSubKey(UNINSTALL_KEY)) {
                key.DeleteSubKeyTree("BDeploy-App-" + appUid, false);
            }
        }

        /// <summary>
        /// Sets the given key to the given value. Does nothing if the value is null
        /// </summary>
        /// <param name="key">Registry key</param>
        /// <param name="name">Name of the entry</param>
        /// <param name="value">Value of the entry</param>
        private static void SetIfNotNull(RegistryKey key, string name, string value) {
            if (value != null) {
                key.SetValue(name, value);
            }
        }

    }

    /// <summary>
    /// Data record required to write the registry key.
    /// </summary>
    public class SoftwareEntryData {

        /// <summary>
        /// The text to display
        /// </summary>
        public string DisplayName;

        /// <summary>
        /// The icon to display
        /// </summary>
        public string DisplayIcon;

        /// <summary>
        /// The version of the application
        /// </summary>
        public string DisplayVersion;

        /// <summary>
        /// The name of the publisher
        /// </summary>
        public string Publisher;

        /// <summary>
        /// Path and filename of the uninstaller to remove the app.
        /// </summary>
        public string UninstallString;

        /// <summary>
        /// Path and filename of the uninstaller to remove the app without a visible window.
        /// </summary>
        public string QuietUninstallString;

        /// <summary>
        /// File system location of the app
        /// </summary>
        public string InstallLocation;

        /// <summary>
        /// Date when the app was installed.  Format: yyyyMMdd
        /// </summary>
        public string InstallDate;

        /// <summary>
        /// Desktop shortcut
        /// </summary>
        public string DesktopShortcut;

        /// <summary>
        /// Start menu shortcut
        /// </summary>
        public string StartMenuShortcut;

        /// <summary>
        /// Hint that modify and repair is not possible
        /// </summary>
        public bool noModifyAndRepair;
    }

}
