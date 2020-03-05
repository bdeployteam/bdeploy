package io.bdeploy.jersey.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.core.UriBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;

/**
 * Manages stored local login sessions for CLI or build tools.
 * <p>
 * Sessions are stored in a file in a given root directory. CLI tools typically will use the user's home, but build tools may
 * decide to store sessions elsewhere.
 */
public class LocalLoginManager {

    private static final Logger log = LoggerFactory.getLogger(LocalLoginManager.class);

    private final Path home;

    public LocalLoginManager(Path home) {
        this.home = home;
    }

    public LocalLoginManager() {
        this(Paths.get(System.getProperty("user.home")).resolve(".bdeploy"));
    }

    private Path getDataFile() {
        return home.resolve(".bdeploy_login");
    }

    private void write(LocalLoginData data) {
        try {
            if (!Files.isRegularFile(getDataFile())) {
                Files.createDirectories(getDataFile().getParent());
                Files.createFile(getDataFile());
            }

            try (OutputStream os = Files.newOutputStream(getDataFile())) {
                JacksonHelper.createObjectMapper(MapperType.JSON).writeValue(os, data);
            }
        } catch (IOException e) {
            log.error("Cannot write local login data to " + getDataFile(), e);
        }
    }

    public LocalLoginData read() {
        try {
            if (!Files.isRegularFile(getDataFile())) {
                return new LocalLoginData();
            }

            try (InputStream is = Files.newInputStream(getDataFile())) {
                return JacksonHelper.createObjectMapper(MapperType.JSON).readValue(is, LocalLoginData.class);
            }
        } catch (IOException e) {
            log.error("Cannot read local login data from " + getDataFile(), e);
            return new LocalLoginData();
        }
    }

    public void login(String serverName, String url, String user, String password) {
        LocalLoginData data = read();

        if (data.servers.containsKey(serverName)) {
            throw new IllegalStateException("Server with name " + serverName + " already exists.");
        }

        AuthDto auth = new AuthDto();
        auth.user = user;
        auth.password = password;

        // Cannot use JerseyClientFactory as we do not have a token yet... need to trust all servers here.
        ClientBuilder builder = ClientBuilder.newBuilder().hostnameVerifier((h, s) -> true).sslContext(createTrustAllContext());
        Response result = builder.build().target(url).path("/auth/packed").request()
                .post(Entity.entity(auth, MediaType.APPLICATION_JSON));

        if (result.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
            throw new IllegalStateException("Failed to login: " + result.getStatusInfo().getReasonPhrase());
        } else {
            LocalLoginServer s = new LocalLoginServer();
            s.url = url;
            s.token = result.readEntity(String.class);

            data.servers.put(serverName, s);
            data.current = serverName;

            write(data);
        }
    }

    public void remove(String serverName) {
        LocalLoginData data = read();
        data.servers.remove(serverName);
        if (data.current.equals(serverName)) {
            data.current = null;
        }
        write(data);
    }

    public void setCurrent(String serverName) {
        LocalLoginData data = read();

        if (!data.servers.containsKey(serverName)) {
            throw new IllegalStateException("Unknown server: " + serverName);
        }

        data.current = serverName;
        write(data);
    }

    public String getCurrent() {
        return read().current;
    }

    public RemoteService getCurrentService() {
        LocalLoginData data = read();

        if (data.current == null || !data.servers.containsKey(data.current)) {
            return null;
        }

        LocalLoginServer server = data.servers.get(data.current);
        return new RemoteService(UriBuilder.fromUri(server.url).build(), server.token);
    }

    private SSLContext createTrustAllContext() {
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");

            sslcontext.init(null, new TrustManager[] { new X509TrustManager() {

                @Override
                public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            } }, new java.security.SecureRandom());
            return sslcontext;
        } catch (GeneralSecurityException e) {
            log.warn("Cannot create SSL context", e);
            return null;
        }
    }

    @SuppressWarnings("unused")
    private static final class AuthDto {

        public String user;
        public String password;
    }

}
