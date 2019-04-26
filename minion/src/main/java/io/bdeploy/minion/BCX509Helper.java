package io.bdeploy.minion;

import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Create X.509 certificates and private keys for Minion Servers.
 */
public class BCX509Helper {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String SECURITY_PROVIDER_ID = "BC";

    public static void createKeyStore(Path target, char[] passphrase) throws Exception {
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
        keyStore.setKeyEntry("1", kp.getPrivate(), passphrase, new java.security.cert.Certificate[] { cert });

        try (OutputStream os = Files.newOutputStream(target)) {
            keyStore.store(os, passphrase);
        }
    }

}
