
using System.Diagnostics;
using System.ComponentModel;
using System.Security.Principal;
using System.IO;
using System.Threading.Tasks;
using System;
using System.Text;
using System.Text.RegularExpressions;
using System.Linq;
using System.Collections.Generic;

namespace Bdeploy
{
    /// <summary>
    /// Launches the companion script and passes the desired application to start.
    /// </summary>
    partial class Launcher
    {
        // The name of the command line script to launch
        public static readonly string COMPANION = "launcher.bat";

        // The full path of the bdeploy file to launch
        private readonly string application;

        // working directory of the application
        private readonly string launcherWorkingDir;


        /// <summary>
        /// Creates a new instance of the launcher.
        /// </summary>
        /// <param name="application">The bdeploy file to pass to the compantion script</param>
        public Launcher(string application)
        {
            FileInfo info = new FileInfo(Process.GetCurrentProcess().MainModule.FileName);
            this.launcherWorkingDir = Path.Combine(info.DirectoryName, "bin");
            this.application = application;
        }

        /// <summary>
        /// Returns the full path to the companion script
        /// </summary>
        /// <returns></returns>
        public string GetCompanion()
        {
            return Path.Combine(launcherWorkingDir, COMPANION);
        }

        /// <summary>
        /// Returns whether or not the companion script is at the desired location
        /// </summary>
        /// <returns></returns>
        public bool HasCompanion()
        {
            return File.Exists(GetCompanion());
        }

        /// <summary>
        /// Launches companion script and waits for termination.
        /// </summary>
        /// <returns> Exit code of the process.</returns>
        public int Start()
        {
            using (StreamWriter writer = InitLogging())
            using (Process process = new Process())
            {
                string launcher = Path.Combine(launcherWorkingDir, COMPANION);
                string arguments = String.Format("\"{0}\"", application);
                Log(writer, String.Format("Launcher: {0}", launcher));
                Log(writer, String.Format("Starting launcher with arguments: {0}", arguments));

                // Launch process
                process.StartInfo.FileName = launcher;
                process.StartInfo.CreateNoWindow = true;
                process.StartInfo.UseShellExecute = false;
                process.StartInfo.Arguments = arguments;
                process.StartInfo.WorkingDirectory = launcherWorkingDir;
                process.StartInfo.ErrorDialog = false;
                process.StartInfo.RedirectStandardOutput = true;
                process.StartInfo.RedirectStandardError = true;
                process.Start();

                // Write log file about the startup
                Log(writer, String.Format("Launcher started. PID = {0}", process.Id));
                writer.WriteLine("");

                // Capture output and write to logfile
                StreamReader reader = process.StandardOutput;
                Task logTask = Task.Run(() => CopyTo(reader, writer));

                // Wait until the process terminates
                process.WaitForExit();

                writer.WriteLine("");
                Log(writer, String.Format("Launcher terminated. ExitCode = {0}", process.ExitCode));
                return process.ExitCode;
            }
        }

        /// <summary>
        /// Creates a new file where to store the log files of the current run. 
        /// Ensures that old log files are cleaned so that they do not stay forever.
        /// </summary>
        /// <returns></returns>
        private StreamWriter InitLogging()
        {
            string name = Path.GetFileNameWithoutExtension(application);

            // Determine where to store logs
            string logPath = Path.Combine(GetBdeployHome(), "log");
            Directory.CreateDirectory(logPath);
            DirectoryInfo dir = new DirectoryInfo(logPath);

            // Cleanup old files of this application
            CleanupOldLogs(dir, name);

            // Determine next free index for log file name
            int logIndex = GetLogFileIdx(dir, name);
            string logName = String.Format("{0}.{1}.log", name, logIndex);

            // Create a new writer that flushes on each output
            string logFile = Path.Combine(logPath, logName);
            StreamWriter writer = new StreamWriter(logFile, false)
            {
                AutoFlush = true
            };
            return writer;
        }

        /// <summary>
        /// Determines the next available index for the log file name.
        /// </summary>
        /// <returns></returns>
        private int GetLogFileIdx(DirectoryInfo dir, string application)
        {
            int maxIndex = -1;
            foreach (FileInfo file in dir.GetFiles("*.log"))
            {
                LogFile logFile = MatchLogFile(file);
                if (logFile == null || logFile.application != application)
                {
                    continue;
                }
                maxIndex = Math.Max(maxIndex, logFile.index);
            }
            if (maxIndex == -1)
            {
                return 0;
            }
            return maxIndex + 1;
        }

        /// <summary>
        /// Retains the last log file of each application if it is not older than one month.
        /// </summary>
        private static void CleanupOldLogs(DirectoryInfo dir, string application)
        {
            // Create backup of all log files.
            foreach (FileInfo file in dir.GetFiles("*.log").ToArray())
            {
                TryBackup(file);
            }

            // Retain only the last backup file per application and delete expired backups
            HashSet<String> backups = new HashSet<String>();
            foreach (FileInfo file in dir.GetFiles("*.bak").OrderByDescending(p => p.LastWriteTimeUtc).ToArray())
            {
                LogFile logFile = MatchLogFile(file);
                if (logFile == null)
                {
                    continue;
                }
                if (IsExpired(file) || backups.Contains(logFile.application))
                {
                    TryDelete(file);
                    continue;
                }
                backups.Add(logFile.application);
            }
        }

        /// <summary>
        /// Matches the log name pattern against the given file and returns the match.
        /// Logfile pattern is: <application>.<index>.<log>
        /// </summary>
        private static LogFile MatchLogFile(FileInfo file)
        {
            Regex rx = new Regex(@"(.*)\.(\d+)\.(.*)", RegexOptions.IgnoreCase);
            Match match = rx.Match(file.Name);
            if (!match.Success)
            {
                return null;
            }
            LogFile logFile = new LogFile();
            logFile.application = match.Groups[1].Value;
            Int32.TryParse(match.Groups[2].Value, out logFile.index);
            logFile.extension = match.Groups[3].Value;
            return logFile;
        }

        /// <summary>
        /// Reads data from the given stream and writes it to the given stream.
        /// </summary>
        private static void CopyTo(StreamReader reader, StreamWriter writer)
        {
            string line;
            while ((line = reader.ReadLine()) != null)
            {
                writer.WriteLine(line);
            }
        }

        /// <summary>
        /// Tries to delete the given file. Fails silently if not posible
        /// </summary>
        private static void TryDelete(FileInfo file)
        {
            try
            {
                file.Delete();
            }
            catch (Exception)
            {
            }
        }

        /// <summary>
        /// Create a backup by renaming it to .bak. Existing files are overwritten
        /// </summary>
        private static void TryBackup(FileInfo file)
        {
            try
            {
                // Do not try to move a file that is in use
                if(IsFileLocked(file))
                {
                    return;
                }

                // Replace with the given file
                FileInfo backup = new FileInfo(file.FullName + ".bak");
                TryDelete(backup);
                file.MoveTo(backup.FullName);
            }
            catch (Exception)
            {
            }
        }

        /// <summary>
        /// Returns whether or not the file is older than 30 days
        /// </summary>
        private static bool IsExpired(FileInfo file)
        {
            return (DateTime.UtcNow - file.LastWriteTimeUtc).TotalDays > 30;
        }

        /// <summary>
        /// Writes a log statement including the current date and time.
        /// </summary>
        private static void Log(StreamWriter writer, String message)
        {
            string date = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss.fff");
            writer.WriteLine("{0} | {1}", date, message);
        }

        /// <summary>
        /// Returns the home directory where the applications are stored.
        /// </summary>
        /// <returns></returns>
        private static string GetBdeployHome()
        {
            // Check if BDEPLOY_HOME is set
            string home = Environment.GetEnvironmentVariable("BDEPLOY_HOME");
            if (File.Exists(home))
            {
                return home;
            }

            // Otherwise store in local app-data folder of current user
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            return Path.Combine(appData, "BDeploy");
        }

        private static bool IsFileLocked(FileInfo file)
        {
            try
            {
                using (FileStream stream = file.Open(FileMode.Open, FileAccess.Read, FileShare.None))
                    return false;
            }
            catch (IOException)
            {
                return true;
            }

        }
    }
}
