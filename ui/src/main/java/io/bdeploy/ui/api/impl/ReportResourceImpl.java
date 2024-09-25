package io.bdeploy.ui.api.impl;

import java.util.List;

import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.report.ProductsInUseReportDescriptor;
import io.bdeploy.interfaces.report.ReportDescriptor;
import io.bdeploy.interfaces.report.ReportRequestDto;
import io.bdeploy.interfaces.report.ReportResponseDto;
import io.bdeploy.interfaces.report.ReportType;
import io.bdeploy.jersey.JerseySecurityContext;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.ReportResource;
import io.bdeploy.ui.report.ProductsInUseReportGenerator;
import io.bdeploy.ui.report.ReportGenerator;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

public class ReportResourceImpl implements ReportResource {

    @Context
    private ContainerRequestContext crq;

    @Context
    private SecurityContext context;

    @Context
    private ResourceContext rc;

    @Inject
    private AuthService auth;

    @Override
    public List<ReportDescriptor> list() {
        return List.of((ReportDescriptor) new ProductsInUseReportDescriptor()).stream()
                .filter(report -> isAuthorized(new ScopedPermission(report.type.name(), Permission.CLIENT))).toList();
    }

    private boolean isAuthorized(ScopedPermission requiredPermission) {
        SecurityContext ctx = crq.getSecurityContext();
        if (!(ctx instanceof JerseySecurityContext)) {
            return false;
        }
        JerseySecurityContext securityContext = (JerseySecurityContext) ctx;

        return securityContext.isAuthorized(requiredPermission)
                || auth.isAuthorized(context.getUserPrincipal().getName(), requiredPermission);
    }

    @Override
    public ReportResponseDto generateReport(String report, ReportRequestDto request) {
        if (!isAuthorized(new ScopedPermission(report, Permission.CLIENT))) {
            return new ReportResponseDto();
        }
        ReportType type = ReportType.valueOf(report);
        checkRequiredParameters(type, request);
        ReportGenerator svc = getReportService(type);
        return svc.generateReport(request);
    }

    private ReportGenerator getReportService(ReportType type) {
        switch (type) {
            case productsInUse:
                return new ProductsInUseReportGenerator(rc.getResource(BHiveRegistry.class),
                        rc.getResource(InstanceGroupResourceImpl.class));
            default:
                throw new WebApplicationException("Unknown report " + type, Status.NOT_FOUND);
        }
    }

    private void checkRequiredParameters(ReportType type, ReportRequestDto request) {
        ReportDescriptor desc = list().stream().filter(d -> d.type.equals(type)).findFirst().orElseThrow();
        List<String> missingRequiredParams = desc.parameters.stream().filter(param -> param.required).map(param -> param.key)
                .filter(paramKey -> request.params.get(paramKey) == null).toList();
        if (!missingRequiredParams.isEmpty()) {
            throw new WebApplicationException("Missing required parameters " + missingRequiredParams, Status.BAD_REQUEST);
        }
    }

}
