package io.bdeploy.minion.remote.jersey;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.remote.ProxiedRequestWrapper;
import io.bdeploy.interfaces.remote.ProxiedResponseWrapper;
import io.bdeploy.interfaces.remote.SlaveProcessResource;
import io.bdeploy.interfaces.remote.SlaveProxyResource;
import io.bdeploy.interfaces.variables.ApplicationParameterValueResolver;
import io.bdeploy.interfaces.variables.ApplicationVariableResolver;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.minion.MinionRoot;

public class SlaveProxyResourceImpl implements SlaveProxyResource {

    private static final Logger log = LoggerFactory.getLogger(SlaveProxyResourceImpl.class);

    @Inject
    private MinionRoot root;

    @Context
    private ResourceContext rc;

    @Override
    public ProxiedResponseWrapper forward(ProxiedRequestWrapper wrapper) {
        InstanceNodeManifest inm = findInstanceNodeManifest(wrapper.instanceId);
        if (inm == null) {
            throw new WebApplicationException("Cannot find instance " + wrapper.instanceId, Status.NOT_FOUND);
        }

        SlaveProcessResource spr = rc.initResource(new SlaveProcessResourceImpl());
        InstanceNodeStatusDto ins = spr.getStatus(wrapper.instanceId);
        ProcessStatusDto ps = ins.getStatus(wrapper.applicationId);

        if (!ps.processState.isRunning()) {
            throw new WebApplicationException(
                    "Process with ID " + wrapper.applicationId + " is not running for instance " + wrapper.instanceId,
                    Status.PRECONDITION_FAILED);
        }

        try {
            byte[] body = wrapper.base64body == null ? null : Base64.decodeBase64(wrapper.base64body);
            WebTarget target = initClient(wrapper.endpoint).target(initUri(wrapper, inm));

            for (Map.Entry<String, List<String>> entry : wrapper.queryParameters.entrySet()) {
                target = target.queryParam(entry.getKey(), entry.getValue().toArray());
            }

            Invocation.Builder request = target.request();
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
        } catch (ProcessingException pex) {
            Throwable cause = pex.getCause();
            if (cause instanceof SSLException) {
                log.warn("SSL Exception: " + cause.getMessage());
                log.info("Falling back to plain HTTP for endpoint " + wrapper.endpoint.id);

                wrapper.endpoint.secure = false;
                return forward(wrapper);
            }

            throw pex;
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

    private String initUri(ProxiedRequestWrapper wrapper, InstanceNodeManifest inm) {
        ApplicationConfiguration app = inm.getConfiguration().applications.stream()
                .filter(a -> a.uid.equals(wrapper.applicationId)).findFirst().orElseThrow();

        CompositeResolver list = new CompositeResolver();
        list.add(new ApplicationVariableResolver(app));
        list.add(new ApplicationParameterValueResolver(app.uid, inm.getConfiguration()));

        String port = TemplateHelper.process(wrapper.endpoint.port, list);
        String path = TemplateHelper.process(wrapper.endpoint.path, list);

        return (wrapper.endpoint.secure ? "https://" : "http://") + "localhost:" + port + (path.startsWith("/") ? "" : "/")
                + path;
    }

    private Client initClient(HttpEndpoint endpoint) throws GeneralSecurityException {
        ClientBuilder client = ClientBuilder.newBuilder();

        if (endpoint.secure && endpoint.trustAll) {
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

            client.sslContext(sslcontext).hostnameVerifier((s1, s2) -> true);
        }

        return client.build();
    }

    private InstanceNodeManifest findInstanceNodeManifest(String instanceId) {
        SortedSet<Key> manifests = InstanceNodeManifest.scan(root.getHive());
        for (Key key : manifests) {
            InstanceNodeManifest mf = InstanceNodeManifest.of(root.getHive(), key);
            if (!mf.getUUID().equals(instanceId)) {
                continue;
            }
            return mf;
        }
        return null;
    }

}
