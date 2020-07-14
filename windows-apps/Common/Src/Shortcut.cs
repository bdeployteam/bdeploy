using IWshRuntimeLibrary;
using System;
using System.IO;

namespace Bdeploy.Shared {
    public class Shortcut {
        /// <summary>
        /// Creates a shortcut for the given application on the desktop using the given name and icon
        /// </summary>
        public static string CreateDesktopLink(string group, string instance, string appName, string targetPath, string workingDir, string iconFile) {
            group = FileHelper.GetSafeFilename(group);
            instance = FileHelper.GetSafeFilename(instance);
            appName = FileHelper.GetSafeFilename(appName);

            string desktop = Environment.GetFolderPath(Environment.SpecialFolder.Desktop);
            string linkName = appName + " (" + group + " - " + instance + ").lnk";
            string linkPath = Path.Combine(desktop, linkName);

            WshShell shell = new WshShell();
            IWshShortcut linkFile = (IWshShortcut)shell.CreateShortcut(linkPath);
            linkFile.TargetPath = targetPath;
            linkFile.WorkingDirectory = workingDir;
            linkFile.IconLocation = iconFile;
            linkFile.Description = appName;
            linkFile.Save();
            return linkPath;
        }

        /// <summary>
        /// Creates a shortcut for the given application in the start menu of the user.
        /// </summary>
        public static string CreateStartMenuLink(string group, string instance, string appName, string productVendor, string targetPath, string workingDir, string iconFile) {
            group = FileHelper.GetSafeFilename(group);
            instance = FileHelper.GetSafeFilename(instance);
            appName = FileHelper.GetSafeFilename(appName);

            string startMenu = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
            string linkDir = Path.Combine(startMenu, productVendor, group, instance);
            Directory.CreateDirectory(linkDir);

            string linkName = appName + " (" + group + " - " + instance + ").lnk";
            string linkPath = Path.Combine(linkDir, linkName);

            WshShell shell = new WshShell();
            IWshShortcut linkFile = (IWshShortcut)shell.CreateShortcut(linkPath);
            linkFile.TargetPath = targetPath;
            linkFile.WorkingDirectory = workingDir;
            linkFile.IconLocation = iconFile;
            linkFile.Description = appName;
            linkFile.Save();
            return linkPath;
        }
    }
}
