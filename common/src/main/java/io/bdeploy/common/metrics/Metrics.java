package io.bdeploy.common.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;

/**
 * Provides access to global {@link MetricRegistry}s by use case.
 */
public class Metrics {

    public enum MetricGroup {
        HTTP,
        CLI,
        HIVE
    }

    private Metrics() {
    }

    /**
     * @param group the {@link MetricGroup} to get a {@link MetricRegistry} for.
     * @return a {@link MetricRegistry} which can be used to create and track metrics.
     */
    public static MetricRegistry getMetric(MetricGroup group) {
        return SharedMetricRegistries.getOrCreate(group.name());
    }

}
