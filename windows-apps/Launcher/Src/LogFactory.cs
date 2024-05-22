using Bdeploy.Shared;
using Serilog;
using System;
using System.IO;

namespace Bdeploy.Launcher
{
    /// <summary>
    /// Responsible for creating and initializing loggers
    /// </summary>
    class LogFactory
    {
        private static readonly string TEMPLATE = "{Timestamp:yyyy-MM-dd HH:mm:ss} | PID:{ProcessId} | User:{EnvironmentUserName} | {Level:u3} | {Message:l}{NewLine}{Exception}";

        /// <summary>
        /// Directory where the logs are stored. (LOCALAPPDATA\logs or USER_AREA\logs) 
        /// </summary>
        /// <returns></returns>
        public static string GetLogsDir()
        {
            // Store logs in the provided user area
            string userArea = Environment.GetEnvironmentVariable("BDEPLOY_USER_AREA");
            if (userArea != null)
            {
                return Path.Combine(userArea, "logs");
            }

            // Calculate the home directory based on the executable
            string launcherDir = Utils.GetExecutableDir();
            string homeDir = Directory.GetParent(launcherDir).FullName;
            if (!FileHelper.IsReadOnly(homeDir))
            {
                return Path.Combine(homeDir, "logs");
            }

            // Use the application data folder as fallback
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            return Path.Combine(appData, "BDeploy", "logs");
        }

        /// <summary>
        /// Creates the default logger used by the application.
        /// </summary>
        public static ILogger CreateGlobalLogger(string path)
        {
            return new LoggerConfiguration()
                .MinimumLevel.Debug()
                .Enrich.WithProcessId()
                .Enrich.WithEnvironmentUserName()

                // Write to console for debugging purpose
                .WriteTo.Console(outputTemplate: TEMPLATE)

                // Write to a shared rolling log file
                .WriteTo.File(path,
                    outputTemplate: TEMPLATE,
                    shared: true,
                    retainedFileCountLimit: 5,
                    rollOnFileSizeLimit: true,
                    rollingInterval: RollingInterval.Day,
                    fileSizeLimitBytes: 2048000) // 2 MB
                .CreateLogger();
        }

        /// <summary>
        /// Returns a logger that is used to log the output of the standard out of the launched process.
        /// </summary>
        /// <returns></returns>
        public static ILogger GetAppLogger(string path)
        {
            return new LoggerConfiguration()
               .WriteTo.File(path,
                   outputTemplate: "{Message:l}{NewLine}",
                   shared: true,
                   retainedFileCountLimit: 5,
                   rollOnFileSizeLimit: true,
                   rollingInterval: RollingInterval.Day,
                   fileSizeLimitBytes: 2048000) // 2 MB
               .CreateLogger();
        }

    }
}
