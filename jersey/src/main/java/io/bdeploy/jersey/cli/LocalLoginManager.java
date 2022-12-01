package io.bdeploy.jersey.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.jersey.TrustAllServersTrustManager;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;
import jakarta.ws.rs.core.UriBuilder;

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

            try (OutputStream os = Files.newOutputStream(getDataFile(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC)) {
                JacksonHelper.getDefaultJsonObjectMapper().writeValue(os, data);
            }
        } catch (IOException e) {
            log.error("Cannot write local login data to {}", getDataFile(), e);
        }
    }

    public LocalLoginData read() {
        try {
            if (!Files.isRegularFile(getDataFile())) {
                return new LocalLoginData();
            }

            try (InputStream is = Files.newInputStream(getDataFile())) {
                return JacksonHelper.getDefaultJsonObjectMapper().readValue(is, LocalLoginData.class);
            }
        } catch (IOException e) {
            log.error("Cannot read local login data from {}", getDataFile(), e);
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
            s.user = user;

            data.servers.put(serverName, s);
            data.current = serverName;

            write(data);
        }
    }

    public void remove(String serverName) {
        LocalLoginData data = read();
        data.servers.remove(serverName);
        if (serverName.equals(data.current)) {
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
        if (data.current == null) {
            return null;
        }
        return getNamedService(data.current);
    }

    public RemoteService getNamedService(String name) {
        LocalLoginData data = read();

        if (!data.servers.containsKey(name)) {
            return null;
        }

        LocalLoginServer server = data.servers.get(name);
        return new RemoteService(UriBuilder.fromUri(server.url).build(), server.token);

    }

    private SSLContext createTrustAllContext() {
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");

            sslcontext.init(null, new TrustManager[] { new TrustAllServersTrustManager() }, new java.security.SecureRandom());
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
