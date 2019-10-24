package io.bdeploy.common.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStore.ProtectionParameter;
import java.security.KeyStore.SecretKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.common.util.JacksonHelper;

/**
 * Encapsulates certificate and token handling for mutual authentication.
 */
public class SecurityHelper {

    private static final Logger log = LoggerFactory.getLogger(SecurityHelper.class);

    private static final SecurityHelper INSTANCE = new SecurityHelper();

    private SecurityHelper() {
    }

    public static SecurityHelper getInstance() {
        return INSTANCE;
    }

    /**
     * Creates a new encoded and signed token for this server.
     * <p>
     * To generate an appropriate self signed certificate in a (PKCS12) keystore,
     * use this:
     *
     * <pre>
     *   openssl req -newkey rsa:2048 -nodes -keyout key.pem -x509 -days 17800 -out cert.pem
     *   openssl pkcs12 -inkey key.pem  -in cert.pem -export -out certstore.p12
     * </pre>
     *
     * @param payload the payload to sign. will be serialized and encoded in the
     *            final signed token
     * @param keystore the keystore containing the private key. The keystore must
     *            be in PKCS12 format and contain exactly one entry, which is
     *            the private X.509 certificate.
     * @param passphrase the passphrase for both the keystore and the certificate
     *            within.
     * @return an encoded and signed token containing all security relevant
     *         information for a client to connect to this server.
     */
    public <T> String createSignaturePack(T payload, KeyStore keystore, char[] passphrase) throws GeneralSecurityException {
        SignaturePack pack = new SignaturePack();

        pack.t = createToken(payload, keystore, passphrase);
        pack.c = encode(getCertificate(keystore).getEncoded());

        return pack.toString();
    }

    /**
     * Creates a new encoded and signed token for this server.
     * <p>
     * To generate an appropriate self signed certificate in a (PKCS12) keystore,
     * use this:
     *
     * <pre>
     *   openssl req -newkey rsa:2048 -nodes -keyout key.pem -x509 -days 17800 -out cert.pem
     *   openssl pkcs12 -inkey key.pem  -in cert.pem -export -out certstore.p12
     * </pre>
     *
     * @param payload the payload to sign. will be serialized and encoded in the
     *            final signed token
     * @param keystore the keystore containing the private key. The keystore must
     *            be in PKCS12 format and contain exactly one entry, which is
     *            the private X.509 certificate.
     * @param passphrase the passphrase for both the keystore and the certificate
     *            within.
     * @return an encoded and signed token containing all security relevant
     *         information for a client to connect to this server.
     */
    public <T> String createSignaturePack(T payload, Path keystore, char[] passphrase)
            throws GeneralSecurityException, IOException {
        KeyStore ks = loadPrivateKeyStore(keystore, passphrase);
        SignaturePack pack = new SignaturePack();

        pack.t = createToken(payload, ks, passphrase);
        pack.c = encode(getCertificate(ks).getEncoded());

        return pack.toString();
    }

    /**
     * Create a valid security token suitable for HTTPS traffic verification. Used
     * to pass to clients connecting and authorizing to use APIs.
     *
     * @param payload the token payload.
     * @param ks the keystore.
     * @param passphrase the passphrase.
     * @return a signed token
     */
    public <T> String createToken(T payload, KeyStore ks, char[] passphrase) {
        try {
            PrivateKey pk = getPrivateKey(ks, passphrase);
            return getSignedToken(payload, pk).toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Accepts a token in {@link String} form, extracts the payload from it (see
     * {@link #createSignaturePack(Object, Path, char[])}) and verifies that the
     * enclosed signature is valid for the decoded payload.
     *
     * @param token the encoded payload and signature.
     * @param clazz the {@link Class} of the payload - used for
     *            de-serialization.
     * @param ks the keystore containing the private key and certificate
     * @return the signed payload, if the signature is valid.
     */
    public <T> T getVerifiedPayload(String token, Class<T> clazz, KeyStore ks) throws GeneralSecurityException {
        Certificate cert = getCertificate(ks);

        SignedPayload t = SignedPayload.parse(token);
        byte[] payloadBytes = decode(t.p);
        T payload;
        try {
            payload = getMapper().readValue(payloadBytes, clazz);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read JSON", e);
        }

        Signature sig = getSignatureAlgorithm();
        sig.initVerify(cert.getPublicKey());
        sig.update(payloadBytes);

        if (!sig.verify(decode(t.s))) {
            return null;
        }

        return payload;
    }

    /**
     * Accepts a token in {@link String} form, extracts the payload from it (see
     * {@link #createSignaturePack(Object, Path, char[])}) and verifies that the
     * enclosed signature is valid for the decoded payload using the enclosed public certificate.
     * <p>
     * This does NOT verify that the enclosed signature is valid against a present private key.
     *
     * @param token the encoded payload and signature.
     * @param clazz the {@link Class} of the payload - used for
     *            de-serialization.
     * @return the signed payload, if the signature is valid.
     */
    public <T> T getSelfVerifiedPayloadFromPack(String token, Class<T> clazz) throws GeneralSecurityException, IOException {
        SignaturePack sp = SignaturePack.parse(token);
        SignedPayload t = SignedPayload.parse(sp.t);

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(decode(sp.c))) {
            cert = cf.generateCertificate(bais);
        }

        byte[] payloadBytes = decode(t.p);
        T payload;
        try {
            payload = getMapper().readValue(payloadBytes, clazz);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read JSON", e);
        }

        Signature sig = getSignatureAlgorithm();
        sig.initVerify(cert.getPublicKey());
        sig.update(payloadBytes);

        if (!sig.verify(decode(t.s))) {
            return null;
        }

        return payload;
    }

    /**
     * Accepts an encoded and signed token and imports the enclosed security
     * relevant information into the given (JCEKS) keystore. The keystore is created
     * if it does not exist.
     *
     * @param pack the token/signature pack in {@link String} form
     * @param ks the keystore to use.
     * @param passphrase the passphrase used to decode and encode the keystore.
     */
    public void importSignaturePack(String pack, KeyStore ks, char[] passphrase) throws GeneralSecurityException, IOException {
        SignaturePack sigs = SignaturePack.parse(pack);

        String aliasCert = "cert";
        String aliasToken = "token";

        ProtectionParameter pp = passphrase == null ? null : new KeyStore.PasswordProtection(passphrase);

        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBE");
        SecretKey key = skf.generateSecret(new PBEKeySpec(sigs.t.toCharArray()));
        ks.setEntry(aliasToken, new KeyStore.SecretKeyEntry(key), pp);

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(decode(sigs.c))) {
            cert = cf.generateCertificate(bais);
        }
        ks.setCertificateEntry(aliasCert, cert);
    }

    /**
     * Loads a {@link KeyStore} from the given {@link Path} or creates one if it does not exist, imports the signature pack and
     * saves the {@link KeyStore} afterwards back to the given {@link Path}.
     *
     * @see #importSignaturePack(String, KeyStore, char[])
     */
    public void importSignaturePack(String pack, Path keystore, char[] passphrase) throws GeneralSecurityException, IOException {
        KeyStore ks = loadPublicKeyStore(keystore, passphrase);

        importSignaturePack(pack, ks, passphrase);

        try (OutputStream os = Files.newOutputStream(keystore)) {
            ks.store(os, passphrase);
        }
    }

    /**
     * Retrieve the signed token for authentication against a server using this
     * helper to decode the token.
     *
     * @param ks the public keystore
     * @param passphrase the passphrase for the keystore
     * @return an encoded token which can be sent to the server.
     */
    public String getSignedToken(KeyStore ks, char[] passphrase) throws GeneralSecurityException {
        String aliasToken = "token";
        if (!ks.containsAlias(aliasToken)) {
            throw new IllegalStateException("No access token found in keystore");
        }

        ProtectionParameter pp = passphrase == null ? null : new KeyStore.PasswordProtection(passphrase);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBE");
        SecretKeyEntry ske = (SecretKeyEntry) ks.getEntry(aliasToken, pp);
        PBEKeySpec spec = (PBEKeySpec) skf.getKeySpec(ske.getSecretKey(), PBEKeySpec.class);

        return new String(spec.getPassword());
    }

    /**
     * Load and return a PKCS12 formatted keystore.
     */
    public KeyStore loadPrivateKeyStore(Path keystore, char[] passphrase) throws GeneralSecurityException, IOException {
        try (InputStream is = Files.newInputStream(keystore)) {
            return loadPrivateKeyStore(is, passphrase);
        }

    }

    /**
     * @see #loadPrivateKeyStore(Path, char[])
     */
    public KeyStore loadPrivateKeyStore(InputStream is, char[] passphrase) throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(is, passphrase);
        ArrayList<String> aliases = Collections.list(ks.aliases());

        // only one entry permitted in the keystore
        if (aliases.size() != 1) {
            throw new IllegalArgumentException("Private Keystore must contain exactly one key");
        }
        return ks;
    }

    /**
     * Load and return (create on demand) a JCEKS formatted keystore.
     */
    public KeyStore loadPublicKeyStore(Path keystore, char[] passphrase) throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance("JCEKS");

        if (Files.exists(keystore)) {
            try (InputStream is = Files.newInputStream(keystore)) {
                return loadPublicKeyStore(is, passphrase);
            }
        } else {
            ks.load(null, passphrase);
        }

        return ks;
    }

    /**
     * @see #loadPublicKeyStore(Path, char[])
     */
    public KeyStore loadPublicKeyStore(InputStream is, char[] passphrase) throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance("JCEKS");
        ks.load(is, passphrase);
        return ks;
    }

    private PrivateKey getPrivateKey(KeyStore ks, char[] passphrase) throws GeneralSecurityException {
        String alias = ks.aliases().nextElement();
        return (PrivateKey) ks.getKey(alias, passphrase);
    }

    private Certificate getCertificate(KeyStore ks) throws KeyStoreException {
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            Certificate cert = ks.getCertificate(alias);
            if (cert != null) {
                return cert;
            }
        }
        throw new IllegalStateException("KeyStore does not contain a certificate");
    }

    private String getRawSignature(String data, PrivateKey pk) throws GeneralSecurityException {
        Signature rsa = getSignatureAlgorithm();
        rsa.initSign(pk);
        rsa.update(data.getBytes(StandardCharsets.UTF_8));
        byte[] signature = rsa.sign();

        return encode(signature);
    }

    private Signature getSignatureAlgorithm() throws NoSuchAlgorithmException {
        return Signature.getInstance("SHA256withRSA");
    }

    private SignedPayload getSignedToken(Object payload, PrivateKey pk) throws GeneralSecurityException, IOException {
        String toSign = getMapper().writeValueAsString(payload);
        String signature = getRawSignature(toSign, pk);

        SignedPayload t = new SignedPayload();
        t.p = encode(toSign.getBytes(StandardCharsets.UTF_8));
        t.s = signature;

        return t;
    }

    private static ObjectMapper getMapper() {
        return JacksonHelper.createDefaultObjectMapper();
    }

    private static String encode(byte[] bytes) {
        return Base64.encodeBase64String(bytes);
    }

    private static byte[] decode(String data) {
        return Base64.decodeBase64(data);
    }

    private static class SignedPayload {

        private String p; // payload
        private String s; // signature

        @Override
        public String toString() {
            try {
                return encode(getMapper().writeValueAsBytes(this));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Cannot write JSON", e);
            }
        }

        public static SignedPayload parse(String token) {
            try {
                return getMapper().readValue(decode(token), SignedPayload.class);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read JSON", e);
            }
        }
    }

    /**
     * Encapsulates all required certificates, keys and tokens to be able to
     * communicate with a given {@link RemoteHive} via SSL/TLS.
     */
    private static class SignaturePack {

        private String c; // certificate
        private String t; // token

        @Override
        public String toString() {
            try {
                return encode(getMapper().writeValueAsBytes(this));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Cannot write JSON", e);
            }
        }

        public static SignaturePack parse(String pack) {
            try {
                return getMapper().readValue(decode(pack), SignaturePack.class);
            } catch (IOException e) {
                log.debug("Invalid token supplied", e);
                throw new IllegalStateException("Security token invalid.");
            }
        }
    }

}
