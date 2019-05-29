using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Security.Principal;
using System.Text;

namespace Bdeploy.Shared
{
    public class Utils
    {
        /// <summary>
        /// Returns the home directory where the applications are stored.
        /// </summary>
        /// <returns></returns>
        public static string GetBdeployHome()
        {
            // Check if BDEPLOY_HOME is set
            string home = Environment.GetEnvironmentVariable("BDEPLOY_HOME");
            if (File.Exists(home))
            {
                return home;
            }

            // Otherwise store in local app-data folder of current user
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            return Path.Combine(appData, "BDeploy");
        }

        /// <summary>
        /// Starts a new process and waits for termination
        /// </summary>
        /// <param name="fileName"></param>
        /// <param name="arguments"></param>
        /// <returns></returns>
        public static int RunProcess(string fileName, string arguments)
        {
            using (Process process = new Process())
            {
                process.StartInfo.FileName = fileName;
                process.StartInfo.Arguments = arguments;
                process.Start();
                process.WaitForExit();
                return process.ExitCode;
            }
        }

        /// <summary>
        /// Returns whether or not the current user has administrative privileges.
        /// </summary>
        /// <returns></returns>
        public static bool IsAdmin()
        {
            using (WindowsIdentity identity = WindowsIdentity.GetCurrent())
            {
                WindowsPrincipal principal = new WindowsPrincipal(identity);
                return principal.IsInRole(WindowsBuiltInRole.Administrator);
            }
        }

        /// <summary>
        /// Converts the given icon to a string using UTF-8
        /// </summary>
        public static string IconToString(Icon icon)
        {
            using (MemoryStream ms = new MemoryStream())
            {
                icon.Save(ms);
                return Encoding.Default.GetString(ms.ToArray());
            }
        }

        /// <summary>
        /// Converts the given serialized UTF-8 string into an icon 
        /// </summary>
        public static Icon StringToIcon(string serializedIcon)
        {
            byte[] bytes = Encoding.Default.GetBytes(serializedIcon);
            using (MemoryStream ms = new MemoryStream(bytes))
            {
                return new Icon(ms);
            }
        }
    }
}
