
using System.IO;
using System.Runtime.Serialization;
using System.Runtime.Serialization.Json;
using System.Text;

namespace Bdeploy.Shared
{
    /// <summary>
    /// Represents the configuration of the Installer. 
    /// The configuration is serialized and dynamically embedded into the exeuctable.
    /// </summary>
    [DataContract]
    public class Config
    {
        /// <summary>
        /// The URL to download the launcher ZIP. 
        /// </summary>
        [DataMember(Name = "launcherUrl")]
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
        public string ClickAndStartDescriptor;

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
        /// Returns a string representation of the object.
        /// </summary>
        public override string ToString()
        {
            StringBuilder builder = new StringBuilder();
            builder.AppendFormat("Id: {0}", ApplicationUid).AppendLine();
            builder.AppendFormat("Name: {0}", ApplicationName).AppendLine();
            builder.AppendFormat("URL: {0}", LauncherUrl).AppendLine();
            builder.AppendFormat("Icon: {0}", IconUrl).AppendLine();
            builder.AppendFormat("Payload: {0}", ClickAndStartDescriptor).AppendLine();
            return builder.ToString();
        }

        /// <summary>
        /// Returns whether or not all required parameters are set to a non-null value. 
        /// </summary>
        public bool IsValid()
        {
            return LauncherUrl != null && IconUrl != null && ClickAndStartDescriptor != null && ApplicationName != null && ApplicationUid != null;
        }
    }

    /// <summary>
    /// Represents the content of the Click&Start file.
    /// </summary>
    [DataContract]
    [KnownType(typeof(RemoteService))]
    public class ClickAndStartDescriptor
    {
        /// <summary>
        /// The URL to download the launcher ZIP. 
        /// </summary>
        [DataMember(Name = "host")]
        public RemoteService RemoteService;

        /// <summary>
        /// Instance Group ID.
        /// </summary>
        [DataMember(Name = "groupId")]
        public string GroupId;

        /// <summary>
        /// Instance ID
        /// </summary>
        [DataMember(Name = "instanceId")]
        public string InstanceId;

        /// <summary>
        /// Application Id
        /// </summary>
        [DataMember(Name = "applicationId")]
        public string ApplicationId;

        /// <summary>
        /// Returns a string representation of the object.
        /// </summary>
        public override string ToString()
        {
            StringBuilder builder = new StringBuilder();
            builder.AppendFormat("Host: {0}", RemoteService).AppendLine();
            builder.AppendFormat("GroupId: {0}", GroupId).AppendLine();
            builder.AppendFormat("InstanceId: {0}", InstanceId).AppendLine();
            builder.AppendFormat("ApplicationId: {0}", ApplicationId).AppendLine();
            return builder.ToString();
        }

        /// <summary>
        /// Returns whether or not all required parameters are set to a non-null value. 
        /// </summary>
        public bool IsValid()
        {
            return RemoteService != null && GroupId != null && InstanceId != null && ApplicationId != null;
        }

        /// <summary>
        /// Deserializes the given JSON that represents the serialized descriptor. 
        /// </summary>
        public static ClickAndStartDescriptor FromString(string input)
        {
            UTF8Encoding encoding = new UTF8Encoding(false);
            DataContractJsonSerializer ser = new DataContractJsonSerializer(typeof(ClickAndStartDescriptor));
            MemoryStream stream = new MemoryStream(encoding.GetBytes(input));
            return (ClickAndStartDescriptor)ser.ReadObject(stream);
        }

        /// <summary>
        /// Deserializes the given JSON that represents the serialized descriptor. 
        /// </summary>
        public static ClickAndStartDescriptor FromFile(string file)
        {
            UTF8Encoding encoding = new UTF8Encoding(false);
            string content = File.ReadAllText(file, encoding);
            return FromString(content);
        }
    }

    /// <summary>
    /// Describes where the remote service is running and contains the token to access it.
    /// </summary>
    [DataContract]
    public class RemoteService
    {
        /// <summary>
        /// The URI under which the referenced minion is reachable.
        /// </summary>
        [DataMember(Name = "uri")]
        public string Uri;

        /// <summary>
        /// Certificate and token to access the instance.
        /// </summary>
        [DataMember(Name = "authPack")]
        public string SignaturePack;

        /// <summary>
        /// Returns a string representation of the object.
        /// </summary>
        public override string ToString()
        {
            StringBuilder builder = new StringBuilder();
            builder.AppendFormat("Uri: {0}", Uri).AppendLine();
            builder.AppendFormat("SignaturePack: {0}", SignaturePack).AppendLine();
            return builder.ToString();
        }

        /// <summary>
        /// Returns whether or not all required parameters are set to a non-null value. 
        /// </summary>
        public bool IsValid()
        {
            return Uri != null && SignaturePack != null;
        }
    }

    /// <summary>
    /// Encapsulates all required certificates, keys and tokens to be able to communicate with a given minion via SSL/TLS.
    /// </summary>
    [DataContract]
    public class SignaturePack
    {
        [DataMember(Name = "c")]
        public string Certificate;

        [DataMember(Name = "t")]
        public string Token; 

    }

}
