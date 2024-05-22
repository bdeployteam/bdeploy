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
        /// Root directory of BDeploy. This location might be read-only for the current user.
        /// </summary>
        public static string GetBdeployHome(bool forAllUsers)
        {
            // Check if BDEPLOY_HOME is set
            string home = Environment.GetEnvironmentVariable("BDEPLOY_HOME");
            if (home != null)
            {
                return home;
            }

            // Store in ProgramFiles or in LocalApplicationData depending on the parameter
            Environment.SpecialFolder folder = forAllUsers ? Environment.SpecialFolder.ProgramFiles : Environment.SpecialFolder.LocalApplicationData;
            return Path.Combine(Environment.GetFolderPath(folder), "BDeploy");
        }

        /// <summary>
        /// Directory where the launcher is stored. (HOME_DIR\launcher) 
        /// </summary>
        /// <returns></returns>
        public static string GetLauncherDir(string home)
        {
            return Path.Combine(home, "launcher");
        }

        /// <summary>
        /// Directory where the application are stored. (HOME_DIR\apps) 
        /// </summary>
        /// <returns></returns>
        public static string GetApplicationsDir(string home)
        {
            return Path.Combine(home, "apps");
        }

        /// <summary>
        /// Directory where temporary files are stored. (HOME_DIR\tmp)
        /// </summary>
        /// <returns></returns>
        public static string GetTmpDir(string home)
        {
            return Path.Combine(home, "tmp");
        }
    }
}
