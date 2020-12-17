package io.bdeploy.common.security;

import java.io.Serializable;
import java.net.URI;
import java.security.KeyStore;

import javax.annotation.Generated;
import jakarta.ws.rs.core.UriBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents data about a single known minion.
 */
public class RemoteService implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The URI under which the referenced minion is reachable.
     */
    private final URI uri;

    /**
     * Token - used only for storage and initialization of {@link KeyStoreProvider}
     */
    private final String authPack;

    /**
     * Caches a {@link KeyStore} for the given this {@link RemoteService}' token.
     */
    private transient KeyStoreProvider provider;

    /**
     * Creates a {@link RemoteService} which references a local directory as URI.
     *
     * @param uri a local URI (file, zip, ...) to fake a remote service for
     */
    public RemoteService(URI uri) {
        this.uri = uri;
        this.authPack = null;
        this.provider = null;
    }

    /**
     * @param uri the {@link URI} to the service
     * @param authPack the authentication pack (token and certificate) used to access
     *            the {@link RemoteService}.
     */
    @JsonCreator
    public RemoteService(@JsonProperty("uri") URI uri, @JsonProperty("authPack") String authPack) {
        this.uri = uri;
        this.authPack = authPack;
    }

    /**
     * Creates a new {@link RemoteService} with the given {@link KeyStoreProvider}.
     */
    public RemoteService(URI uri, KeyStoreProvider provider) {
        this.uri = uri;
        this.authPack = null;
        this.provider = provider;
    }

    /**
     * @return the provider for key material.
     */
    public KeyStoreProvider getKeyStore() {
        if (provider == null && authPack != null) {
            provider = new InMemoryKeyStore(authPack);
        }
        return provider;
    }

    /**
     * @return the service URI
     */
    public URI getUri() {
        return uri;
    }

    /**
     * @return the security token
     */
    public String getAuthPack() {
        return authPack;
    }

    /**
     * @return a URI which can be used to connect to WebSockets on the given remote, following the standard pattern
     */
    public URI getWebSocketUri(String path) {
        return UriBuilder.fromUri(getUri()).scheme("wss").replacePath("/ws").path(path).build();
    }

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((authPack == null) ? 0 : authPack.hashCode());
        result = prime * result + ((uri == null) ? 0 : uri.hashCode());
        return result;
    }

    @Generated("Eclipse")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        RemoteService other = (RemoteService) obj;
        if (authPack == null) {
            if (other.authPack != null) {
                return false;
            }
        } else if (!authPack.equals(other.authPack)) {
            return false;
        }
        if (uri == null) {
            if (other.uri != null) {
                return false;
            }
        } else if (!uri.equals(other.uri)) {
            return false;
        }
        return true;
    }

}