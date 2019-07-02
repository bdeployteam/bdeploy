using Bdeploy.Installer.Models;
using Bdeploy.Shared;
using System;
using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Threading.Tasks;

namespace Bdeploy.Installer
{
    /// <summary>
    /// Downloads and unpacks the launcher.
    /// </summary>
    public class AppInstaller
    {
        /// <summary>
        /// Directory where BDeploy stores all files 
        /// </summary>
        private readonly string bdeployHome = PathProvider.GetBdeployHome();

        /// <summary>
        /// Directory where the launcher is stored. (HOME_DIR\launcher) 
        /// </summary>
        private readonly string launcherHome = PathProvider.GetLauncherDir();

        /// <summary>
        /// Directory where the application are stored. (HOME_DIR\applications) 
        /// </summary>
        private readonly string applicationsHome = PathProvider.GetApplicationsDir();

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
        /// Event that is raised when the icon has been loaded
        /// </summary>
        public event EventHandler<IconEventArgs> IconLoaded;

        /// <summary>
        /// Embedded configuration object
        /// </summary>
        public readonly Config config;

        /// <summary>
        /// Creates a new installer instance.
        /// </summary>
        public AppInstaller(Config config)
        {
            this.config = config;
        }

        /// <summary>
        /// Launches the application. Does not wait for termination
        /// </summary>
        /// <returns></returns>
        public void Launch()
        {
            string appUid = config.ApplicationUid;
            string appShortcut = Path.Combine(applicationsHome, appUid + ".bdeploy");
            Utils.RunProcess(PathProvider.GetLauncherExecutable(), appShortcut);
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
                Directory.CreateDirectory(launcherHome);
                Directory.CreateDirectory(applicationsHome);

                // Download and store icon
                await DownloadIcon();

                // Download and extract if not available
                if (!IsLauncherInstalled())
                {
                    await DownloadAndExtractLauncher();
                }

                // Associate bdeploy files with the launcher
                CreateFileAssociation();

                // Launch application
                ExtractApplication();
                return 0;
            }
            catch (Exception ex)
            {
                OnError(ex.Message);
                return -1;
            }
        }

        /// <summary>
        /// Associates .bdeploy files with the launcher
        /// </summary>
        private void CreateFileAssociation()
        {
            string launcher = PathProvider.GetLauncherExecutable();
            string fileAssoc = PathProvider.GetFileAssocExecutable();
            string arguments = string.Format("{0} \"{1}\"", "/CreateForCurrentUser", launcher);
            Utils.RunProcess(fileAssoc, arguments);
        }

        /// <summary>
        /// Extracts the embedded application and writes a new file
        /// </summary>
        private void ExtractApplication()
        {
            string appUid = config.ApplicationUid;
            string appName = config.ApplicationName;
            string appDescriptor = Path.Combine(applicationsHome, appUid + ".bdeploy");
            string icon = Path.Combine(applicationsHome, appUid + ".ico");

            // Always write file as it might be outdated
            bool createShortcut = !File.Exists(appDescriptor);
            File.WriteAllText(appDescriptor, config.ApplicationJson);

            // Only create shortcut if we just have written the descriptor
            if (createShortcut)
            {
                Shortcut.CreateDesktopLink(appName, appDescriptor, launcherHome, icon);
                Shortcut.CreateStartMenuLink(appName, appDescriptor, launcherHome, icon);
            }
        }

        /// <summary>
        /// Downloads and extracts the launcher.
        /// </summary>
        private async Task DownloadAndExtractLauncher()
        {
            // Launcher directory must not exist. 
            // Otherwise ZIP extraction fails
            FileHelper.DeleteDir(launcherHome);

            // Prepare tmp directory
            string tmpDir = PathProvider.GetTmpDir();
            Directory.CreateDirectory(tmpDir);

            // Download and extract
            string launcherZip = await DownloadLauncher(tmpDir);
            ExtractLauncher(launcherZip, launcherHome);

            // Cleanup. Download not required any more
            FileHelper.DeleteDir(tmpDir);
        }

        /// <summary>
        /// Downloads the icon and returns an input stream.
        /// </summary>
        public async Task DownloadIcon()
        {
            var iconFile = Path.Combine(applicationsHome, config.ApplicationUid + ".ico");
            using (HttpClient client = new HttpClient())
            {
                Uri requestUrl = new Uri(config.IconUrl);
                HttpRequestMessage request = new HttpRequestMessage(HttpMethod.Get, requestUrl);
                HttpResponseMessage response = await client.GetAsync(requestUrl);
                if (!response.IsSuccessStatusCode)
                {
                    return;
                }
                using (Stream responseStream = await response.Content.ReadAsStreamAsync())
                using (FileStream fileStream = new FileStream(iconFile, FileMode.Create))
                {
                    await responseStream.CopyToAsync(fileStream);
                }

                // Notify UI that we have an icon
                OnIconLoaded(iconFile);
            }
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
                Uri requestUrl = new Uri(config.LauncherUrl);
                HttpRequestMessage request = new HttpRequestMessage(HttpMethod.Get, requestUrl);
                HttpResponseMessage response = await client.GetAsync(requestUrl, HttpCompletionOption.ResponseHeadersRead);

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
            using (ZipArchive archive = ZipFile.OpenRead(launcherZip))
            {
                OnNewSubtask("Unpacking...", archive.Entries.Count);
                // Enforce directory separator at the end. 
                if (!targetDir.EndsWith(Path.DirectorySeparatorChar.ToString(), StringComparison.Ordinal))
                {
                    targetDir += Path.DirectorySeparatorChar;
                }
                foreach (ZipArchiveEntry entry in archive.Entries)
                {
                    // ZIP contains a single directory with all files in it
                    // Thus we remove the starting directory to unpack all files directly into the target directory
                    string entryName = entry.FullName.Replace('\\', '/');
                    string extractName = entryName.Substring(entryName.IndexOf('/') + 1);
                    string destination = Path.GetFullPath(Path.Combine(targetDir, extractName));

                    // Ensure we do not extract to a directory outside of our control
                    if (!destination.StartsWith(targetDir, StringComparison.Ordinal))
                    {
                        Console.WriteLine("ZIP-Entry contains invalid path. Expecting: {0} but was {1}", targetDir, destination);
                        continue;
                    }

                    // Directory entries do not have the name attribute
                    bool isDirectory = entry.Name.Length == 0;
                    if (isDirectory)
                    {
                        Directory.CreateDirectory(destination);
                    }
                    else
                    {
                        entry.ExtractToFile(destination);
                    }

                    // Notify about extraction progress
                    OnWorked(1);
                }
            }
        }

        /// <summary>
        /// Returns whether or not the launcher is already installed.
        /// </summary>
        private bool IsLauncherInstalled()
        {
            return File.Exists(PathProvider.GetLauncherExecutable());
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

        /// <summary>
        /// Notify that the icon has been downloaded
        /// </summary>
        private void OnIconLoaded(string iconPath)
        {
            IconLoaded?.Invoke(this, new IconEventArgs(iconPath));
        }
    }
}

