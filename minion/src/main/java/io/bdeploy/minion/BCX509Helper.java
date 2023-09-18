package io.bdeploy.minion;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.OperatorException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import io.bdeploy.common.security.SecurityHelper;

/**
 * Create X.509 certificates and private keys for Minion Servers.
 */
public class BCX509Helper {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String SECURITY_PROVIDER_ID = "BC";

    private BCX509Helper() {
    }

    public static void createKeyStore(Path target, char[] passphrase)
            throws GeneralSecurityException, IOException, OperatorException {
        // set security Provider
        Security.addProvider(new BouncyCastleProvider());

        X500Name root = new X500NameBuilder().addRDN(BCStyle.CN, "BDeploy") // Common Name
                .addRDN(BCStyle.O, "BDeploy Team") // Organization
                .addRDN(BCStyle.C, "AT") // Country Code
                .addRDN(BCStyle.OU, "BDeploy") // Organizational Unit
                .build();

        final Date validFrom = new Date();
        final Date validUntil = new Date(validFrom.getTime() + TimeUnit.DAYS.toMillis(17800));

        KeyPairGenerator kpGen = KeyPairGenerator.getInstance(KEY_ALGORITHM, SECURITY_PROVIDER_ID);
        kpGen.initialize(2048, RANDOM);
        KeyPair kp = kpGen.generateKeyPair();

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(root, // issuer authority
                BigInteger.valueOf(RANDOM.nextInt()), // serial number of certificate
                validFrom, // start of validity
                validUntil, // end of certificate validity
                root, // subject name of certificate
                kp.getPublic()); // public key of certificate

        // key usage restrictions
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(
                KeyUsage.keyCertSign | KeyUsage.digitalSignature | KeyUsage.keyEncipherment | KeyUsage.dataEncipherment));
        builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(true));

        X509Certificate cert = new JcaX509CertificateConverter().//
                getCertificate(builder.build(new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).//
                        setProvider(SECURITY_PROVIDER_ID).//
                        build(kp.getPrivate()))); // private key of signing authority , here it is self signed

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry(SecurityHelper.ROOT_ALIAS, kp.getPrivate(), passphrase,
                new java.security.cert.Certificate[] { cert });

        try (OutputStream os = Files.newOutputStream(target)) {
            keyStore.store(os, passphrase);
        }
    }

    public static void createEmptyKeystore(Path target, char[] passphrase)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        try (OutputStream os = Files.newOutputStream(target)) {
            keyStore.store(os, passphrase);
        }
    }

    public static void updatePrivateCertificate(Path ks, char[] pass, Path certifcate)
            throws GeneralSecurityException, IOException {
        Security.addProvider(new BouncyCastleProvider());

        // backup before doing it...
        Files.copy(ks, ks.getParent().resolve(ks.getFileName().toString() + ".bak"), StandardCopyOption.REPLACE_EXISTING);

        KeyStore keyStore = SecurityHelper.getInstance().loadPrivateKeyStore(ks, pass);

        List<Certificate> chain = new ArrayList<>();
        KeyPair kp = null;

        try (PEMParser parser = new PEMParser(Files.newBufferedReader(certifcate))) {
            Object pemObject;

            while ((pemObject = parser.readObject()) != null) {
                if (pemObject instanceof X509CertificateHolder xch) {
                    chain.add(new JcaX509CertificateConverter().getCertificate(xch));
                }

                if (pemObject instanceof PEMKeyPair pkp) {
                    if (kp != null) {
                        throw new IllegalArgumentException("PEM contains multiple kay pairs");
                    }

                    kp = new JcaPEMKeyConverter().getKeyPair(pkp);
                }
            }
        }

        if (kp == null || chain.isEmpty()) {
            throw new IllegalArgumentException("Given certificate has either no key or empty certificate chain");
        }

        // replace the existing one...
        keyStore.setKeyEntry(SecurityHelper.ROOT_ALIAS, kp.getPrivate(), pass, chain.toArray(Certificate[]::new));

        try (OutputStream os = Files.newOutputStream(ks)) {
            keyStore.store(os, pass);
        }
    }

    public static void exportPrivateCertificateAsPem(Path ks, char[] pass, Path pem)
            throws GeneralSecurityException, IOException {
        Security.addProvider(new BouncyCastleProvider());

        if (Files.exists(pem)) {
            throw new IllegalStateException("Will not overwrite existing PEM: " + pem);
        }

        KeyStore keyStore = SecurityHelper.getInstance().loadPrivateKeyStore(ks, pass);

        PrivateKey pk = (PrivateKey) keyStore.getKey(SecurityHelper.ROOT_ALIAS, pass);
        String alias = SecurityHelper.ROOT_ALIAS;
        if (!keyStore.containsAlias(SecurityHelper.ROOT_ALIAS)) {
            alias = SecurityHelper.CERT_ALIAS;
        }
        Certificate cert = keyStore.getCertificate(alias);

        try (OutputStreamWriter osw = new OutputStreamWriter(Files.newOutputStream(pem))) {
            try (JcaPEMWriter writer = new JcaPEMWriter(osw)) {
                writer.writeObject(cert);
                writer.writeObject(pk);
            }
        }
    }

}
