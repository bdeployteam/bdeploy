using System;
using System.IO;

namespace Bdeploy.Shared {
    /// <summary>
    /// Utility methods to work with files
    /// </summary>
    public class FileHelper {
        /// <summary>
        /// Moves all files and folders in the given source directory into the given target directory.
        /// </summary>
        /// <param name="sourceDirName">The source directory. Must exist.</param>
        /// <param name="destDirName">The target directory. Created if it does not exist.</param>
        /// <param name="excludedDirs">Array of relative directory names that are excluded from moving.</param>
        public static void MoveDirectory(string sourceDirName, string destDirName, string[] excludedDirs) {
            // If the destination directory doesn't exist, create it.
            if (!Directory.Exists(destDirName)) {
                Directory.CreateDirectory(destDirName);
            }

            // Move all files
            DirectoryInfo dir = new DirectoryInfo(sourceDirName);
            foreach (FileInfo file in dir.GetFiles()) {
                string temppath = Path.Combine(destDirName, file.Name);
                file.MoveTo(temppath);
            }

            // Move all directories
            foreach (DirectoryInfo subdir in dir.GetDirectories()) {
                if (Array.IndexOf(excludedDirs, subdir.Name) != -1) {
                    continue;
                }
                string temppath = Path.Combine(destDirName, subdir.Name);
                subdir.MoveTo(temppath);
            }
        }

        /// <summary>
        /// Copies the content of the given source directory - including all subdirectories and files - to the given destination direcotry.
        /// Existing files are overwritten.
        /// </summary>
        /// <param name="sourceDirName">The source directory. Must exist.</param>
        /// <param name="destDirName">The target directory. Created if it does not exist. </param>
        public static void CopyDirectory(string sourceDirName, string destDirName) {
            // If the destination directory doesn't exist, create it.
            if (!Directory.Exists(destDirName)) {
                Directory.CreateDirectory(destDirName);
            }

            // Get the files in the directory and copy them to the new location.
            DirectoryInfo dir = new DirectoryInfo(sourceDirName);
            foreach (FileInfo file in dir.GetFiles()) {
                string temppath = Path.Combine(destDirName, file.Name);
                file.CopyTo(temppath, true);
            }

            // Copy all sub direcotriies
            foreach (DirectoryInfo subdir in dir.GetDirectories()) {
                string temppath = Path.Combine(destDirName, subdir.Name);
                CopyDirectory(subdir.FullName, temppath);
            }
        }

        /// <summary>
        /// Removes the directory and all contained files. Return code indicates success.
        /// </summary>
        public static bool DeleteDir(string path) {
            try {
                Directory.Delete(path, true);
                return true;
            } catch (Exception) {
                return false;
            }
        }

        /// <summary>
        /// Removes the given directory only if it is empty.
        /// </summary>
        public static bool DeleteDirIfEmpty(string path) {
            try {
                DirectoryInfo dir = new DirectoryInfo(path);
                if (dir.GetFiles().Length == 0 && dir.GetDirectories().Length == 0) {
                    DeleteDir(path);
                    return true;
                }
                return false;
            } catch (Exception) {
                return false;
            }
        }

        /// <summary>
        /// Tries to delete the given file. Return code indicates success.
        /// </summary>
        public static bool DeleteFile(string file) {
            try {
                File.Delete(file);
                return true;
            } catch (Exception) {
                return false;
            }
        }

        /// <summary>
        /// Waits until the an exlusive lock on the given file can be created.
        /// </summary>
        public static FileStream WaitForExclusiveLock(string lockFile, int millisecondsToSleep, Func<bool> cancel) {
            while (!cancel.Invoke()) {
                FileStream stream = CreateLockFile(lockFile);
                if (stream != null) {
                    return stream;
                }
                System.Threading.Thread.Sleep(millisecondsToSleep);
            }
            return null;
        }

        /// <summary>
        /// Attempts to get an exclusive write lock on the given file. 
        /// </summary>
        /// <param name="lockFile">The file to open</param>
        /// <returns>The stream or null if another process holds a lock on the file.</returns>
        public static FileStream CreateLockFile(string lockFile) {
            try {
                return new FileStream(lockFile, FileMode.OpenOrCreate, FileAccess.ReadWrite,
                        FileShare.None, 100);
            } catch (Exception) {
                return null;
            }
        }


        /// <summary>
        /// Tries to acquire an exclusive lock on the given file to determine whether or not it is currently in use by another process. 
        /// </summary>
        public static bool IsFileLocked(string file) {
            try {
                using (FileStream stream = new FileStream(file, FileMode.Open, FileAccess.ReadWrite, FileShare.None))
                    return false;
            } catch (Exception) {
                return true;
            }
        }

        /// <summary>
        /// Tests if the given location can be modified.
        /// </summary>
        /// <param name="path">the path to the directory to test</param>
        /// <returns></returns>
        public static bool IsReadOnly(string path) {
            try {
                // Ensure that the parent directory is existing
                Directory.CreateDirectory(path);

                // Create a random new file
                var file = Path.Combine(path, Guid.NewGuid().ToString());
                FileStream stream = new FileStream(file, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None, 100);

                // Dispose and remove if success -> write permissions
                stream.Dispose();
                File.Delete(file);
                return false;
            } catch (Exception) {
                return true;
            }
        }

        /// <summary>
        /// Replaces all characters that cannot be used in a filename/directoryname with an underscore.
        /// See https://stackoverflow.com/a/12800424
        /// </summary>
        /// <returns></returns>
        public static string GetSafeFilename(string filename) {
            return string.Join("_", filename.Split(Path.GetInvalidFileNameChars()));
        }
    }
}
