package io.bdeploy.ui.cli;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.DataTableRowBuilder;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.report.ReportColumnDescriptor;
import io.bdeploy.interfaces.report.ReportDescriptor;
import io.bdeploy.interfaces.report.ReportParameterDescriptor;
import io.bdeploy.interfaces.report.ReportParameterInputType;
import io.bdeploy.interfaces.report.ReportRequestDto;
import io.bdeploy.interfaces.report.ReportResponseDto;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.ReportResource;
import io.bdeploy.ui.cli.RemoteReportTool.RemoteReportConfig;

@Help("View reports")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-report")
public class RemoteReportTool extends RemoteServiceTool<RemoteReportConfig> {

    public @interface RemoteReportConfig {

        @Help(value = "List all reports", arg = false)
        boolean list() default false;

        @Help("View report by the given report ID")
        String report();

        @Help(value = "List all parameters for the given report", arg = false)
        boolean paramHelp() default false;

        @Help("Parameters required for the report")
        String[] params();
    }

    public RemoteReportTool() {
        super(RemoteReportConfig.class);
    }

    @Override
    protected RenderableResult run(RemoteReportConfig config, RemoteService remote) {
        ReportResource rr = ResourceProvider.getVersionedResource(remote, ReportResource.class, getLocalContext());
        if (config.list()) {
            return doList(rr);
        } else if (config.paramHelp()) {
            return doHelpParam(config, rr);
        } else if (config.report() != null) {
            return doViewReport(config, rr);
        }
        return createNoOp();
    }

    private RenderableResult doHelpParam(RemoteReportConfig config, ReportResource rr) {
        helpAndFailIfMissing(config.report(), "--report missing");
        ReportDescriptor desc = rr.list().stream().filter(d -> d.type.name().equals(config.report())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("ERROR: report of type " + config.report() + " is not found"));

        DataTable table = createDataTable();
        table.setCaption("Parameters for report: " + desc.name);
        table.setLineWrapHint(true).setIndentHint(2);
        table.column(new DataTableColumn.Builder("Argument").setScaleToContent(true).build());
        table.column(new DataTableColumn.Builder("Description").setMinWidth(0).build());
        for (ReportParameterDescriptor param : desc.parameters) {
            table.row().cell(param.key + (param.inputType == ReportParameterInputType.CHECKBOX ? "" : "=ARG"))
                    .cell(param.description).build();

        }
        return table;
    }

    private RenderableResult doViewReport(RemoteReportConfig config, ReportResource rr) {
        ReportDescriptor desc = rr.list().stream().filter(d -> d.type.name().equals(config.report())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("ERROR: report of type " + config.report() + " is not found"));

        ReportRequestDto req = new ReportRequestDto();
        req.params = parseParams(config);

        ReportResponseDto resp = rr.generateReport(config.report(), req);

        DataTable table = createDataTable();
        table.setCaption("Report \"" + desc.name + "\" - generated at: "
                + new SimpleDateFormat("dd.MM.yyyy HH:mm").format(resp.generatedAt));
        for (ReportColumnDescriptor column : desc.columns) {
            table.column(new DataTableColumn.Builder(column.name).setMinWidth(column.minWidth)
                    .setScaleToContent(column.scaleToContent).build());
        }
        for (Map<String, String> row : resp.rows) {
            DataTableRowBuilder tableRow = table.row();
            for (ReportColumnDescriptor column : desc.columns) {
                tableRow.cell(row.get(column.key));
            }
            tableRow.build();
        }
        return table;
    }

    private static Map<String, String> parseParams(RemoteReportConfig config) {
        Map<String, String> params = new HashMap<>();
        if (config.params() != null) {
            for (String param : config.params()) {
                int idx = param.indexOf("=");
                if (idx >= 0) {
                    params.put(param.substring(0, idx), param.substring(idx + 1));
                } else {
                    params.put(param, "true");
                }
            }
        }
        return params;
    }

    private RenderableResult doList(ReportResource rr) {
        DataTable table = createDataTable();
        table.column(new DataTableColumn.Builder("Name").setScaleToContent(true).build());
        table.column(new DataTableColumn.Builder("Type").setScaleToContent(true).build());
        table.column(new DataTableColumn.Builder("Description").setMinWidth(0).build());
        for (ReportDescriptor desc : rr.list()) {
            table.row().cell(desc.name).cell(desc.type).cell(desc.description).build();
        }
        return table;
    }
}
