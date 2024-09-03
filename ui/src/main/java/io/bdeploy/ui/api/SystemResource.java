package io.bdeploy.ui.api;

import java.util.List;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.jvnet.hk2.annotations.Optional;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
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
    @RequiredPermission(permission = Permission.WRITE)
    public Manifest.Key update(SystemConfigurationDto dto);

    @DELETE
    @Path("{system}")
    @RequiredPermission(permission = Permission.WRITE)
    public void delete(@PathParam("system") String system);

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public SystemTemplateDto loadTemplate(FormDataMultiPart fdmp, @Optional @QueryParam("server") String server);

    @POST
    @Path("import-missing-products")
    public SystemTemplateDto importMissingProducts(SystemTemplateDto template);

    @POST
    @Path("apply")
    @RequiredPermission(permission = Permission.WRITE)
    public SystemTemplateResultDto applyTemplate(SystemTemplateRequestDto request);

}
