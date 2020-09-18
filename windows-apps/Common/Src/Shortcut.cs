using IWshRuntimeLibrary;
using System;
using System.IO;

namespace Bdeploy.Shared {
    public class Shortcut {

        private readonly string group;
        private readonly string instance;
        private readonly string appName;
        private readonly string productVendor;
        private readonly string targetPath;
        private readonly string workingDir;
        private readonly string iconFile;

        public Shortcut(string group, string instance, string appName, string productVendor, string targetPath, string workingDir, string iconFile) {
            this.group = FileHelper.GetSafeFilename(group);
            this.instance = FileHelper.GetSafeFilename(instance);
            this.appName = FileHelper.GetSafeFilename(appName);
            this.productVendor = productVendor;
            this.targetPath = targetPath;
            this.workingDir = workingDir;
            this.iconFile = iconFile;
        }

        /// <summary>
        /// Creates a shortcut for the given application on the desktop.
        /// </summary>
        public string CreateDesktopLink(bool forAllUsers) {
            Environment.SpecialFolder folder = forAllUsers ? Environment.SpecialFolder.CommonDesktopDirectory : Environment.SpecialFolder.DesktopDirectory;
            string desktop = Environment.GetFolderPath(folder);
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
        /// Creates a shortcut for the given application in the start menu.
        /// </summary>
        public string CreateStartMenuLink(bool forAllUsers) {
            Environment.SpecialFolder folder = forAllUsers ? Environment.SpecialFolder.CommonPrograms : Environment.SpecialFolder.Programs;
            string startMenu = Environment.GetFolderPath(folder);
            string linkDir = Path.Combine(startMenu, productVendor, group, instance);
            string linkName = appName + " (" + group + " - " + instance + ").lnk";
            string linkPath = Path.Combine(linkDir, linkName);

            Directory.CreateDirectory(linkDir);
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
