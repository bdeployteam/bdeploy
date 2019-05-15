namespace Bdeploy
{
    partial class Launcher
    {
        /// <summary>
        /// Represents a log file stored in the log directory.
        /// </summary>
        private sealed class LogFile
        {
            // The name of the application
            public string application;

            // Index of the log file
            public int index;

            // File Extension. Either .log or .log.bak
            public string extension;
        }
    }
}
