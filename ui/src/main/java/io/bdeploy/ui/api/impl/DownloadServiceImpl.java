package io.bdeploy.ui.api.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.ContentDisposition.ContentDispositionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.ui.api.DownloadService;
import io.bdeploy.ui.api.Minion;

/**
 * A generic service serving files that have been prepared by a (secure call). The following steps are required in order to
 * use this service:
 * <ol>
 * <li>Acquire a new token</li>
 * <li>Request the storage location to use</li>
 * <li>Register the created file</li>
 * <li>File can now be downloaded using the token</li>
 * </ol>
 */
@Singleton
public class DownloadServiceImpl implements DownloadService {

    private static final Logger log = LoggerFactory.getLogger(InstanceResourceImpl.class);

    private static final String ATTACHMENT_DISPOSITION = "attachment";

    @Inject
    private Minion minion;

    @Inject
    private DownloadTokenCache tokenCache;

    @Override
    public Response download(String token) {
        // Check if we still know this file
        String fileName = tokenCache.get(token);
        if (fileName == null) {
            throw new WebApplicationException("Token to download client installer is not valid any more.", Status.BAD_REQUEST);
        }

        // File must be downloaded within a given timeout
        Path targetFile = minion.getDownloadDir().resolve(token);
        File file = targetFile.toFile();
        if (!file.isFile()) {
            throw new WebApplicationException("Token to download client installer is not valid any more.", Status.BAD_REQUEST);
        }
        long lastModified = file.lastModified();
        long validUntil = lastModified + TimeUnit.MINUTES.toMillis(5);
        if (System.currentTimeMillis() > validUntil) {
            throw new WebApplicationException("Token to download client installer is not valid any more.", Status.BAD_REQUEST);
        }

        // Build a response with the stream
        ResponseBuilder responeBuilder = Response.ok(new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException {
                try (InputStream is = Files.newInputStream(targetFile)) {
                    is.transferTo(output);
                } catch (IOException ioe) {
                    if (log.isDebugEnabled()) {
                        log.debug("Could not fully write output", ioe);
                    } else {
                        log.warn("Could not fully write output: {}", ioe);
                    }
                } finally {
                    // Cleanup token and file
                    tokenCache.remove(token);
                    PathHelper.deleteRecursive(targetFile);
                }
            }
        }, MediaType.APPLICATION_OCTET_STREAM);

        // Serve file to the client
        ContentDispositionBuilder<?, ?> builder = ContentDisposition.type(ATTACHMENT_DISPOSITION);
        builder.size(file.length()).fileName(fileName);
        responeBuilder.header(HttpHeaders.CONTENT_DISPOSITION, builder.build());
        responeBuilder.header(HttpHeaders.CONTENT_LENGTH, file.length());
        return responeBuilder.build();
    }

    /**
     * Registers a previously created file for downloading.
     *
     * @param token the token to access the file
     * @param fileName the real name of the file that should be used when serving this file
     */
    public void registerForDownload(String token, String fileName) {
        // File must be present so that it can be registered
        File file = minion.getDownloadDir().resolve(token).toFile();
        if (!file.exists() || file.length() == 0) {
            throw new WebApplicationException("File does not exist or is empty");
        }

        // Store the file using the desired name
        tokenCache.add(token, fileName);
    }

    /**
     * Requests a new token to store a file in the download directory.
     */
    public String createNewToken() {
        return UuidHelper.randomId();
    }

    /**
     * Returns the path where to store the file that should be served
     */
    public Path getStoragePath(String token) {
        return minion.getDownloadDir().resolve(token);
    }

    /**
     * Creates a new response to serve the given bytes.
     *
     * @param data
     *            the data to serve
     * @param name
     *            the name shown to the user when downloading
     */
    public Response serveBytes(byte[] data, String name) {
        ResponseBuilder responeBuilder = Response.ok(new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException {
                try (InputStream is = new ByteArrayInputStream(data)) {
                    is.transferTo(output);
                }
            }
        }, MediaType.APPLICATION_OCTET_STREAM);
        ContentDispositionBuilder<?, ?> builder = ContentDisposition.type(ATTACHMENT_DISPOSITION);
        builder.size(data.length).fileName(name);
        responeBuilder.header(HttpHeaders.CONTENT_DISPOSITION, builder.build());
        responeBuilder.header(HttpHeaders.CONTENT_LENGTH, data.length);
        return responeBuilder.build();
    }

    /**
     * Creates a new response to serve the given file.
     *
     * @param file
     *            the file to serve
     * @param name
     *            the name shown to the user when downloading
     */
    public Response serveFile(Path file, String name) {
        ResponseBuilder responeBuilder = Response.ok(new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException {
                try (InputStream is = Files.newInputStream(file)) {
                    is.transferTo(output);
                } finally {
                    Files.deleteIfExists(file);
                }
            }
        }, MediaType.APPLICATION_OCTET_STREAM);
        ContentDispositionBuilder<?, ?> builder = ContentDisposition.type(ATTACHMENT_DISPOSITION);
        builder.size(file.toFile().length()).fileName(name);
        responeBuilder.header(HttpHeaders.CONTENT_DISPOSITION, builder.build());
        responeBuilder.header(HttpHeaders.CONTENT_LENGTH, file.toFile().length());
        return responeBuilder.build();
    }

}
