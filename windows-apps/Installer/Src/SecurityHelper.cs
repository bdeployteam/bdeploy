using Bdeploy.Shared;
using System.IO;
using System.Linq;
using System.Runtime.Serialization.Json;
using System.Security.Cryptography.X509Certificates;

namespace Bdeploy.Installer.Models
{
    class SecurityHelper {
        /// <summary>
        /// Loads the X509Certificate required to communicate with the given service.
        /// </summary>
        /// <returns></returns>
        public static X509Certificate2 LoadCertificate(RemoteService service) {
            byte[] decodedPack = System.Convert.FromBase64String(service.SignaturePack);

            DataContractJsonSerializer ser = new DataContractJsonSerializer(typeof(SignaturePack));
            MemoryStream stream = new MemoryStream(decodedPack);
            SignaturePack signaturePack = (SignaturePack)ser.ReadObject(stream);

            byte[] decodedToken = System.Convert.FromBase64String(signaturePack.Certificate);

            X509Certificate2 x509 = new X509Certificate2();
            x509.Import(decodedToken);
            return x509;
        }

        /// <summary>
        /// Verifies that the provided certificate matches the given other certificate and that they are still valid.
        /// 
        /// <para>
        /// Custom verification is required as our certificates are typically self-signed and not created by a root authority that the OS trusts.
        /// Therefore we need to allow unknown certificate authorities and check if the it is the one that we are expecting.
        /// See https://github.com/dotnet/corefx/issues/30284 for more details.
        /// </para>
        /// 
        /// </summary>
        /// <param name="root">The baseline to compare.</param>
        /// <param name="cert">The certificate to validate.</param>
        /// <returns></returns>
        public static bool Verify(X509Certificate2 root, X509Certificate2 cert) {
            X509Chain chain = new X509Chain();
            chain.ChainPolicy.RevocationMode = X509RevocationMode.NoCheck;
            chain.ChainPolicy.ExtraStore.Add(root);
            chain.ChainPolicy.VerificationFlags = X509VerificationFlags.AllowUnknownCertificateAuthority;
            var isValid = chain.Build(cert);
            isValid = isValid && cert.RawData.SequenceEqual(root.RawData);
            return isValid;
        }
    }
}
