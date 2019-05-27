using Bdeploy.Common;
using Bdeploy.Models;
using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Threading.Tasks;

namespace Bdeploy
{
    /// <summary>
    /// Downloads and unpacks the launcher.
    /// </summary>
    public class Installer
    {
        /// <summary>
        /// Directory where BDeploy stores all files 
        /// </summary>
        private readonly string bdeployHome;

        /// <summary>
        /// Directory where the launcher stores all files. (HOME_DIR\launcher) 
        /// </summary>
        private readonly string launcherHome;

        /// <summary>
        /// Flag indicating whether or not to cancel the operattion
        /// </summary>
        public bool Canceled { get; internal set; }

        /// <summary>
        /// Event that is raised when some work has been done
        /// </summary>
        public event EventHandler<WorkedEventArgs> Worked;

        /// <summary>
        /// Event that is raised when a new task has been started
        /// </summary>
        public event EventHandler<SubTaskEventArgs> NewSubtask;

        /// <summary>
        /// Event that is raised when an error occured
        /// </summary>
        public event EventHandler<MessageEventArgs> Error;

        /// <summary>
        /// Creates a new installer instance.
        /// </summary>
        public Installer()
        {
            bdeployHome = Utils.GetBdeployHome();
            launcherHome = Path.Combine(bdeployHome, "launcher");
        }

        /// <summary>
        /// Checks if the desired application is already installed.
        /// </summary>
        public bool IsApplicationAlreadyInstalled()
        {
            string appDir = Path.Combine(bdeployHome, "applications");
            string appUid = Properties.Resources.ApplicationUid;
            string appShortcut = Path.Combine(appDir, appUid + ".bdeploy");
            string appExe = Path.Combine(appDir, appUid + ".exe");

            // Shortcut, Executable and launcher must be installed
            return File.Exists(appShortcut) && File.Exists(appExe) && IsLauncherInstalled();
        }

        /// <summary>
        /// Launches the application and waits for it to terminate
        /// </summary>
        /// <returns></returns>
        public int Launch()
        {
            string appDir = Path.Combine(bdeployHome, "applications");
            string appUid = Properties.Resources.ApplicationUid;
            string appShortcut = Path.Combine(appDir, appUid + ".bdeploy");
            string launcher = Path.Combine(launcherHome, "BDeploy.exe");
            return Utils.RunProcess(launcher, appShortcut);
        }

        /// <summary>
        /// Executes the installer and performs all tasks.
        /// </summary>
        public async Task<int> Setup()
        {
            try
            {
                OnNewSubtask("Preparing...", -1);
                Directory.CreateDirectory(bdeployHome);

                // Download and extract if not available
                if (!IsLauncherInstalled())
                {
                    await DownloadAndExtractLauncher();
                }

                // Extract application to launch from our executable
                string appFile = ExtractApplication();

                // Associate bdeploy files with the launcher
                CreateFileAssociation();

                // Launch application
                CreateShortcut();
                return 0;
            }
            catch (Exception ex)
            {
                OnError(string.Format("Error Message. {0}", ex.Message));
                return -1;
            }
        }

        /// <summary>
        /// Copies the current executable and create desktop shortcut
        /// </summary>
        private void CreateShortcut()
        {
            string appDir = Path.Combine(bdeployHome, "applications");
            string appUid = Properties.Resources.ApplicationUid;
            string appName = Properties.Resources.ApplicationName;

            // Copy executable to the applications folder
            string installer = Process.GetCurrentProcess().MainModule.FileName;
            string persisted = Path.Combine(appDir, appUid + ".exe");
            bool createShortcut = !File.Exists(persisted);
            File.Copy(installer, persisted, true);

            // Only create shortcut if the files does not exit
            if (createShortcut)
            {
                Shortcut.Create(appName, persisted, launcherHome);
            }
        }

        /// <summary>
        /// Associates .bdeploy files with the launcher
        /// </summary>
        private void CreateFileAssociation()
        {
            string launcher = Path.Combine(launcherHome, "FileAssoc.exe");
            string fileAssoc = Path.Combine(launcherHome, "FileAssoc.exe");
            string arguments = string.Format("{0} \"{1}\"", "/CreateForCurrentUser", launcher);
            Utils.RunProcess(fileAssoc, arguments);
        }

        /// <summary>
        /// Extracts the embedded application and writes a new file
        /// </summary>
        private string ExtractApplication()
        {
            OnNewSubtask("Installing...", -1);

            // All applications are stored in a separate directory
            string appDir = Path.Combine(bdeployHome, "applications");
            Directory.CreateDirectory(appDir);

            // Write entire content into the file
            string appUid = Properties.Resources.ApplicationUid;
            string jsonContent = Properties.Resources.ApplicationJson;
            string fileName = Path.Combine(appDir, appUid + ".bdeploy");
            File.WriteAllText(fileName, jsonContent);
            return fileName;
        }

        /// <summary>
        /// Downloads and extracts the launcher.
        /// </summary>
        private async Task DownloadAndExtractLauncher()
        {
            // Launcher directory must not exist. 
            // Otherwise ZIP extraction fails
            DeleteDir(launcherHome);

            // Prepare tmp directory
            string tmpDir = Path.Combine(bdeployHome, "tmp");
            Directory.CreateDirectory(tmpDir);

            // Download and extract
            string launcherZip = await DownloadLauncher(tmpDir);
            ExtractLauncher(launcherZip, launcherHome);

            // Cleanup. Download not required any more
            DeleteDir(tmpDir);
        }

        /// <summary>
        /// Downloads the launcher and stores it in the given directory
        /// </summary>
        private async Task<string> DownloadLauncher(string tmpDir)
        {
            // Send request and download launcher to a local file
            var tmpFileName = Path.Combine(tmpDir, Guid.NewGuid() + ".download");
            using (HttpClient client = new HttpClient())
            {
                var requestUrl = new Uri(Bdeploy.Properties.Resources.LauncherUrl);
                var request = new HttpRequestMessage(HttpMethod.Get, requestUrl);
                var response = await client.GetAsync(requestUrl, HttpCompletionOption.ResponseHeadersRead);

                long? contentLength = response.Content.Headers.ContentLength;
                if (contentLength.HasValue)
                {
                    OnNewSubtask("Downloading...", contentLength.Value);
                }
                else
                {
                    OnNewSubtask("Downloading...", -1);
                }

                using (Stream contentStream = await response.Content.ReadAsStreamAsync())
                using (FileStream fileStream = new FileStream(tmpFileName, FileMode.Create, FileAccess.Write, FileShare.None, 8192, true))
                {
                    await CopyStreamAsync(contentStream, fileStream);
                }
            }

            // Rename to ZIP when finished
            var zipFileName = Path.Combine(tmpDir, Guid.NewGuid() + ".zip");
            File.Move(tmpFileName, zipFileName);
            return zipFileName;
        }

        /// <summary>
        /// Reads from the given stream and writes the content to the other stream.
        /// </summary>
        /// <returns></returns>
        private async Task CopyStreamAsync(Stream contentStream, FileStream fileStream)
        {
            var buffer = new byte[8192];
            while (true)
            {
                var read = await contentStream.ReadAsync(buffer, 0, buffer.Length);
                if (read == 0)
                {
                    return;
                }
                await fileStream.WriteAsync(buffer, 0, read);
                OnWorked(read);
            }
        }

        /// <summary>
        /// Extracts the ZIP file.
        /// </summary>
        /// <returns></returns>
        private void ExtractLauncher(string launcherZip, string targetDir)
        {
            OnNewSubtask("Installing...", -1);
            ZipFile.ExtractToDirectory(launcherZip, targetDir);
        }

        /// <summary>
        /// Removes the directory and all contained files
        /// </summary>
        private void DeleteDir(string path)
        {
            DirectoryInfo dir = new DirectoryInfo(path);
            if (dir.Exists)
            {
                dir.Delete(true);
            }
        }

        /// <summary>
        /// // Returns whether or not the launcher is already installed.
        /// </summary>
        private bool IsLauncherInstalled()
        {
            return File.Exists(Path.Combine(launcherHome, "BDeploy.exe"));
        }

        /// <summary>
        /// Notify that a new task has been started
        /// </summary>
        private void OnNewSubtask(string taskName, long totalWork)
        {
            NewSubtask?.Invoke(this, new SubTaskEventArgs(taskName, totalWork));
        }

        /// <summary>
        /// Notify that some work has been done
        /// </summary>
        private void OnWorked(long worked)
        {
            Worked?.Invoke(this, new WorkedEventArgs(worked));
        }

        /// <summary>
        /// Notify that an error occured
        /// </summary>
        private void OnError(string message)
        {
            Error?.Invoke(this, new MessageEventArgs(message));
        }
    }
}

