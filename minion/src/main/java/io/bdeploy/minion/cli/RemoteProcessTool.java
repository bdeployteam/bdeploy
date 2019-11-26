package io.bdeploy.minion.cli;

import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.cli.RemoteProcessTool.RemoteProcessConfig;

@Help("Deploy to a remote master minion")
@CliName("remote-process")
public class RemoteProcessTool extends RemoteServiceTool<RemoteProcessConfig> {

    private static final String PROCESS_STATUS_FORMAT = "%1$-25s %2$-14s %3$-20s %4$-10s %5$-3s %6$20s %7$-10s %8$5s";

    public @interface RemoteProcessConfig {

        @Help("UUID of the deployment to query/control")
        String uuid();

        @Help("The name of the application to control, controls all applications for the given UUID if missing")
        String application();

        @Help("The name of the instance group to work on")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help(value = "List process status on the remote", arg = false)
        boolean status() default false;

        @Help(value = "Start a named process.", arg = false)
        boolean start() default false;

        @Help(value = "Stop a named process.", arg = false)
        boolean stop() default false;
    }

    public RemoteProcessTool() {
        super(RemoteProcessConfig.class);
    }

    @Override
    protected void run(RemoteProcessConfig config, RemoteService svc) {
        helpAndFailIfMissing(config.uuid(), "Missing --uuid");
        helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");

        if (!config.start() && !config.status() && !config.stop()) {
            helpAndFailIfMissing(null, "Missing --start or --stop or --status");
        }

        MasterRootResource proxy = ResourceProvider.getResource(svc, MasterRootResource.class, null);
        MasterNamedResource master = proxy.getNamedMaster(config.instanceGroup());
        if (config.start() || config.stop()) {
            if (config.start()) {
                master.start(config.uuid(), config.application());
            } else if (config.stop()) {
                master.stop(config.uuid(), config.application());
            }
        }

        InstanceStatusDto status = master.getStatus(config.uuid());
        if (config.application() == null) {
            printAllProcessDetails(status);
        } else {
            ProcessStatusDto appStatus = status.getAppStatus(config.application());
            out().println(appStatus);
        }
    }

    private void printAllProcessDetails(InstanceStatusDto status) {
        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

        out().println(
                String.format(PROCESS_STATUS_FORMAT, "Name", "ID", "Status", "Node", "Tag", "Started At", "OS User", "PID"));
        for (Entry<String, InstanceNodeStatusDto> nodeEntry : status.node2Applications.entrySet()) {
            InstanceNodeStatusDto node = nodeEntry.getValue();
            if (node.activeTag == null) {
                continue;
            }

            String nodeName = nodeEntry.getKey();
            printNodeProcesses(df, node, nodeName);
        }
    }

    private void printNodeProcesses(SimpleDateFormat df, InstanceNodeStatusDto node, String nodeName) {
        Map<String, ProcessStatusDto> allProc = new TreeMap<>();
        allProc.putAll(node.deployed.get(node.activeTag).deployed);
        allProc.putAll(node.runningOrScheduled);

        for (Map.Entry<String, ProcessStatusDto> procEntry : allProc.entrySet()) {
            ProcessStatusDto ps = procEntry.getValue();
            ProcessDetailDto detail = ps.processDetails;

            out().println(String.format(PROCESS_STATUS_FORMAT, ps.appName, ps.appUid, ps.processState.name(), nodeName,
                    node.activeTag, detail == null ? "-" : df.format(detail.startTime), detail == null ? "-" : detail.user,
                    detail == null ? "-" : Long.toString(detail.pid)));

            if (isVerbose() && detail != null) {
                printProcessDetailsRec(detail, "  ");
            }
        }
    }

    private void printProcessDetailsRec(ProcessDetailDto pdd, String indent) {
        out().println(String.format("%1$s [pid=%2$d, cpu=%3$ds] %4$s %5$s", indent, pdd.pid, pdd.totalCpuDuration, pdd.command,
                (pdd.arguments != null && pdd.arguments.length > 0 ? String.join(" ", pdd.arguments) : "")));

        for (ProcessDetailDto child : pdd.children) {
            printProcessDetailsRec(child, indent + "  ");
        }
    }

}
