using System;
using System.IO;

namespace Bdeploy.Shared
{
    /// <summary>
    /// Helper class providing access to common folders.
    /// </summary>
    public class PathProvider
    {
        /// <summary>
        /// Root directory of BDeploy.
        /// </summary>
        /// <returns></returns>
        public static string GetBdeployHome()
        {
            // Check if BDEPLOY_HOME is set
            string home = Environment.GetEnvironmentVariable("BDEPLOY_HOME");
            if (home != null)
            {
                return home;
            }

            // Otherwise store in local app-data folder of current user
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            return Path.Combine(appData, "BDeploy");
        }

        /// <summary>
        /// Directory where the launcher is stored. (HOME_DIR\launcher) 
        /// </summary>
        /// <returns></returns>
        public static string GetLauncherDir()
        {
            return Path.Combine(GetBdeployHome(), "launcher");
        }

        /// <summary>
        /// Directory where the application are stored. (HOME_DIR\apps) 
        /// </summary>
        /// <returns></returns>
        public static string GetApplicationsDir()
        {
            return Path.Combine(GetBdeployHome(), "apps");
        }

        /// <summary>
        /// Directory where the logs are stored. (HOME_DIR\logs) 
        /// </summary>
        /// <returns></returns>
        public static string GetLogsDir()
        {
            return Path.Combine(GetBdeployHome(), "logs");
        }

        /// <summary>
        /// The launcher executable. Knows how to handle .bdeploy files.
        /// </summary>
        /// <returns></returns>
        public static string GetLauncherExecutable()
        {
            return Path.Combine(GetLauncherDir(), "BDeploy.exe");
        }

        /// <summary>
        /// The file association executable. Knows how to associate .bdeploy files with the launcher.
        /// </summary>
        /// <returns></returns>
        public static string GetFileAssocExecutable()
        {
            return Path.Combine(GetLauncherDir(), "FileAssoc.exe");
        }

        /// <summary>
        /// Directory where temporary files are stored. (HOME_DIR\tmp)
        /// </summary>
        /// <returns></returns>
        public static string GetTmpDir()
        {
            return Path.Combine(GetBdeployHome(), "tmp");
        }
    }
}
