using System;
using System.IO;

namespace Bdeploy.Shared {
    /// <summary>
    /// Helper class providing access to common folders.
    /// </summary>
    public class PathProvider {
        /// <summary>
        /// Root directory of BDeploy. This location might be read-only for the current user.
        /// </summary>
        /// <returns></returns>
        public static string GetBdeployHome() {
            // Check if BDEPLOY_HOME is set
            string home = Environment.GetEnvironmentVariable("BDEPLOY_HOME");
            if (home != null) {
                return home;
            }

            // Otherwise store in local app-data folder of current user
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            return Path.Combine(appData, "BDeploy");
        }

        /// <summary>
        /// Returns a directory that can be used to store files required to run the application. This location
        /// must always be writable for the current user. The BDeploy home directory is returned in case that the 
        /// location is not specified. 
        /// </summary>
        /// <returns></returns>
        public static string GetUserArea() {
            // Check if BDEPLOY_USER_AREA is set
            string home = Environment.GetEnvironmentVariable("BDEPLOY_USER_AREA");
            if (home != null) {
                return home;
            }
            return GetBdeployHome();
        }

        /// <summary>
        /// Directory where the launcher is stored. (HOME_DIR\launcher) 
        /// </summary>
        /// <returns></returns>
        public static string GetLauncherDir() {
            return Path.Combine(GetBdeployHome(), "launcher");
        }

        /// <summary>
        /// Directory where the application are stored. (HOME_DIR\apps) 
        /// </summary>
        /// <returns></returns>
        public static string GetApplicationsDir() {
            return Path.Combine(GetBdeployHome(), "apps");
        }

        /// <summary>
        /// Directory where the logs are stored. (HOME_DIR\logs or USER_AREA\logs) 
        /// </summary>
        /// <returns></returns>
        public static string GetLogsDir() {
            return Path.Combine(GetUserArea(), "logs");
        }

        /// <summary>
        /// The launcher executable. Knows how to handle .bdeploy files.
        /// </summary>
        /// <returns></returns>
        public static string GetLauncherExecutable() {
            return Path.Combine(GetLauncherDir(), "BDeploy.exe");
        }

        /// <summary>
        /// The file association executable. Knows how to associate .bdeploy files with the launcher.
        /// </summary>
        /// <returns></returns>
        public static string GetFileAssocExecutable() {
            return Path.Combine(GetLauncherDir(), "FileAssoc.exe");
        }

        /// <summary>
        /// Directory where temporary files are stored. (HOME_DIR\tmp)
        /// </summary>
        /// <returns></returns>
        public static string GetTmpDir() {
            return Path.Combine(GetBdeployHome(), "tmp");
        }
    }
}
