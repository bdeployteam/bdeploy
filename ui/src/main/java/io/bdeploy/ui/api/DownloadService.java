package io.bdeploy.ui.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;

@Path("/download")
public interface DownloadService {

    /**
     * Serves a file that has been prepared for downloading by another (secure) call. Anybody having
     * the correct token can download the file.
     *
     * @param token the token identifying the file to download
     * @return the stream to download the prepared file.
     */
    @GET
    @Unsecured
    @Path("/file/{token}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@PathParam("token") String token);

}
