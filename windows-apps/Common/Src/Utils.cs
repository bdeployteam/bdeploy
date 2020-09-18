using System;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Security.Principal;

namespace Bdeploy.Shared {
    public class Utils {

        public static readonly int OPERATION_CANCELED = -2;

        /// <summary>
        /// Returns whether or not the given array contains the given value
        /// </summary>
        /// <param name="args">The arguments to check</param>
        /// <param name="expected">The expected value</param>
        /// <returns>true if matching argument found, false otherwise</returns>
        public static bool HasArgument(string[] args, string expected) {
            foreach (string arg in args) {
                if (arg.Equals(expected, StringComparison.OrdinalIgnoreCase)) {
                    return true;
                }
            }
            return false;
        }

        /// <summary>
        /// Returns the directory containing the executable that has been launched.
        /// </summary>
        /// <returns></returns>
        public static string GetExecutableDir() {
            string path = Assembly.GetEntryAssembly().Location;
            return Path.GetDirectoryName(path);
        }

        /// <summary>
        /// Starts a new process and waits for termination
        /// </summary>
        /// <param name="fileName"></param>
        /// <param name="arguments"></param>
        /// <returns></returns>
        public static int RunProcessAndWait(string fileName, string arguments) {
            try {
                using (Process process = new Process()) {
                    process.StartInfo.FileName = fileName;
                    process.StartInfo.Arguments = arguments;
                    process.StartInfo.ErrorDialog = false;
                    process.Start();
                    process.WaitForExit();
                    return process.ExitCode;
                }
            } catch (Exception) {
                return -1;
            }
        }

        /// <summary>
        /// Starts a new process. Does not wait for termination
        /// </summary>
        /// <param name="fileName"></param>
        /// <param name="arguments"></param>
        /// <returns></returns>
        public static int RunProcess(string fileName, string arguments) {
            using (Process process = new Process()) {
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
        public static bool IsAdmin() {
            using (WindowsIdentity identity = WindowsIdentity.GetCurrent()) {
                WindowsPrincipal principal = new WindowsPrincipal(identity);
                return principal.IsInRole(WindowsBuiltInRole.Administrator);
            }
        }


        /// <summary>
        /// Launches the current application again with admin privileges. Waits for the termination
        /// </summary>
        /// <returns> Exit code of the process. -2 if the user cancels the operation (UAC)</returns>
        public static int RunAsAdmin(string arguments) {
            try {
                using (Process process = new Process()) {
                    process.StartInfo.FileName = Process.GetCurrentProcess().MainModule.FileName;
                    process.StartInfo.Verb = "runas";
                    process.StartInfo.Arguments = arguments;
                    process.Start();
                    process.WaitForExit();
                    return process.ExitCode;
                }
            } catch (Win32Exception) {
                // Thrown when the user cancels the UAC dialog
                return OPERATION_CANCELED;
            }
        }
    }
}
