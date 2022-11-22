/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.nio.file.Path;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.tea.core.services.TaskingLog;

import io.bdeploy.api.validation.v1.ProductValidationHelper;
import io.bdeploy.api.validation.v1.dto.ProductValidationIssueApi;
import io.bdeploy.api.validation.v1.dto.ProductValidationIssueApi.ProductValidationSeverity;
import io.bdeploy.api.validation.v1.dto.ProductValidationResponseApi;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.tea.plugin.server.BDeployTargetSpec;
import jakarta.ws.rs.core.UriBuilder;

public class BDeployValidateProductTask {

    private final BDeployTargetSpec server;
    private final Path validationYaml;

    public BDeployValidateProductTask(BDeployTargetSpec server, Path validationYaml) {
        this.server = server;
        this.validationYaml = validationYaml;
    }

    @Execute
    public void check(BDeployConfig cfg, TaskingLog log) {
        RemoteService svc = new RemoteService(UriBuilder.fromUri(server.uri).build(), server.token);

        ProductValidationResponseApi result = ProductValidationHelper.validate(validationYaml, svc);
        if (result.issues.isEmpty()) {
            return;
        }

        boolean hasError = false;
        for (ProductValidationIssueApi issue : result.issues) {
            if (issue.severity == ProductValidationSeverity.ERROR) {
                hasError = true;
                log.error("ERROR: " + issue.message);
            } else {
                log.warn("WARNING: " + issue.message);
            }
        }

        if (hasError) {
            throw new IllegalStateException("Validation failed. Aborting.");
        }
    }

}
