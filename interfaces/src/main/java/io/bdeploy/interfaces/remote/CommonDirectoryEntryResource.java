package io.bdeploy.interfaces.remote;

import java.io.InputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;

@Path("/directories")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface CommonDirectoryEntryResource {

    /**
     * @param entry the {@link RemoteDirectoryEntry} to fetch content from.
     * @param offset the offset into the underlying file.
     * @param limit maximum bytes to read. 0 means no limit.
     * @return a chunk of the given entry, starting at offset until the <b>current</b> end of the file.
     */
    @POST
    @Path("/content")
    public EntryChunk getEntryContent(RemoteDirectoryEntry entry, @QueryParam("o") long offset, @QueryParam("l") long limit);

    /**
     * @param entry the entry to stream. The stream will include the complete content of the file.
     * @return an {@link InputStream} that can be used to stream the file.
     */
    @POST
    @Path("/streamContent")
    @Produces("*/*")
    public Response getEntryStream(RemoteDirectoryEntry entry);

}
