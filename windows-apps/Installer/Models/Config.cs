using System;
using System.IO;
using System.Reflection;
using System.Runtime.Serialization;
using System.Runtime.Serialization.Json;
using System.Text;
using System.Text.RegularExpressions;
using System.Windows;

namespace Bdeploy.Installer.Models
{
    [DataContract]
    public class Config
    {
        /// <summary>
        /// The URL to download the launcher ZIP. 
        /// </summary>
        [DataMember(Name="launcherUrl")]
        public string LauncherUrl;

        /// <summary>
        /// The URL to download the ICON. 
        /// </summary>
        [DataMember(Name = "iconUrl")]
        public string IconUrl;

        /// <summary>
        /// The serialized .beploy file that is written to the local storage
        /// </summary>
        [DataMember(Name = "applicationJson")]
        public string ApplicationJson;

        /// <summary>
        /// The human readable application name. Used to name the desktop shortcut
        /// </summary>
        [DataMember(Name = "applicationName")]
        public string ApplicationName;

        /// <summary>
        /// The unique name of the application. Used to store the .bdeploy file
        /// </summary>
        [DataMember(Name = "applicationUid")]
        public string ApplicationUid;

        /// <summary>
        /// Prints the configuration to the console
        /// </summary>
        internal void LogToConsole()
        {
            Console.WriteLine("Id: {0}", ApplicationUid);
            Console.WriteLine("Name: {0}", ApplicationName);
            Console.WriteLine("URL: {0}", LauncherUrl);
            Console.WriteLine("Icon: {0}", IconUrl);
            Console.WriteLine("Payload: {0}", ApplicationJson);
        }
    }

    /// <summary>
    /// Knows how to read and write the embedded configuration file. 
    /// 
    /// The configuration file has a special format where the acutal data is surrounded by start
    /// and end tags. Layout:
    /// <!--
    ///     START_BDEPLOY
    ///       START_CONFIG
    ///        CONFIG
    ///       END_CONFIG
    ///       RANDOM_DATA
    ///     END_BDEPLOY
    /// -->
    /// 
    /// The embedded configuration is changed by a Java Application that will parse the
    /// executable and update the embedded config. Inserting strings that are larger than 
    /// the pre-defined ones would break the layout of the executable and render it unsuable.
    /// To aovid that, the random data block is truncated by the Java application. Thus after
    /// modifications the size of the entire block is not changed.
    /// 
    /// </summary>
    public class ConfigStorage
    {
        public static readonly string RESOURCE_NAME = "Bdeploy.Installer.Resources.config.txt";

        public static readonly string START_MARKER = "###START_BDEPLOY###";
        public static readonly string START_CONFIG = "###START_CONFIG###";
        public static readonly string END_CONFIG = "###END_CONFIG###";
        public static readonly string END_MARKER = "###END_BDEPLOY###";

        public static readonly long FILE_SIZE = 8192;

        /// <summary>
        /// Returns the configuration to use. Either the one passed to the application or the embedded is used.
        /// </summary>
        public static Config GetConfig(StartupEventArgs e)
        {
            if (e.Args.Length == 1 && File.Exists(e.Args[0]))
            {
                return ConfigStorage.ReadConfigurationFromFile(e.Args[0]);
            }
            return ConfigStorage.ReadEmbeddedConfiguration();
        }

        /// <summary>
        /// De-Serializes the configuration from the given file.
        /// </summary>
        /// <returns></returns>
        public static Config ReadConfigurationFromFile(string file)
        {
            string payload = File.ReadAllText(file);
            return DeserializeConfig(payload);
        }

        /// <summary>
        /// De-Serializes the embedded configuration 
        /// </summary>
        /// <returns></returns>
        public static Config ReadEmbeddedConfiguration()
        {
            string payload = ReadEmbeddedConfigFile();
            return DeserializeConfig(payload);
        }

        /// <summary>
        /// Parses the payload in order to extract the deserialze the configuration object.
        /// </summary>
        private static Config DeserializeConfig(string payload)
        {
            string regex = string.Format("{0}.*{1}(.*){2}.*{3}", START_MARKER, START_CONFIG, END_CONFIG, END_MARKER);
            Regex rx = new Regex(regex, RegexOptions.Singleline | RegexOptions.IgnoreCase);
            Match match = rx.Match(payload);
            if (!match.Success)
            {
                Console.WriteLine("Regex not matching.");
                return null;
            }
            string value = match.Groups[1].Value.Trim();
            UTF8Encoding encoding = new UTF8Encoding(false);
            DataContractJsonSerializer ser = new DataContractJsonSerializer(typeof(Config));
            Config config = (Config)ser.ReadObject(new MemoryStream(encoding.GetBytes(value)));
            return config;
        }

        /// <summary>
        /// Serializes the configururation to the given file. 
        /// </summary>
        public static void WriteConfiguration(string file, Config config)
        {
            var encoding = new UTF8Encoding(false);
            using (StreamWriter writer = new StreamWriter(new FileStream(file, FileMode.Create), encoding))
            {
                // Write header of file
                writer.Write(START_MARKER);

                // Write JSON 
                writer.Write(START_CONFIG);
                writer.Flush();
                DataContractJsonSerializer ser = new DataContractJsonSerializer(typeof(Config));
                ser.WriteObject(writer.BaseStream, config);
                writer.Flush();
                writer.Write(END_CONFIG);

                // Write dummy-data at the end until the desired size is reached
                writer.Flush();
                long remaingBytes = FILE_SIZE - writer.BaseStream.Length - END_MARKER.Length;
                for (long i = 0; i < remaingBytes; i++)
                {
                    writer.Write('0');
                }
                writer.Write(END_MARKER);
            }
        }

        /// <summary>
        /// Reads the embedded configuration file and returns the payload as string
        /// </summary>
        /// <returns></returns>
        private static string ReadEmbeddedConfigFile()
        {
            var assembly = Assembly.GetExecutingAssembly();
            using (Stream stream = assembly.GetManifestResourceStream(RESOURCE_NAME))
            using (StreamReader reader = new StreamReader(stream))
            {
                return reader.ReadToEnd();
            }
        }

    }
}
