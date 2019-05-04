
using System.Diagnostics;
using System.ComponentModel;
using System.Security.Principal;

namespace Bdeploy
{
    /// <summary>
    /// Utility class to launch a process
    /// </summary>
    class Launcher
    {
        public static readonly int OPERATION_CANCELED = -2;

        /// <summary>
        /// Launches the current application again with admin privileges. Waits for the termination
        /// </summary>
        /// <returns> Exit code of the process. -2 if the user cancels the operation (UAC)</returns>
        public static int RunAsAdmin(string arguments)
        {
            try
            {
                using (Process process = new Process())
                {
                    process.StartInfo.FileName = Process.GetCurrentProcess().MainModule.FileName;
                    process.StartInfo.Verb = "runas";
                    process.StartInfo.UseShellExecute = true;
                    process.StartInfo.Arguments = arguments;
                    process.Start();
                    process.WaitForExit();
                    return process.ExitCode;
                }
            }
            catch (Win32Exception)
            {
                // Thrown when the user cancels the UAC dialog
                return OPERATION_CANCELED;
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
