using System;
using System.Collections;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Security.Principal;
using System.Text;

namespace Bdeploy.Shared {
    public class Utils {

        public static readonly int OPERATION_CANCELED = -2;

        /// <summary>
        /// Enriches the given error message with additional details about the system environment
        /// </summary>
        /// <param name="message"></param>
        /// <returns></returns>
        public static string GetDetailedErrorMessage(string message) {

            StringBuilder builder = new StringBuilder();
            builder.AppendFormat("*** Date: {0}", DateTime.Now.ToString("dd.MM.yyyy hh:mm:ss"));
            builder.AppendLine().AppendLine();

            builder.AppendFormat("*** Error:").AppendLine();
            builder.Append(message);
            builder.AppendLine().AppendLine();

            builder.AppendFormat("*** Application:").AppendLine();
            builder.Append(Environment.CommandLine);
            builder.AppendLine().AppendLine();

            builder.Append("*** System environment variables: ").AppendLine();
            foreach (DictionaryEntry entry in Environment.GetEnvironmentVariables()) {
                builder.AppendFormat("{0}={1}", entry.Key, entry.Value).AppendLine();
            }
            builder.AppendLine();

            builder.Append("*** Operating system: ").AppendLine();
            builder.Append(ReadValueName("ProductName")).Append(Environment.NewLine);
            builder.AppendFormat("Version {0} (OS Build {1}.{2})", ReadValueName("ReleaseId"), ReadValueName("CurrentBuildNumber"), ReadValueName("UBR"));
            builder.AppendLine();

            return builder.ToString();
        }

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

        private static string ReadValueName(string valueName) {
            return Microsoft.Win32.Registry.GetValue(@"HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion", valueName, "").ToString();
        }
    }
}
