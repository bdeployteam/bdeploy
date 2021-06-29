package io.bdeploy.ui.api.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.apache.commons.codec.binary.Base64;

import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.RemoteEntryStreamRequestService;
import io.bdeploy.ui.RemoteEntryStreamRequestService.EntryRequest;
import io.bdeploy.ui.api.LoggingAdminResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.StringEntryChunkDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

public class LoggingAdminResourceImpl implements LoggingAdminResource {

    @Inject
    private Minion minion;

    @Context
    private SecurityContext context;

    @Inject
    RemoteEntryStreamRequestService resrs;

    @Override
    public List<RemoteDirectory> getLogDirectories() {
        CommonRootResource root = ResourceProvider.getResource(minion.getSelf(), CommonRootResource.class, context);
        return root.getLogDirectories();
    }

    @Override
    public StringEntryChunkDto getLogContentChunk(String minionName, RemoteDirectoryEntry entry, long offset, long limit) {
        CommonRootResource root = ResourceProvider.getResource(minion.getSelf(), CommonRootResource.class, context);

        EntryChunk chunk = root.getLogContent(minionName, entry, offset, limit);
        if (chunk == null) {
            return null;
        }
        return new StringEntryChunkDto(chunk);
    }

    @Override
    public String getLogContentStreamRequest(String minionName, RemoteDirectoryEntry entry) {
        return resrs.createRequest(new EntryRequest(minionName, null, entry));
    }

    @Override
    public Response getLogContentStream(String token) {
        EntryRequest rq = resrs.consumeRequestToken(token);
        CommonRootResource root = ResourceProvider.getResource(minion.getSelf(), CommonRootResource.class, context);
        return root.getLogStream(rq.minion, rq.entry);
    }

    @Override
    public String getLogConfig() {
        CommonRootResource root = ResourceProvider.getResource(minion.getSelf(), CommonRootResource.class, context);
        Path temp = root.getLoggerConfig();
        try (InputStream is = Files.newInputStream(temp); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            is.transferTo(baos);
            return Base64.encodeBase64String(baos.toByteArray());
        } catch (IOException e) {
            throw new WebApplicationException("Cannot read logging configuration", e);
        } finally {
            PathHelper.deleteRecursive(temp);
        }
    }

    @Override
    public void setLogConfig(String config) {
        Path temp = null;
        try {
            temp = Files.createTempFile(minion.getTempDir(), "log4j2-", ".xml");
            Files.write(temp, Base64.decodeBase64(config), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.SYNC);
            CommonRootResource root = ResourceProvider.getResource(minion.getSelf(), CommonRootResource.class, context);
            root.setLoggerConfig(temp);
        } catch (IOException e) {
            throw new WebApplicationException("Cannot store new logger config", e);
        } finally {
            if (temp != null) {
                PathHelper.deleteRecursive(temp);
            }
        }
    }

}
