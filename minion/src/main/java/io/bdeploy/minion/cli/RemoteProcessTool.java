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
import io.bdeploy.interfaces.configuration.pcu.ProcessHandleDto;
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
        String instanceId = config.uuid();
        helpAndFailIfMissing(instanceId, "Missing --uuid");

        String groupName = config.instanceGroup();
        helpAndFailIfMissing(groupName, "Missing --instanceGroup");

        if (!config.start() && !config.status() && !config.stop()) {
            helpAndFailIfMissing(null, "Missing --start or --stop or --status");
        }

        MasterRootResource proxy = ResourceProvider.getResource(svc, MasterRootResource.class, null);
        MasterNamedResource master = proxy.getNamedMaster(groupName);
        String appId = config.application();
        if (config.start() || config.stop()) {
            if (config.start()) {
                if (appId == null || appId.isEmpty()) {
                    master.start(instanceId);
                } else {
                    master.start(instanceId, appId);
                }
            } else if (config.stop()) {
                if (appId == null || appId.isEmpty()) {
                    master.stop(instanceId);
                } else {
                    master.stop(instanceId, appId);
                }
            }
        }

        InstanceStatusDto status = master.getStatus(instanceId);
        if (appId == null) {
            printAllProcessDetails(master, status);
        } else {
            ProcessStatusDto appStatus = status.getAppStatus(appId);
            out().println(appStatus);
        }
    }

    private void printAllProcessDetails(MasterNamedResource master, InstanceStatusDto status) {
        out().println(
                String.format(PROCESS_STATUS_FORMAT, "Name", "ID", "Status", "Node", "Tag", "Started At", "OS User", "PID"));
        for (Entry<String, InstanceNodeStatusDto> nodeEntry : status.node2Applications.entrySet()) {
            InstanceNodeStatusDto node = nodeEntry.getValue();
            if (node.activeTag == null) {
                continue;
            }

            String nodeName = nodeEntry.getKey();
            printNodeProcesses(master, status.getInstanceId(), node, nodeName);
        }
    }

    private void printNodeProcesses(MasterNamedResource master, String instanceId, InstanceNodeStatusDto node, String nodeName) {
        Map<String, ProcessStatusDto> allProc = new TreeMap<>();
        allProc.putAll(node.deployed.get(node.activeTag).deployed);
        allProc.putAll(node.runningOrScheduled);

        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        for (Map.Entry<String, ProcessStatusDto> procEntry : allProc.entrySet()) {
            ProcessStatusDto ps = procEntry.getValue();

            ProcessDetailDto detail = master.getProcessDetails(instanceId, ps.appUid);
            ProcessHandleDto handle = detail.handle;

            out().println(String.format(PROCESS_STATUS_FORMAT, ps.appName, ps.appUid, ps.processState.name(), nodeName,
                    node.activeTag, handle == null ? "-" : df.format(handle.startTime), handle == null ? "-" : handle.user,
                    handle == null ? "-" : Long.toString(handle.pid)));

            if (isVerbose() && handle != null) {
                printProcessDetailsRec(handle, "  ");
            }
        }
    }

    private void printProcessDetailsRec(ProcessHandleDto pdd, String indent) {
        out().println(String.format("%1$s [pid=%2$d, cpu=%3$ds] %4$s %5$s", indent, pdd.pid, pdd.totalCpuDuration, pdd.command,
                (pdd.arguments != null && pdd.arguments.length > 0 ? String.join(" ", pdd.arguments) : "")));

        for (ProcessHandleDto child : pdd.children) {
            printProcessDetailsRec(child, indent + "  ");
        }
    }

}
