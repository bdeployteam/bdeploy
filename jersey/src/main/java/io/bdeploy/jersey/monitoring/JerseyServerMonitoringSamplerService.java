package io.bdeploy.jersey.monitoring;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.EvictingQueue;

import io.bdeploy.common.util.NamedDaemonThreadFactory;

public class JerseyServerMonitoringSamplerService {

    private static final int MAX_SNAPSHOTS = 1 * 60; // every minute, 60 samples

    private final ScheduledExecutorService sampler;
    private final EvictingQueue<JerseyServerMonitoringSnapshot> snapshots = EvictingQueue.create(MAX_SNAPSHOTS);

    private final JerseyServerMonitor monitor;

    public JerseyServerMonitoringSamplerService(JerseyServerMonitor monitor) {
        this.monitor = monitor;
        sampler = Executors.newSingleThreadScheduledExecutor(new NamedDaemonThreadFactory("Monitoring Sampler"));
        sampler.scheduleAtFixedRate(this::performSnapshot, 0, 1, TimeUnit.MINUTES);
    }

    private void performSnapshot() {
        if (monitor != null) {
            snapshots.add(monitor.getSnapshot());
        }
    }

    public JerseyServerMonitoringDto getSamples() {
        JerseyServerMonitoringDto dto = new JerseyServerMonitoringDto();
        dto.snapshots = new ArrayList<>(snapshots);
        return dto;
    }

}
