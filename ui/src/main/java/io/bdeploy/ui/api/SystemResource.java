package io.bdeploy.ui.api;

import java.io.InputStream;
import java.util.List;

import org.glassfish.jersey.media.multipart.FormDataParam;
import org.jvnet.hk2.annotations.Optional;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.ui.dto.SystemConfigurationDto;
import io.bdeploy.ui.dto.SystemTemplateDto;
import io.bdeploy.ui.dto.SystemTemplateRequestDto;
import io.bdeploy.ui.dto.SystemTemplateResultDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface SystemResource {

    @GET
    public List<SystemConfigurationDto> list();

    @POST
    public Manifest.Key update(SystemConfigurationDto dto);

    @DELETE
    @Path("{system}")
    public void delete(@PathParam("system") String system);

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public SystemTemplateDto loadTemplate(@FormDataParam("file") InputStream inputStream,
            @Optional @QueryParam("server") String server);

    @POST
    @Path("apply")
    public SystemTemplateResultDto applyTemplate(SystemTemplateRequestDto request);

}
