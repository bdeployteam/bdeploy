using IWshRuntimeLibrary;
using System;
using System.IO;

namespace Bdeploy.Shared {
    public class Shortcut {

        private readonly string targetPath;
        private readonly string workingDir;
        private readonly string appName;
        private readonly string appIcon;

        public Shortcut(string targetPath, string workingDir, string appName, string appIcon) {
            this.targetPath = targetPath;
            this.workingDir = workingDir;
            this.appName = appName;
            this.appIcon = appIcon;
        }

        /// <summary>
        /// Creates a shortcut on the desktop using the given name.
        /// </summary>
        public string CreateDesktopLink(string linkName, bool forAllUsers) {
            Environment.SpecialFolder folder = forAllUsers ? Environment.SpecialFolder.CommonDesktopDirectory : Environment.SpecialFolder.DesktopDirectory;
            string desktop = Environment.GetFolderPath(folder);
            string linkFile = Path.Combine(desktop, linkName + ".lnk");

            WshShell shell = new WshShell();
            IWshShortcut shortcut = (IWshShortcut)shell.CreateShortcut(linkFile);
            shortcut.TargetPath = targetPath;
            shortcut.WorkingDirectory = workingDir;
            if (appIcon != null) {
                shortcut.IconLocation = appIcon;
            }
            shortcut.Description = appName;
            shortcut.Save();
            return linkFile;
        }

        /// <summary>
        /// Creates a shortcut in the start menu using the given name.
        /// </summary>
        public string CreateStartMenuLink(string linkName, string subDir, bool forAllUsers) {
            Environment.SpecialFolder folder = forAllUsers ? Environment.SpecialFolder.CommonPrograms : Environment.SpecialFolder.Programs;
            string startMenu = Environment.GetFolderPath(folder);
            string linkPath = Path.Combine(startMenu, subDir);
            string linkFile = Path.Combine(linkPath, linkName + ".lnk");

            Directory.CreateDirectory(linkPath);
            WshShell shell = new WshShell();
            IWshShortcut shortcut = (IWshShortcut)shell.CreateShortcut(linkFile);
            shortcut.TargetPath = targetPath;
            shortcut.WorkingDirectory = workingDir;
            if (appIcon != null) {
                shortcut.IconLocation = appIcon;
            }
            shortcut.Description = appName;
            shortcut.Save();
            return linkFile;
        }
    }
}
