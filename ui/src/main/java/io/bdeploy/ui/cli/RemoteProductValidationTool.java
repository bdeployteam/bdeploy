package io.bdeploy.ui.cli;

import java.nio.file.Files;
import java.nio.file.Paths;

import io.bdeploy.api.validation.v1.ProductValidationHelper;
import io.bdeploy.api.validation.v1.dto.ProductValidationIssueApi;
import io.bdeploy.api.validation.v1.dto.ProductValidationIssueApi.ProductValidationSeverity;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.ExitCode;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.cli.RemoteProductValidationTool.RemoteProductValidationConfig;

@Help("Validate product config")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-product-validation")
public class RemoteProductValidationTool extends RemoteServiceTool<RemoteProductValidationConfig> {

    public @interface RemoteProductValidationConfig {

        @Help("Descriptor file path")
        String descriptor();
    }

    public RemoteProductValidationTool() {
        super(RemoteProductValidationConfig.class);
    }

    @Override
    protected RenderableResult run(RemoteProductValidationConfig config, RemoteService remote) {
        helpAndFailIfMissing(config.descriptor(), "--descriptor path missing");

        var descriptor = Paths.get(config.descriptor());
        if (!Files.exists(descriptor)) {
            throw new IllegalStateException("File " + descriptor + " does not exist");
        }

        var result = ProductValidationHelper.validate(descriptor, remote);

        if (result.issues == null || result.issues.isEmpty()) {
            return createSuccess();
        }

        DataTable table = createDataTable();
        table.column(new DataTableColumn.Builder("ID").setMinWidth(13).build());
        table.column(new DataTableColumn.Builder("Severity").setMinWidth(8).build());
        table.column(new DataTableColumn.Builder("Message").setMinWidth(20).build());
        for (int i = 0; i < result.issues.size(); ++i) {
            ProductValidationIssueApi issue = result.issues.get(i);
            table.row().cell(i).cell(issue.severity.name()).cell(issue.message).build();

            if (issue.severity == ProductValidationSeverity.ERROR) {
                table.setExitCode(ExitCode.ERROR);
            }
        }
        return table;
    }

}
