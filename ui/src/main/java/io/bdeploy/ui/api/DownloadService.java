package io.bdeploy.ui.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
