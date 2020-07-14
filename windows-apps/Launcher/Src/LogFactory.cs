using Serilog;

namespace Bdeploy.Launcher {
    /// <summary>
    /// Responsible for creating and initializing loggers
    /// </summary>
    class LogFactory {
        private static readonly string TEMPLATE = "{Timestamp:yyyy-MM-dd HH:mm:ss} | PID:{ProcessId} | User:{EnvironmentUserName} | {Level:u3} | {Message:l}{NewLine}{Exception}";

        /// <summary>
        /// Creates the default logger used by the application.
        /// </summary>
        public static ILogger CreateGlobalLogger(string path) {
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
        public static ILogger GetAppLogger(string path) {
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
