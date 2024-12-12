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
import io.bdeploy.ui.api.ReportParameterOptionResource;
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

    @Inject
    private BHiveRegistry registry;

    @Override
    public List<ReportDescriptor> list() {
        return List.of((ReportDescriptor) new ProductsInUseReportDescriptor()).stream()
                .filter(report -> isAuthorized(report.type.name())).toList();
    }

    private boolean isAuthorized(String report) {
        SecurityContext ctx = crq.getSecurityContext();
        if (!(ctx instanceof JerseySecurityContext)) {
            return false;
        }
        JerseySecurityContext securityContext = (JerseySecurityContext) ctx;

        ScopedPermission requiredPermission = new ScopedPermission(report, Permission.READ);
        return securityContext.isAuthorized(requiredPermission)
                || auth.isAuthorized(context.getUserPrincipal().getName(), requiredPermission);
    }

    @Override
    public ReportResponseDto generateReport(String report, ReportRequestDto request) {
        ReportType type = ReportType.valueOf(report);
        ReportGenerator svc = getReportService(type);
        return svc.generateReport(request);
    }

    private ReportGenerator getReportService(ReportType type) {
        if (type == ReportType.productsInUse) {
            return new ProductsInUseReportGenerator(registry, rc.getResource(InstanceGroupResourceImpl.class));
        }
        throw new WebApplicationException("Unknown report " + type, Status.NOT_FOUND);
    }

    @Override
    public ReportParameterOptionResource getReportParameterOptionResource(String report) {
        return rc.initResource(new ReportParameterOptionResourceImpl(ReportType.valueOf(report)));
    }

}
