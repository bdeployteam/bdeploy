package io.bdeploy.ui.api;

import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateReferenceDescriptor;
import io.bdeploy.ui.dto.InstanceTemplateReferenceResultDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface InstanceTemplateResource {

    /**
     * Apply a given instance template.
     * <p>
     * The template reference needs to provide all group mappings and variables, as user input is not possible.
     */
    @POST
    @Path("/apply")
    public InstanceTemplateReferenceResultDto createFromTemplate(InstanceTemplateReferenceDescriptor instance,
            @QueryParam("purpose") InstancePurpose purpose, @QueryParam("server") String server,
            @QueryParam("system") String system);

    /**
     * Apply a given instance template to an existing instance.
     * <p>
     * The template reference needs to provide all group mappings and variables, as user input is not possible.
     */
    @POST
    @Path("/update")
    public InstanceTemplateReferenceResultDto updateWithTemplate(InstanceTemplateReferenceDescriptor instanceTemplate,
            @QueryParam("server") String server, @QueryParam("uuid") String uuid);
}
