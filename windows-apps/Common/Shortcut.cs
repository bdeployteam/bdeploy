using IWshRuntimeLibrary;
using System;
using System.IO;

namespace Bdeploy.Shared
{
    public class Shortcut
    {
        /// <summary>
        /// Creates a shortcut for the given application on the desktop using the given name and icon
        /// </summary>
        public static void CreateDesktopLink(string appName, string targetPath, string workingDir, string iconFile)
        {
            string path = Environment.GetFolderPath(Environment.SpecialFolder.Desktop);
            string linkPath = Path.Combine(path, appName + ".lnk");
            WshShell shell = new WshShell();
            IWshShortcut linkFile = (IWshShortcut)shell.CreateShortcut(linkPath);
            linkFile.TargetPath = targetPath;
            linkFile.WorkingDirectory = workingDir;
            linkFile.IconLocation = iconFile;
            linkFile.Description = appName;
            linkFile.Save();
        }

        /// <summary>
        /// Creates a shortcut for the given application in the start menu of the user.
        /// </summary>
        public static void CreateStartMenuLink(string productVendor, string appName, string targetPath, string workingDir, string iconFile)
        {
            // Use the name of the vendor for the directory
            string path = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
            if (productVendor != null)
            {
                path = Path.Combine(path, productVendor);
                Directory.CreateDirectory(path);
            }

            string linkPath = Path.Combine(path, appName + ".lnk");
            WshShell shell = new WshShell();
            IWshShortcut linkFile = (IWshShortcut)shell.CreateShortcut(linkPath);
            linkFile.TargetPath = targetPath;
            linkFile.WorkingDirectory = workingDir;
            linkFile.IconLocation = iconFile;
            linkFile.Description = appName;
            linkFile.Save();
        }
    }
}
