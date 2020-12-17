package io.bdeploy.jersey;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.Providers;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.jersey.JerseyStreamingHelper.StreamDirection;

/**
 * Allows deserializing {@link Path} objects (parameter or return value). The
 * actual file content is streamed to a local file and a path to the temporary
 * file is injected.
 * <p>
 * The recipient of the {@link Path} is responsible for cleaning up temporary
 * files.
 */
@Provider
@Consumes(MediaType.APPLICATION_OCTET_STREAM)
public class JerseyPathReader implements MessageBodyReader<Path> {

    static final String PATH_SIZE_HDR = "X-File-Size"; // don't use Content-Length = restricted.

    @Context
    private Providers providers;

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return Path.class.isAssignableFrom(type);
    }

    @Override
    public Path readFrom(Class<Path> type, Type genericType, Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException {
        String cl = httpHeaders.getFirst(PATH_SIZE_HDR);
        long length = -1l;
        try {
            length = Long.parseLong(cl);
        } catch (NumberFormatException e) {
            // no length present or invalid format.
        }

        Path tmpFile = Files.createTempFile("dl-", ".bin");
        try (OutputStream out = Files.newOutputStream(tmpFile)) {
            JerseyStreamingHelper.streamWithProgress(providers.getContextResolver(ActivityReporter.class, MediaType.WILDCARD_TYPE)
                    .getContext(ActivityReporter.class), StreamDirection.READ, entityStream, out, length);
        }
        return tmpFile;
    }

}
