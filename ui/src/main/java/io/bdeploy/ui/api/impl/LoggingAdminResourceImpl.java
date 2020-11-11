package io.bdeploy.ui.api.impl;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

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

}
