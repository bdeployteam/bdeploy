using Bdeploy.Shared;
using Serilog;
using System.IO;

namespace Bdeploy.Launcher {

    /// <summary>
    /// Base class for all applications that are expecting a ClickAndStart file
    /// </summary>
    public abstract class ClickAndStartLauncher : BaseLauncher {

        // The full path of the .bdeploy file to launch
        protected readonly string clickAndStartFile;

        // The deserialized clickAndStartFile
        protected ClickAndStartDescriptor descriptor;

        /// <summary>
        /// Creates a new instance of the launcher.
        /// </summary>
        /// <param name="clickAndStartFile">The .bdeploy file</param>
        protected ClickAndStartLauncher(string clickAndStartFile) {
            this.clickAndStartFile = clickAndStartFile;
        }

        /// <summary>
        /// Checks that the given file point to a valid application descriptor
        /// </summary>
        protected bool ValidateDescriptor() {
            // File must exist 
            if (!File.Exists(clickAndStartFile)) {
                Log.Fatal("Application descriptor '{0}' not found.", clickAndStartFile);
                Log.Information("Exiting application.");
                return false;
            }

            // We must be able to deserialize the content
            descriptor = ClickAndStartDescriptor.FromFile(clickAndStartFile);
            if (descriptor == null) {
                Log.Fatal("Cannot deserialize application descriptor.", clickAndStartFile);
                Log.Information("Exiting application.");
                return false;
            }
            return true;
        }

        protected override string GetAppLoggerName() {
            return descriptor.ApplicationId + "-log.txt";
        }

    }
}
