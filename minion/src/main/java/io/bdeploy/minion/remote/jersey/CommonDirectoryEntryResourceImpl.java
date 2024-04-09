package io.bdeploy.minion.remote.jersey;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;

import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.remote.CommonDirectoryEntryResource;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.minion.MinionRoot;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.StreamingOutput;

public class CommonDirectoryEntryResourceImpl implements CommonDirectoryEntryResource {

    private static final Logger log = LoggerFactory.getLogger(CommonDirectoryEntryResourceImpl.class);

    @Inject
    private MinionRoot root;

    @Override
    public EntryChunk getEntryContent(RemoteDirectoryEntry entry, long offset, long limit) {
        // determine file first...
        Path actual = getEntryPath(root, entry);

        if (limit == 0) {
            limit = Long.MAX_VALUE;
        }

        File file = actual.toFile();
        long currentSize = file.length();
        if (currentSize < offset) {
            // file has been reset.
            return EntryChunk.ROLLOVER_CHUNK;
        } else if (currentSize > offset) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(offset);
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    int b;
                    long c = 0;
                    while ((b = raf.read()) != -1) {
                        if (c++ >= limit) {
                            break;
                        }

                        baos.write(b);
                    }

                    return new EntryChunk(baos.toByteArray(), offset, raf.getFilePointer());
                }
            } catch (IOException e) {
                throw new WebApplicationException("Cannot read chunk of " + actual, e);
            }
        }

        return null; // offset == size...
    }

    static Path getEntryPath(MinionRoot root, RemoteDirectoryEntry entry) {
        Path rootDir;
        if (entry.root != null) {
            DeploymentPathProvider dpp = new DeploymentPathProvider(root.getDeploymentDir(), root.getLogDataDir(), entry.id,
                    entry.tag);
            rootDir = dpp.get(entry.root).toAbsolutePath();
        } else {
            rootDir = root.getRootDir();
        }

        Path actual = rootDir.resolve(entry.path).normalize();

        if (!actual.startsWith(rootDir)) {
            Path externalDataFilesDir = root.getLogDataDir();
            if (externalDataFilesDir == null || !actual.startsWith(externalDataFilesDir)) {
                throw new WebApplicationException("Trying to escape the scope of the minion.", Status.BAD_REQUEST);
            }
        }

        if (!Files.exists(actual)) {
            throw new WebApplicationException("Cannot find " + actual, Status.NOT_FOUND);
        }
        return actual;
    }

    @Override
    public Response getEntryStream(RemoteDirectoryEntry entry) {
        Path actual = getEntryPath(root, entry);
        String mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            ContentInfo ci = ContentInfoUtil.findExtensionMatch(actual.getFileName().toString());
            ci = PathHelper.getContentInfo(actual, ci);

            // dynamic return mime type
            if (ci != null && ci.getMimeType() != null) {
                mediaType = ci.getMimeType();
            }
        } catch (IOException e) {
            log.warn("Cannot determine mime type of {}", actual, e);
        }

        // Build a response with the stream
        ResponseBuilder responseBuilder = Response.ok(new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException {
                try (InputStream is = Files.newInputStream(actual)) {
                    is.transferTo(output);
                } catch (IOException ioe) {
                    if (log.isDebugEnabled()) {
                        log.debug("Could not fully write output", ioe);
                    } else {
                        log.warn("Could not fully write output: {}", ioe.toString());
                    }
                }
            }
        }, mediaType);

        // Load and attach metadata to give the file a nice name
        try {
            long size = Files.size(actual);
            ContentDisposition contentDisposition = ContentDisposition.type("attachement").size(size)
                    .fileName(actual.getFileName().toString()).build();
            responseBuilder.header("Content-Disposition", contentDisposition);
            responseBuilder.header("Content-Length", size);
            return responseBuilder.build();
        } catch (IOException e) {
            throw new WebApplicationException("Cannot provide download for entry", e);
        }
    }

    @Override
    public Response getEntriesZipStream(List<RemoteDirectoryEntry> entries) {
        // Build a response with the stream
        var responseBuilder = Response.ok(new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException {
                try (var zos = new ZipOutputStream(output)) {
                    for (var entry : entries) {
                        var path = getEntryPath(root, entry); // will throw in case of error
                        var ze = new ZipEntry(entry.path); // relative path name.

                        ze.setTime(Files.getLastModifiedTime(path).toMillis());
                        zos.putNextEntry(ze);

                        try (var is = Files.newInputStream(path)) {
                            is.transferTo(zos);
                        }

                        zos.closeEntry();
                    }
                } catch (IOException ioe) {
                    if (log.isDebugEnabled()) {
                        log.debug("Could not fully write output", ioe);
                    } else {
                        log.warn("Could not fully write output: {}", ioe.toString());
                    }
                }
            }
        });

        // Load and attach metadata to give the file a nice name
        var contentDisposition = ContentDisposition.type("attachement").fileName("Files.zip").build();
        responseBuilder.header("Content-Disposition", contentDisposition);
        return responseBuilder.build();
    }

}
