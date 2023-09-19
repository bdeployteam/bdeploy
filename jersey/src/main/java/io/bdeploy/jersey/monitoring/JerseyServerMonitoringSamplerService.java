package io.bdeploy.jersey.monitoring;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.EvictingQueue;

import io.bdeploy.common.util.NamedDaemonThreadFactory;
import jakarta.inject.Singleton;

@Service
@Singleton
public class JerseyServerMonitoringSamplerService {

    private static final int MAX_SNAPSHOTS = 1 * 60; // every minute, 60 samples
    private static final Logger log = LoggerFactory.getLogger(JerseyServerMonitoringSamplerService.class);

    private final ScheduledExecutorService sampler;
    private final EvictingQueue<JerseyServerMonitoringSnapshot> snapshots = EvictingQueue.create(MAX_SNAPSHOTS);

    private final JerseyServerMonitor monitor;

    public JerseyServerMonitoringSamplerService(JerseyServerMonitor monitor) {
        this.monitor = monitor;
        sampler = Executors.newSingleThreadScheduledExecutor(new NamedDaemonThreadFactory("Monitoring Sampler"));
        sampler.scheduleAtFixedRate(this::performSnapshot, 5, 60, TimeUnit.SECONDS);
    }

    private void performSnapshot() {
        if (monitor != null) {
            try {
                snapshots.add(monitor.getSnapshot());
            } catch (Exception e) {
                log.warn("Cannot fetch server monitoring data");
                if (log.isDebugEnabled()) {
                    log.debug("Exception", e);
                }
            }
        }
    }

    public JerseyServerMonitoringDto getSamples() {
        JerseyServerMonitoringDto dto = new JerseyServerMonitoringDto();
        dto.snapshots = new ArrayList<>(snapshots);
        return dto;
    }

}
