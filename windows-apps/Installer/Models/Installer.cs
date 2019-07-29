using Bdeploy.Installer.Models;
using Bdeploy.Shared;
using System;
using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Runtime.Serialization.Json;
using System.Security.Cryptography.X509Certificates;
using System.Text;
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
        /// Lock file that is created to avoid that multiple installers run simultaneously
        /// </summary>
        private readonly string lockFile = Path.Combine(PathProvider.GetBdeployHome(), ".lock");

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
        /// Event that is raised when an error occurred
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
            FileStream lockStream = null;
            try
            {
                // Show error message if configuration is invalid
                if (config == null || !config.IsValid())
                {
                    StringBuilder builder = new StringBuilder();
                    builder.Append("Configuration is invalid or corrupt.").AppendLine().AppendLine();
                    builder.AppendFormat("Configuration:").AppendLine();
                    builder.Append(config == null ? "<null>" : config.ToString()).AppendLine();
                    builder.AppendFormat("Embedded:").AppendLine();
                    builder.Append(ConfigStorage.ReadEmbeddedConfigFile()).AppendLine();
                    OnError(builder.ToString());
                    return -1;
                }
                Directory.CreateDirectory(bdeployHome);
                Directory.CreateDirectory(launcherHome);
                Directory.CreateDirectory(applicationsHome);

                // Installers should not run simultaneously to avoid conflicts when extracting files
                // Thus we try to create a lockfile. If it exists we wait until it is removed
                OnNewSubtask("Waiting for other installations to finish...", -1);
                lockStream = WaitForExclusiveLock();
                if (lockStream == null)
                {
                    OnError("Installation has been canceled by the user.");
                    return -1;
                }

                // Download and store icon
                OnNewSubtask("Preparing...", -1);
                await DownloadIcon();

                // Download and extract if not available
                if (!IsLauncherInstalled())
                {
                    bool success = await DownloadAndExtractLauncher();
                    if (!success)
                    {
                        return -1;
                    }
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
            finally
            {
                // Release lock
                if (lockStream != null)
                {
                    lockStream.Dispose();
                }
                FileHelper.DeleteFile(new FileInfo(lockFile));
            }
        }

        /// <summary>
        /// Waits until this installer gets an exclusive lock.
        /// </summary>
        private FileStream WaitForExclusiveLock()
        {
            while (!Canceled)
            {
                try
                {
                    return new FileStream(lockFile, FileMode.OpenOrCreate, FileAccess.ReadWrite,
                            FileShare.None, 100);
                }
                catch (Exception)
                {
                    // Another installer is currently running. Wait for some time
                    System.Threading.Thread.Sleep(500);
                }
            }

            // User canceled waiting to get lock. Abort
            return null;
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
            File.WriteAllText(appDescriptor, config.ClickAndStartDescriptor);

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
        private async Task<bool> DownloadAndExtractLauncher()
        {
            // Launcher directory must not exist. 
            // Otherwise ZIP extraction fails
            FileHelper.DeleteDir(launcherHome);

            // Prepare tmp directory
            string tmpDir = PathProvider.GetTmpDir();
            Directory.CreateDirectory(tmpDir);

            // Download and extract
            string launcherZip = await DownloadLauncher(tmpDir);
            if (launcherZip == null)
            {
                return false;
            }

            ExtractLauncher(launcherZip, launcherHome);

            // Cleanup. Download not required any more
            FileHelper.DeleteDir(tmpDir);
            return true;
        }

        /// <summary>
        /// Downloads the icon and returns an input stream.
        /// </summary>
        public async Task DownloadIcon()
        {
            string iconFile = Path.Combine(applicationsHome, config.ApplicationUid + ".ico");
            Uri requestUrl = new Uri(config.IconUrl);
            using (HttpClient client = CreateHttpClient())
            using (HttpRequestMessage request = new HttpRequestMessage(HttpMethod.Get, requestUrl))
            using (HttpResponseMessage response = await client.GetAsync(requestUrl))
            {
                if (!response.IsSuccessStatusCode)
                {
                    Console.WriteLine("Cannot download application icon. Error {0} - {1} ", response.ReasonPhrase, request.RequestUri);
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
        /// Creates a new HTTP client that validates the certificate provided by the server against the embedded.
        /// </summary>
        /// <returns></returns>
        private HttpClient CreateHttpClient()
        {
            WebRequestHandler handler = new WebRequestHandler();
            handler.ServerCertificateValidationCallback += (sender, cert, chain, error) =>
             {
                 RemoteService remoteService = config.DeserializeDescriptor().RemoteService;
                 X509Certificate2 root = SecurityHelper.LoadCertificate(remoteService);
                 return SecurityHelper.Verify(root, (X509Certificate2)cert);
             };
            return new HttpClient(handler, true);
        }

        /// <summary>
        /// Downloads the launcher and stores it in the given directory
        /// </summary>
        private async Task<string> DownloadLauncher(string tmpDir)
        {
            Uri requestUrl = new Uri(config.LauncherUrl);
            string tmpFileName = Path.Combine(tmpDir, Guid.NewGuid() + ".download");
            using (HttpClient client = CreateHttpClient())
            using (HttpRequestMessage request = new HttpRequestMessage(HttpMethod.Get, requestUrl))
            using (HttpResponseMessage response = await client.GetAsync(requestUrl, HttpCompletionOption.ResponseHeadersRead))
            {
                if (!response.IsSuccessStatusCode)
                {
                    StringBuilder builder = new StringBuilder();
                    builder.Append("Failed to download application launcher.").AppendLine().AppendLine();
                    builder.AppendFormat("Request: {0}", request.RequestUri).AppendLine();
                    builder.AppendFormat("Status: {0}", response.StatusCode).AppendLine();
                    builder.AppendFormat("Response: {0}", response.ReasonPhrase);
                    OnError(builder.ToString());
                    return null;
                }

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
        /// Notify that an error occurred
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

