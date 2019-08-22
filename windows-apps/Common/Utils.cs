using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Security.Principal;

namespace Bdeploy.Shared
{
    public class Utils
    {
        /// <summary>
        /// Returns whether or not the given array contains the given value
        /// </summary>
        /// <param name="args">The arguments to check</param>
        /// <param name="expected">The expected value</param>
        /// <returns>true if matching argument found, false otherwise</returns>
        public static bool HasArgument(string[] args, string expected)
        {
            foreach (string arg in args)
            {
                if (arg == expected)
                {
                    return true;
                }
            }
            return false;
        }

        /// <summary>
        /// Returns the working directory of the current process.
        /// </summary>
        /// <returns></returns>
        public static string GetWorkingDir()
        {
            FileInfo info = new FileInfo(Process.GetCurrentProcess().MainModule.FileName);
            return info.DirectoryName;
        }

        /// <summary>
        /// Starts a new process and waits for termination
        /// </summary>
        /// <param name="fileName"></param>
        /// <param name="arguments"></param>
        /// <returns></returns>
        public static int RunProcessAndWait(string fileName, string arguments)
        {
            try
            {
                using (Process process = new Process())
                {
                    process.StartInfo.FileName = fileName;
                    process.StartInfo.Arguments = arguments;
                    process.StartInfo.ErrorDialog = false;
                    process.Start();
                    process.WaitForExit();
                    return process.ExitCode;
                }
            }
            catch (Exception)
            {
                return -1;
            }
        }

        /// <summary>
        /// Starts a new process. Does not wait for termination
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
                return process.Id;
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
    }
}
