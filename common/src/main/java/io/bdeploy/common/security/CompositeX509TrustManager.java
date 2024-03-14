package io.bdeploy.common.security;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * Represents an ordered list of {@link X509TrustManager}s with additive trust. If any one of the composed managers
 * trusts a certificate chain, then it is trusted by the composite manager.
 * This is necessary because of the fine-print on {@link SSLContext#init}: Only the first instance of a particular key
 * and/or trust manager implementation type in the array is used. (For example, only the first
 * javax.net.ssl.X509KeyManager in the array will be used.)
 *
 * @author codyaray
 * @since 4/22/2013
 * @see <a href="http://stackoverflow.com/questions/1793979/registering-multiple-keystores-in-jvm">
 *      http://stackoverflow.com/questions/1793979/registering-multiple-keystores-in-jvm
 *      </a>
 */
public class CompositeX509TrustManager implements X509TrustManager {

    private final List<X509TrustManager> trustManagers;

    public CompositeX509TrustManager(KeyStore keystore) {
        this.trustManagers = List.of(getDefaultTrustManager(), getTrustManager(keystore));
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        for (X509TrustManager trustManager : trustManagers) {
            try {
                trustManager.checkClientTrusted(chain, authType);
                return; // someone trusts them. success!
            } catch (CertificateException e) {
                // maybe someone else will trust them
            }
        }
        throw new CertificateException("None of the TrustManagers trust this certificate chain");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        for (X509TrustManager trustManager : trustManagers) {
            try {
                trustManager.checkServerTrusted(chain, authType);
                return; // someone trusts them. success!
            } catch (CertificateException e) {
                // maybe someone else will trust them
            }
        }
        throw new CertificateException("None of the TrustManagers trust this certificate chain");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        ImmutableList.Builder<X509Certificate> certificates = ImmutableList.builder();
        for (X509TrustManager trustManager : trustManagers) {
            for (X509Certificate cert : trustManager.getAcceptedIssuers()) {
                certificates.add(cert);
            }
        }
        return Iterables.toArray(certificates.build(), X509Certificate.class);
    }

    /**
     * @param keyStore the key store with additional certificates to trust.
     * @return a {@link TrustManager} which trusts the default (global CA) certificates as well as additional ones from the given
     *         keystore.
     */
    public static TrustManager[] getTrustManagers(KeyStore keyStore) {
        return new TrustManager[] { new CompositeX509TrustManager(keyStore) };
    }

    private static X509TrustManager getDefaultTrustManager() {
        return getTrustManager(null);
    }

    private static X509TrustManager getTrustManager(KeyStore keystore) {
        return getTrustManager(TrustManagerFactory.getDefaultAlgorithm(), keystore);
    }

    private static X509TrustManager getTrustManager(String algorithm, KeyStore keystore) {
        TrustManagerFactory factory;

        try {
            factory = TrustManagerFactory.getInstance(algorithm);
            factory.init(keystore);
            return Arrays.stream(factory.getTrustManagers()).filter(X509TrustManager.class::isInstance)
                    .map(X509TrustManager.class::cast).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Cannot find X509 trust manager"));
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new IllegalStateException("Cannot create trust manager", e);
        }
    }
}
