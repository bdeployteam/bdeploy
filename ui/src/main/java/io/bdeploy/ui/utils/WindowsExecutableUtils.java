package io.bdeploy.ui.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.SignedData;
import org.bouncycastle.asn1.cms.SignerInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DigestInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.DefaultCMSSignatureAlgorithmNameGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.operator.DefaultAlgorithmNameFinder;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;

import net.jsign.DigestAlgorithm;
import net.jsign.asn1.authenticode.AuthenticodeObjectIdentifiers;
import net.jsign.pe.CertificateTableEntry;
import net.jsign.pe.DataDirectoryType;
import net.jsign.pe.PEFile;

/**
 * Provides helpers for embedding a payload into an signed PE/COFF executable
 * and for verifying if a given PE/COFF executable is properly signed. The
 * digital signature of the executable remains valid despite that we are
 * modifying it.
 * <p>
 * See https://blog.barthe.ph/2009/02/22/change-signed-executable/ for more
 * details.
 * </p>
 */
public class WindowsExecutableUtils {

    /**
     * Embeds the given bytes into given signed PE/COFF executable.
     */
    public static void embed(Path executable, byte[] data) throws IOException {
        try (PEFile pe = new PEFile(executable.toFile())) {

            List<CMSSignedData> signatures = pe.getSignatures();
            if (signatures.isEmpty()) {
                throw new RuntimeException("Only signed executables can be modified.");
            }

            // we only support a single top level signature.
            CertificateTableEntry topSignature = new CertificateTableEntry(signatures.get(0));
            byte[] signature = topSignature.toBytes();

            // append data right after each other
            // bitwise round up to next multiple of 8
            byte[] bytes = new byte[(signature.length + data.length + 7) & (-8)];
            System.arraycopy(signature, 0, bytes, 0, signature.length);
            System.arraycopy(data, 0, bytes, signature.length, data.length);

            // update the executable, table size, checksum, etc.
            pe.writeDataDirectory(DataDirectoryType.CERTIFICATE_TABLE, bytes);
        }
    }

    /**
     * A verification method for verifying signed signatures Apart from the
     * authenticode_pe.docx microsoft provides https://www.ietf.org/rfc/rfc2315.txt
     * was used to implement this method
     * <p>
     * This is a proposed implementation in JSign, but it has not yet made it into
     * the library, see: https://github.com/ebourg/jsign/pull/59 and associated
     * issues.
     * <p>
     * In addition to the proposed code, we add a check for whether the certificate
     * used to sign the binary is self-signed and reject the signature if so.
     */
    public static void verify(Path executable) throws IOException {
        try (PEFile file = new PEFile(executable.toFile())) {

            List<CMSSignedData> signatures = file.getSignatures();

            if (!signatures.isEmpty()) {

                for (CMSSignedData signedData : signatures) {

                    checkSingleSignature(file, signedData);
                }
            } else {
                throw new IllegalStateException("No Signature Present");
            }
        }
    }

    private static void checkSingleSignature(PEFile file, CMSSignedData signedData) throws IOException {
        DigestAlgorithm signedDataAlgorithm = DigestAlgorithm
                .of(signedData.getDigestAlgorithmIDs().iterator().next().getAlgorithm());
        SignerInformation signerInformation;

        // get SpcIndirectContent structure from signed data, see pefile documentation
        ContentInfo contentInfo = signedData.toASN1Structure();
        SignedData signedData1 = SignedData.getInstance(contentInfo.getContent());
        ContentInfo contentInfo1 = signedData1.getEncapContentInfo();

        // Study ASN1Dump.dumpAsString() to implement getSpcIndirectDataContent(...) or
        // any other ASN.1 parser
        DigestInfo digestInfo = getSpcIndirectDataContent(contentInfo1);
        DigestAlgorithm spcDigestAlgorithm = DigestAlgorithm.of((digestInfo.getAlgorithmId().getAlgorithm()));

        // #1 Check that the digest algorithms match
        signerInformation = getAndCheckAlgorithms(signedData, signedDataAlgorithm, spcDigestAlgorithm);

        // #2 Check the embedded hash in spcIndirectContent matches with the computed
        // hash of the pefile
        if (!Arrays.equals(file.computeDigest(signedDataAlgorithm), digestInfo.getDigest())) {
            throw new IllegalStateException("The embedded hash in the SignedData is not equal to the computed hash");
        }

        // #3 The hash of the spc blob should be equal to message digest in
        // authenticated attributes of signed data
        // Get the message digest from authenticated attributes, see authenticode_pe
        // documentation
        checkSpcDigest(signerInformation, contentInfo1, signedData);

        // #4 Check the hash in Authenticated Attributes with Encrypted Hash
        @SuppressWarnings("unchecked")
        X509CertificateHolder h = (X509CertificateHolder) signedData.getCertificates().getMatches(signerInformation.getSID())
                .iterator().next();
        JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
        X509Certificate certificate = null;
        try {
            certificate = converter.getCertificate(h);
        } catch (CertificateException e) {
            throw new IllegalStateException(e);
        }

        if (isSelfSigned(certificate)) {
            throw new IllegalStateException("Certificate is self-signed, the signature is not universally valid.");
        }

        PublicKey key = certificate.getPublicKey();
        Signature signature = null;
        try {
            SignerInfo signerInfo = signerInformation.toASN1Structure();
            String digestAndEncryptionAlgorithmName = new DefaultCMSSignatureAlgorithmNameGenerator()
                    .getSignatureName(signerInformation.getDigestAlgorithmID(), signerInfo.getDigestEncryptionAlgorithm());
            signature = Signature.getInstance(digestAndEncryptionAlgorithmName);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try {
            signature.initVerify(key);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException(e);
        }

        try {
            signature.update(signerInformation.getEncodedSignedAttributes());
            if (!signature.verify(signerInformation.getSignature())) {
                throw new IllegalStateException(
                        "The hash in the the authenticated attributes doesn't match the encrypted hash(getSignature())");
            }
            // Note that the getSignature() method returns the encrypted hash in the
            // SignerInformation
        } catch (SignatureException e) {
            throw new IllegalStateException(e);
        }

        // #4 Check the countersigner hash
        if (signerInformation.getCounterSignatures().size() != 0) {
            checkCounterSignatures(signerInformation);
        }
    }

    private static void checkCounterSignatures(SignerInformation signerInformation) {
        SignerInformation counterSignerInformation = signerInformation.getCounterSignatures().iterator().next();
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(counterSignerInformation.getDigestAlgorithmID().getAlgorithm().toString());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        md.update(signerInformation.getSignature());
        byte[] authAttrHash = md.digest();

        byte[] messageDigestInCounterSignature;
        Attribute attributeOfCounterSignature = (Attribute) counterSignerInformation.getSignedAttributes().toHashtable()
                .get(CMSAttributes.messageDigest);
        Object digestObjOfCounterSignature = attributeOfCounterSignature.getAttrValues().iterator().next();

        if (digestObjOfCounterSignature instanceof ASN1OctetString) {

            ASN1OctetString oct = (ASN1OctetString) digestObjOfCounterSignature;

            messageDigestInCounterSignature = oct.getOctets();
        } else {
            throw new IllegalStateException("No message digest was found in authenticated attributes of counter signature");
        }

        // Compare both and throw exception if not equal
        if (!Arrays.equals(messageDigestInCounterSignature, authAttrHash)) {
            throw new IllegalStateException(
                    "The digest of encrypted hash in the signerInformation does not match with digest found in counter signature");
        }
    }

    private static void checkSpcDigest(SignerInformation signerInformation, ContentInfo contentInfo1, CMSSignedData signedData)
            throws IOException {
        byte messageDigestInAuthenticatedAttr[];
        Attribute attribute = (Attribute) signerInformation.getSignedAttributes().toHashtable().get(CMSAttributes.messageDigest);
        Object digestObj = attribute.getAttrValues().iterator().next();

        if (digestObj instanceof ASN1OctetString) {
            ASN1OctetString oct = (ASN1OctetString) digestObj;
            messageDigestInAuthenticatedAttr = oct.getOctets();
        } else {
            throw new IllegalStateException("No message digest was found in authenticated attributes");
        }

        // Get the spc blob
        byte[] spcBlob = getSpcBlob(contentInfo1.getContent().toASN1Primitive());

        // Now calculate the digest of the spcblob
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(signedData.getDigestAlgorithmIDs().iterator().next().getAlgorithm().toString());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

        md.update(spcBlob);
        byte[] spcDigest = md.digest();

        // Compare both and throw exception if not equal
        if (!Arrays.equals(messageDigestInAuthenticatedAttr, spcDigest)) {
            throw new IllegalStateException(
                    "The hash of stripped content of SpcInfo does not match digest found in authenticated attributes");
        }

    }

    private static SignerInformation getAndCheckAlgorithms(CMSSignedData signedData, DigestAlgorithm signedDataAlgorithm,
            DigestAlgorithm spcDigestAlgorithm) {
        SignerInformation signerInformation;
        if (signedData.getDigestAlgorithmIDs().size() != 1) {
            throw new IllegalStateException("Signed Data must contain exactly one DigestAlgorithm");
        }

        // Check that SpcIndirectContent DigestAlgorithm equals CMSSignedData algorithm
        if (!(spcDigestAlgorithm.oid.getId()).equals(signedDataAlgorithm.oid.getId())) {
            throw new IllegalStateException("Signed Data algorithm doesn't match with spcDigestAlgorithm");
        }

        // Check that SignerInfo DigestAlgorithm equals CMSSignedData algorithm
        if (signedData.getSignerInfos().size() != 1) {
            throw new IllegalStateException("Signed Data must contain exactly one SignerInfo");
        }

        signerInformation = signedData.getSignerInfos().iterator().next();
        if (!(signerInformation.getDigestAlgorithmID().getAlgorithm().getId()).equals(signedDataAlgorithm.oid.getId())) {
            throw new IllegalStateException("Signed Data algorithm doesn't match with SignerInformation algorithm");
        }
        return signerInformation;
    }

    /**
     * Taken from AuthenticodeSigner, which also checks this.
     */
    private static boolean isSelfSigned(X509Certificate certificate) {
        return certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal());
    }

    /**
     * This method returns the SpcIndirectDataContent data structure which is
     * described in authenticode_pe.docx This structure consists of the digest of
     * the contents of the EXE which was calculated at the time of signing This will
     * be retrieved and compared to check if the digest calculated now matches with
     * this digest That's how we verify the integrity of the EXE
     *
     * @param contentInfo
     * @return
     */
    private static DigestInfo getSpcIndirectDataContent(ContentInfo contentInfo) {

        DigestInfo digestInfo;

        AlgorithmIdentifier algId = null;
        byte[] digest = null;

        if ((contentInfo.getContentType().getId()).equals(AuthenticodeObjectIdentifiers.SPC_INDIRECT_DATA_OBJID.getId())) {
            ASN1Primitive obj = contentInfo.getContent().toASN1Primitive();

            if (obj instanceof ASN1Sequence) {
                Enumeration<?> e = ((ASN1Sequence) obj).getObjects();
                e.nextElement();
                Object messageDigestObj = e.nextElement();

                if (messageDigestObj instanceof ASN1Sequence) {
                    Enumeration<?> e1 = ((ASN1Sequence) messageDigestObj).getObjects();

                    Object seq = e1.nextElement();
                    Object digestObj = e1.nextElement();

                    if (seq instanceof ASN1Sequence) {
                        Enumeration<?> e2 = ((ASN1Sequence) seq).getObjects();
                        Object digestAlgorithmObj = e2.nextElement();

                        if (digestAlgorithmObj instanceof ASN1ObjectIdentifier) {
                            AlgorithmIdentifier a = new DefaultDigestAlgorithmIdentifierFinder().find(
                                    new DefaultAlgorithmNameFinder().getAlgorithmName((ASN1ObjectIdentifier) digestAlgorithmObj));
                            algId = AlgorithmIdentifier.getInstance(a);
                        }
                    }

                    if (digestObj instanceof ASN1OctetString) {
                        ASN1OctetString oct = (ASN1OctetString) digestObj;
                        digest = oct.getOctets();
                    }
                }
            }
        }

        digestInfo = new DigestInfo(algId, digest);
        return digestInfo;
    }

    /**
     * This method returns the constituents of the spcIndirectDataContent minus the
     * some data Refer RFC2315, 9.3 to find what have been omitted and the remaining
     * is returned as bytes This will be digested and compared to the digest in the
     * AuthenticatedAttributes in the certificate
     */
    private static byte[] getSpcBlob(ASN1Primitive primitive) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        if (primitive instanceof ASN1Sequence) {
            Iterator<?> it = ((ASN1Sequence) primitive).iterator();

            while (it.hasNext()) {
                ASN1Primitive p = (ASN1Primitive) it.next();
                outputStream.write(p.getEncoded());
            }

            return outputStream.toByteArray();
        }

        return null;
    }

}
