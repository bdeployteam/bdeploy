using System;
using System.IO;

namespace Bdeploy.Shared
{
    /// <summary>
    /// Utility methods to work with files
    /// </summary>
    public class FileHelper
    {
        /// <summary>
        /// Removes the directory and all contained files
        /// </summary>
        public static void DeleteDir(string path)
        {
            DirectoryInfo dir = new DirectoryInfo(path);
            if (dir.Exists)
            {
                dir.Delete(true);
            }
        }

        /// <summary>
        /// Tries to delete the given file. Fails silently if not posible
        /// </summary>
        public static void DeleteFile(FileInfo file)
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
        /// Returns whether or not the given file is locked.
        /// </summary>
        public static bool IsFileLocked(FileInfo file)
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
