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
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.op.ManifestDeleteOldByIdOperation;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionMonitoringDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.minion.MinionRoot;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
public class MinionStatusResourceImpl implements MinionStatusResource {

    private static final Logger log = LoggerFactory.getLogger(MinionStatusResourceImpl.class);

    @Inject
    private MinionRoot root;

    @Inject
    @Named(JerseyServer.START_TIME)
    private Instant startTime;

    @Override
    public MinionStatusDto getStatus() {
        MinionStatusDto s = new MinionStatusDto();
        s.startup = startTime;
        s.config = root.getMinionConfig();
        return s;
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
            PathHelper.deleteRecursive(file);
        }
    }

    @Override
    public List<RemoteDirectoryEntry> getLogEntries() {
        Path logDir = root.getLogDir();
        Path rootDir = root.getRootDir();

        List<RemoteDirectoryEntry> entries = new ArrayList<>();
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

    @Override
    public MinionMonitoringDto getMonitoring(boolean update) {
        OperatingSystemMXBean osMBean = ManagementFactory.getOperatingSystemMXBean();
        com.sun.management.OperatingSystemMXBean extOsMBean = ManagementFactory
                .getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);

        MinionManifest minionManifest = new MinionManifest(root.getHive());
        MinionConfiguration configuration = minionManifest.read();
        MinionDto minion = configuration.getMinion(root.getState().self);

        if (minion != null) {
            long now = System.currentTimeMillis();
            if (minion.monitoring == null) {
                minion.monitoring = new MinionMonitoringDto();
            }

            if (minion.monitoring.loadAvg == null) {
                minion.monitoring.loadAvg = new ArrayList<>();
            }
            if (minion.monitoring.cpuUsage == null) {
                minion.monitoring.cpuUsage = new ArrayList<>();
            }

            // if existing data is older than 5min, assume that it's old data and start a new list
            if ((now - minion.monitoring.timestamp > 1000 * 60 * 5)) {
                minion.monitoring.loadAvg = new ArrayList<>();
                minion.monitoring.cpuUsage = new ArrayList<>();
            }
            minion.monitoring.timestamp = now;
            minion.monitoring.availableProcessors = osMBean.getAvailableProcessors();
            minion.monitoring.loadAvg.add(0, osMBean.getSystemLoadAverage());
            minion.monitoring.cpuUsage.add(0, extOsMBean.getSystemCpuLoad());

            // keep values for 15min
            while (minion.monitoring.loadAvg.size() > 15) {
                minion.monitoring.loadAvg.remove(minion.monitoring.loadAvg.size() - 1);
            }
            while (minion.monitoring.cpuUsage.size() > 15) {
                minion.monitoring.cpuUsage.remove(minion.monitoring.cpuUsage.size() - 1);
            }
            if (update) {
                minionManifest.update(configuration);
                root.getHive().execute(new ManifestDeleteOldByIdOperation().setToDelete(minionManifest.getKey().getName()));
            }
            return minion.monitoring;
        } else {
            log.error("minion {} not found in MinionManifest", root.getState().self);
        }
        return null;
    }

}
