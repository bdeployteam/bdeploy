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
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import io.bdeploy.common.util.PathHelper;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

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

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return Path.class.isAssignableFrom(type);
    }

    @Override
    public void writeTo(Path t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException {
        long size = Files.size(t);
        boolean delete = false;

        if (info != null) {
            // on the server :)
            Method m = info.getResourceMethod();
            if (m != null && m.getAnnotation(DeleteAfterWrite.class) != null) {
                delete = true;
            }
        }

        httpHeaders.addFirst(JerseyPathReader.PATH_SIZE_HDR, size);
        OpenOption[] delOpt = delete ? new OpenOption[] { StandardOpenOption.DELETE_ON_CLOSE } : new OpenOption[0];

        try (InputStream in = Files.newInputStream(t, delOpt)) {
            JerseyStreamingHelper.streamWithProgress(in, entityStream, size);
        }

        if (delete) {
            PathHelper.deleteIfExistsRetry(t);
        }
    }

}
