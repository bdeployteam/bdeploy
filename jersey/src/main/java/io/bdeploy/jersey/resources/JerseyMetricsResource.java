package io.bdeploy.jersey.resources;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.common.metrics.Metrics.MetricGroup;

/**
 * A resource which allows remote access to server metrics.
 */
@Path("/metrics")
@Produces(MediaType.APPLICATION_JSON)
public interface JerseyMetricsResource {

    /**
     * Bundles all metrics in a serialization/JSON friendly way.
     */
    public class MetricBundle {

        public SortedMap<String, CounterMetric> counters = new TreeMap<>();
        public SortedMap<String, HistogramMetric> histograms = new TreeMap<>();
        public SortedMap<String, TimerMetric> timers = new TreeMap<>();
        public SortedMap<String, GaugeMetric<?>> gauges = new TreeMap<>();
        public SortedMap<String, MeterMetric> meters = new TreeMap<>();
    }

    /**
     * A metric counting occurrences.
     */
    public class CounterMetric {

        public CounterFields counter = new CounterFields();
    }

    /**
     * A metric counting values and keeping track of their distribution
     */
    public class HistogramMetric {

        public CounterFields counter = new CounterFields();
        public HistogramFields histogram = new HistogramFields();
    }

    /**
     * Count and measure rates of metric occurrence as well as keeping track of
     * occurrence duration distribution.
     * <p>
     * Note: time is stored in nanoseconds in the histogram.
     */
    public class TimerMetric {

        public CounterFields counter = new CounterFields();
        public MeterFields meter = new MeterFields();
        public HistogramFields histogram = new HistogramFields();
    }

    /**
     * Keeps track of a snapshot of a value
     */
    public class GaugeMetric<T> {

        public T value;
    }

    /**
     * Count and measure rates of metric occurrence.
     */
    public class MeterMetric {

        public CounterFields counter = new CounterFields();
        public MeterFields meter = new MeterFields();
    }

    /**
     * Fields used in metrics which count occurrences
     */
    public class CounterFields {

        public long value;
    }

    /**
     * Fields used in metrics which meter rates
     */
    public class MeterFields {

        public double meanRate;
        public double oneMinRate;
        public double fiveMinRate;
        public double fifteenMinRate;
    }

    /**
     * Fields used in metrics which keep track of value distribution
     */
    public class HistogramFields {

        public long min;
        public long max;
        public double mean;
        public double stdDev;
        public double median;
        public double p75th;
        public double p95th;
        public double p98th;
        public double p99th;
        public double p999th;

        public long size;
        public long[] values;
    }

    /**
     * @return all currently available metrics
     */
    @GET
    public Map<MetricGroup, MetricBundle> getAllMetrics();

}
