using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.ComTypes;
using System.Text;

/// <summary>
/// Creates shortcuts without a dependency against IWshRuntimeLibrary 
/// https://stackoverflow.com/questions/4897655/create-a-shortcut-on-desktop/14632782#14632782
/// </summary>
namespace Bdeploy.Shared
{
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
            string desktop = Environment.GetFolderPath(folder, Environment.SpecialFolderOption.Create);
            string linkFile = Path.Combine(desktop, linkName + ".lnk");

            CreateAndSaveShortcut(linkFile);
            return linkFile;
        }

        /// <summary>
        /// Creates a shortcut in the start menu using the given name.
        /// </summary>
        public string CreateStartMenuLink(string linkName, string subDir, bool forAllUsers) {
            Environment.SpecialFolder folder = forAllUsers ? Environment.SpecialFolder.CommonPrograms : Environment.SpecialFolder.Programs;
            string startMenu = Environment.GetFolderPath(folder, Environment.SpecialFolderOption.Create);
            string linkPath = Path.Combine(startMenu, subDir);
            string linkFile = Path.Combine(linkPath, linkName + ".lnk");
            Directory.CreateDirectory(linkPath);

            CreateAndSaveShortcut(linkFile);
            return linkFile;
        }

        private void CreateAndSaveShortcut(string linkFile) {
            // setup shortcut information
            IShellLink link = (IShellLink)new ShellLink();
            link.SetDescription(appName);
            link.SetPath(targetPath);
            link.SetWorkingDirectory(workingDir);
            if (appIcon != null) {
                link.SetIconLocation(appIcon, 0);
            }

            // save it
            IPersistFile file = (IPersistFile)link;
            file.Save(linkFile, false);
        }
    }

    [ComImport]
    [Guid("00021401-0000-0000-C000-000000000046")]
    internal class ShellLink {
    }

    [ComImport]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    [Guid("000214F9-0000-0000-C000-000000000046")]
    internal interface IShellLink {
        void GetPath([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszFile, int cchMaxPath, out IntPtr pfd, int fFlags);
        void GetIDList(out IntPtr ppidl);
        void SetIDList(IntPtr pidl);
        void GetDescription([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszName, int cchMaxName);
        void SetDescription([MarshalAs(UnmanagedType.LPWStr)] string pszName);
        void GetWorkingDirectory([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszDir, int cchMaxPath);
        void SetWorkingDirectory([MarshalAs(UnmanagedType.LPWStr)] string pszDir);
        void GetArguments([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszArgs, int cchMaxPath);
        void SetArguments([MarshalAs(UnmanagedType.LPWStr)] string pszArgs);
        void GetHotkey(out short pwHotkey);
        void SetHotkey(short wHotkey);
        void GetShowCmd(out int piShowCmd);
        void SetShowCmd(int iShowCmd);
        void GetIconLocation([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszIconPath, int cchIconPath, out int piIcon);
        void SetIconLocation([MarshalAs(UnmanagedType.LPWStr)] string pszIconPath, int iIcon);
        void SetRelativePath([MarshalAs(UnmanagedType.LPWStr)] string pszPathRel, int dwReserved);
        void Resolve(IntPtr hwnd, int fFlags);
        void SetPath([MarshalAs(UnmanagedType.LPWStr)] string pszFile);
    }
}
