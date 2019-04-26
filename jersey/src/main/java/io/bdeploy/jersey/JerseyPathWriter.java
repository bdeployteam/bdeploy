package io.bdeploy.jersey;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.Providers;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.jersey.JerseyStreamingHelper.StreamDirection;

/**
 * Allows serializing existing files by returning/accepting {@link Path}
 * objects. Relies on the counterpart {@link JerseyPathReader} to deserialize on
 * the othe end of the cable into a temporary file.
 */
@Provider
@Produces(MediaType.APPLICATION_OCTET_STREAM)
public class JerseyPathWriter implements MessageBodyWriter<Path> {

    /**
     * Specifies that the file written to the remote must be deleted after writing.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface DeleteAfterWrite {
    }

    @Context
    private ResourceInfo info;

    @Context
    private Providers providers;

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return Path.class.isAssignableFrom(type);
    }

    @Override
    public void writeTo(Path t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
        long size = Files.size(t);

        httpHeaders.addFirst("Content-Length", size);

        try (InputStream in = Files.newInputStream(t)) {
            JerseyStreamingHelper.streamWithProgress(providers.getContextResolver(ActivityReporter.class, MediaType.WILDCARD_TYPE)
                    .getContext(ActivityReporter.class), StreamDirection.WRITE, in, entityStream, size);
        }

        if (info != null) {
            // on the server :)
            Method m = info.getResourceMethod();
            if (m != null && m.getAnnotation(DeleteAfterWrite.class) != null) {
                Files.deleteIfExists(t);
            }
        }
    }

}
