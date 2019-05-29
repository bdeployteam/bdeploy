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
        public static void CreateLink(string appName, string targetPath, string workingDir,string iconFile)
        {
            var desktop = Environment.GetFolderPath(Environment.SpecialFolder.Desktop);
            var linkPath = Path.Combine(desktop, appName + ".lnk");
            var shell = new WshShell();
            var linkFile = (IWshShortcut)shell.CreateShortcut(linkPath);
            linkFile.TargetPath = targetPath;
            linkFile.WorkingDirectory = workingDir;
            linkFile.IconLocation = iconFile;
            linkFile.Save();
        }

    }
}
