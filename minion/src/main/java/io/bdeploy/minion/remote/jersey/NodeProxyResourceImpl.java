package io.bdeploy.minion.remote.jersey;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.function.UnaryOperator;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.codec.binary.Base64;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint.HttpAuthenticationType;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.remote.NodeProcessResource;
import io.bdeploy.interfaces.remote.NodeProxyResource;
import io.bdeploy.interfaces.remote.ProxiedRequestWrapper;
import io.bdeploy.interfaces.remote.ProxiedResponseWrapper;
import io.bdeploy.interfaces.variables.ApplicationParameterValueResolver;
import io.bdeploy.interfaces.variables.ApplicationVariableResolver;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathResolver;
import io.bdeploy.jersey.TrustAllServersTrustManager;
import io.bdeploy.minion.MinionRoot;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

public class NodeProxyResourceImpl implements NodeProxyResource {

    private static final Logger log = LoggerFactory.getLogger(NodeProxyResourceImpl.class);

    @Inject
    private MinionRoot root;

    @Context
    private ResourceContext rc;

    @Override
    public ProxiedResponseWrapper forward(ProxiedRequestWrapper wrapper) {
        NodeProcessResource spr = rc.initResource(new NodeProcessResourceImpl());
        InstanceNodeStatusDto ins = spr.getStatus(wrapper.instanceId);
        ProcessStatusDto ps = ins.getStatus(wrapper.applicationId);

        if (!ps.processState.isRunning()) {
            throw new WebApplicationException(
                    "Process with ID " + wrapper.applicationId + " is not running for instance " + wrapper.instanceId,
                    Status.PRECONDITION_FAILED);
        }

        InstanceNodeManifest inm = findInstanceNodeManifest(wrapper.instanceId, ps.instanceTag);
        if (inm == null) {
            throw new WebApplicationException("Cannot find instance " + wrapper.instanceId, Status.NOT_FOUND);
        }

        HttpEndpoint processedEndpoint = processEndpoint(wrapper, inm);

        try {
            byte[] body = wrapper.base64body == null ? null : Base64.decodeBase64(wrapper.base64body);
            WebTarget target = initClient(processedEndpoint).target(initUri(processedEndpoint));

            for (Map.Entry<String, List<String>> entry : wrapper.queryParameters.entrySet()) {
                target = target.queryParam(entry.getKey(), entry.getValue().toArray());
            }

            Invocation.Builder request = target.request();

            // Always replace the "host" header with "localhost". the request is *always* made on the local
            // machine. Avoid forwarding the original host (e.g. the hostname of the original BDeploy server).
            // Otherwise a potential SNI check will fail on the target due to hostname mismatch with certificates.
            if (wrapper.headers.containsKey("host")) {
                wrapper.headers.put("host", Collections.singletonList("localhost"));
            }

            for (Map.Entry<String, List<String>> entry : wrapper.headers.entrySet()) {
                for (String value : entry.getValue()) {
                    request.header(entry.getKey(), value);
                }
            }

            if (body != null) {
                return wrap(request.build(wrapper.method, Entity.entity(body, wrapper.bodyType)).invoke());
            } else {
                return wrap(request.build(wrapper.method).invoke());
            }
        } catch (Exception e) {
            throw new WebApplicationException("Failed to call endpoint " + wrapper.endpoint.id + " on target application "
                    + wrapper.applicationId + " for instance " + wrapper.instanceId, e);
        }
    }

    private ProxiedResponseWrapper wrap(Response resp) {
        ProxiedResponseWrapper wrapper = new ProxiedResponseWrapper();

        wrapper.headers = resp.getStringHeaders();
        wrapper.responseCode = resp.getStatus();
        wrapper.responseReason = resp.getStatusInfo().getReasonPhrase();

        if (resp.hasEntity()) {
            try (InputStream is = resp.readEntity(InputStream.class)) {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    StreamHelper.copy(is, baos);
                    wrapper.base64body = Base64.encodeBase64String(baos.toByteArray());
                }
            } catch (IOException e) {
                log.warn("Cannot wrap response", e);
            }
        }

        return wrapper;
    }

    private HttpEndpoint processEndpoint(ProxiedRequestWrapper wrapper, InstanceNodeManifest inm) {
        HttpEndpoint processed = new HttpEndpoint();
        ApplicationConfiguration app = inm.getConfiguration().applications.stream()
                .filter(a -> a.uid.equals(wrapper.applicationId)).findFirst().orElseThrow();

        CompositeResolver list = new CompositeResolver();
        list.add(new DeploymentPathResolver(
                new DeploymentPathProvider(root.getDeploymentDir().resolve(inm.getUUID()), inm.getKey().getTag())));
        list.add(new ApplicationVariableResolver(app));
        list.add(new ApplicationParameterValueResolver(app.uid, inm.getConfiguration()));

        UnaryOperator<String> p = s -> TemplateHelper.process(s, list);

        processed.id = wrapper.endpoint.id;
        processed.path = wrapper.endpoint.path;
        processed.port = p.apply(wrapper.endpoint.port);
        processed.secure = wrapper.endpoint.secure;
        processed.trustAll = wrapper.endpoint.trustAll;
        processed.trustStore = p.apply(wrapper.endpoint.trustStore);
        processed.trustStorePass = p.apply(wrapper.endpoint.trustStorePass);
        processed.authType = wrapper.endpoint.authType;
        processed.authUser = p.apply(wrapper.endpoint.authUser);
        processed.authPass = p.apply(wrapper.endpoint.authPass);

        return processed;
    }

    private String initUri(HttpEndpoint endpoint) {
        return (endpoint.secure ? "https://" : "http://") + "localhost:" + endpoint.port
                + (endpoint.path.startsWith("/") ? "" : "/") + endpoint.path;
    }

    private Client initClient(HttpEndpoint endpoint) throws GeneralSecurityException {
        ClientBuilder client = ClientBuilder.newBuilder();

        if (endpoint.secure && endpoint.trustAll) {
            SSLContext sslcontext = SSLContext.getInstance("TLS");

            sslcontext.init(null, new TrustManager[] { new TrustAllServersTrustManager() }, new java.security.SecureRandom());

            client.sslContext(sslcontext).hostnameVerifier((s1, s2) -> true);
        } else if (endpoint.secure && endpoint.trustStore != null && !endpoint.trustStore.isEmpty()) {
            Path ksPath = Paths.get(endpoint.trustStore);

            char[] pp = null;
            if (endpoint.trustStorePass != null && !endpoint.trustStorePass.isEmpty()) {
                pp = endpoint.trustStorePass.toCharArray();
            }

            KeyStore ks;
            try {
                ks = SecurityHelper.getInstance().loadPublicKeyStore(ksPath, pp);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ks);

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), null);
                client.sslContext(sslContext);
            } catch (GeneralSecurityException | IOException e) {
                log.error("Cannot load configures trust store from {}", ksPath, e);
            }
        }

        if (endpoint.authType == HttpAuthenticationType.BASIC) {
            client.register(HttpAuthenticationFeature.basic(endpoint.authUser, endpoint.authPass));
        } else if (endpoint.authType == HttpAuthenticationType.DIGEST) {
            client.register(HttpAuthenticationFeature.digest(endpoint.authUser, endpoint.authPass));
        }

        return client.build();
    }

    private InstanceNodeManifest findInstanceNodeManifest(String instanceId, String tag) {
        SortedSet<Key> manifests = InstanceNodeManifest.scan(root.getHive());
        for (Key key : manifests) {
            if (!key.getTag().equals(tag)) {
                continue;
            }
            InstanceNodeManifest mf = InstanceNodeManifest.of(root.getHive(), key);
            if (!mf.getUUID().equals(instanceId)) {
                continue;
            }
            return mf;
        }
        return null;
    }

}
