using IWshRuntimeLibrary;
using System;
using System.IO;

namespace Bdeploy.Shared
{
    public class Shortcut
    {
        /// <summary>
        /// Creates a shortcut for the given application on the desktop using the given name
        /// </summary>
        public static void Create(string appName, string targetPath, string workingDir)
        {
            var desktop = Environment.GetFolderPath(Environment.SpecialFolder.Desktop);
            var shell = new WshShell();
            var shortCutLinkFilePath = Path.Combine(desktop, appName + ".lnk");
            var windowsApplicationShortcut = (IWshShortcut)shell.CreateShortcut(shortCutLinkFilePath);
            windowsApplicationShortcut.TargetPath = targetPath;
            windowsApplicationShortcut.WorkingDirectory = workingDir;
            windowsApplicationShortcut.Save();
        }

    }
}
