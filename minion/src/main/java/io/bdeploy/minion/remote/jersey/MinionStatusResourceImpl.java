package io.bdeploy.minion.remote.jersey;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.op.FsckOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.minion.MinionMonitoringDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.minion.NodeSynchronizationStatus;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.logging.audit.RollingFileAuditor;
import io.bdeploy.minion.MinionRoot;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

@Singleton
public class MinionStatusResourceImpl implements MinionStatusResource {

    private static final Logger log = LoggerFactory.getLogger(MinionStatusResourceImpl.class);
    private static final ScheduledExecutorService monitorUpdate = Executors.newSingleThreadScheduledExecutor();

    private final MinionMonitoringDto rollingMonitoring = new MinionMonitoringDto();

    @Inject
    private MinionRoot root;

    @Inject
    private BHiveRegistry registry;

    @Inject
    @Named(JerseyServer.START_TIME)
    private Instant startTime;

    public MinionStatusResourceImpl() {
        monitorUpdate.scheduleAtFixedRate(this::updateMonitoringData, 0, 60, TimeUnit.SECONDS);
    }

    @Override
    public MinionStatusDto getStatus() {
        MinionStatusDto s = new MinionStatusDto();
        s.startup = startTime;
        s.config = root.getSelfConfig();
        s.monitoring = rollingMonitoring;
        s.nodeSynchronizationStatus = NodeSynchronizationStatus.UNKNOWN; // minion does not know if it is synchronized with master
        return s;
    }

    @Override
    public long pruneDefaultBHive() {
        LongAdder adder = new LongAdder();
        root.getHive().execute(new PruneOperation()).forEach((k, v) -> adder.add(v));
        return adder.sum();
    }

    @Override
    public Map<String, String> repairDefaultBHive() {
        Map<String, String> result = new TreeMap<>();
        root.getHive().execute(new FsckOperation().setRepair(true))
                .forEach(v -> result.put(v.getElementId().toString(), v.getPathString()));
        return result;
    }

    @Override
    public void setLoggerConfig(Path file) {
        try {
            root.updateLoggingConfiguration(f -> {
                try (InputStream is = Files.newInputStream(file)) {
                    return f.apply(is);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read updated logging configuration", e);
                }
            });
        } finally {
            PathHelper.deleteRecursiveRetry(file);
        }
    }

    @Override
    public List<RemoteDirectoryEntry> getLogEntries(String hive) {
        Path logDir = root.getLogDir();
        List<RemoteDirectoryEntry> entries = new ArrayList<>();

        if (hive != null) {
            try {
                BHive h = registry.get(hive);
                if (h == null) {
                    return entries; // not found = empty.
                }
                RollingFileAuditor a = (RollingFileAuditor) h.getAuditor();
                logDir = a.getLogDir();
            } catch (Exception e) {
                throw new WebApplicationException("Cannot find hive path for: " + hive, e, Status.NOT_ACCEPTABLE);
            }
        }

        Path rootDir = root.getRootDir();
        try (Stream<Path> paths = Files.walk(logDir)) {
            paths.filter(Files::isRegularFile).forEach(f -> {
                File file = f.toFile();
                RemoteDirectoryEntry rde = new RemoteDirectoryEntry();
                rde.path = PathHelper.separatorsToUnix(rootDir.relativize(f));
                rde.lastModified = file.lastModified();
                rde.size = file.length();
                entries.add(rde);
            });
        } catch (IOException e) {
            log.warn("Cannot read log files", e);
        }
        return entries;
    }

    private void updateMonitoringData() {
        OperatingSystemMXBean osMBean = ManagementFactory.getOperatingSystemMXBean();
        com.sun.management.OperatingSystemMXBean extOsMBean = ManagementFactory
                .getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);

        long now = System.currentTimeMillis();

        if (rollingMonitoring.loadAvg == null) {
            rollingMonitoring.loadAvg = new ArrayList<>();
        }
        if (rollingMonitoring.cpuUsage == null) {
            rollingMonitoring.cpuUsage = new ArrayList<>();
        }

        rollingMonitoring.timestamp = now;
        rollingMonitoring.availableProcessors = osMBean.getAvailableProcessors();
        rollingMonitoring.loadAvg.add(0, osMBean.getSystemLoadAverage());
        rollingMonitoring.cpuUsage.add(0, extOsMBean.getCpuLoad());

        // keep values for 15min
        while (rollingMonitoring.loadAvg.size() > 15) {
            rollingMonitoring.loadAvg.remove(rollingMonitoring.loadAvg.size() - 1);
        }
        while (rollingMonitoring.cpuUsage.size() > 15) {
            rollingMonitoring.cpuUsage.remove(rollingMonitoring.cpuUsage.size() - 1);
        }
    }

}
