package io.bdeploy.jersey.resources;

import java.util.Map;
import java.util.TreeMap;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Counting;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Sampling;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;

import io.bdeploy.common.metrics.Metrics;
import io.bdeploy.common.metrics.Metrics.MetricGroup;

public class JerseyMetricsResourceImpl implements JerseyMetricsResource {

    @Override
    public Map<MetricGroup, MetricBundle> getAllMetrics() {
        Map<MetricGroup, MetricBundle> allBundles = new TreeMap<>();

        for (MetricGroup group : MetricGroup.values()) {
            MetricRegistry reg = Metrics.getMetric(group);
            if (reg != null) {
                allBundles.put(group, getMetricBundle(reg));
            }
        }

        return allBundles;
    }

    private MetricBundle getMetricBundle(MetricRegistry reg) {
        MetricBundle bundle = new MetricBundle();
        for (Map.Entry<String, Metric> entry : reg.getMetrics().entrySet()) {
            Metric m = entry.getValue();
            if (m instanceof Counter) {
                bundle.counters.put(entry.getKey(), readCounter((Counter) m));
            } else if (m instanceof Meter) {
                bundle.meters.put(entry.getKey(), readMeter((Meter) m));
            } else if (m instanceof Timer) {
                bundle.timers.put(entry.getKey(), readTimer((Timer) m));
            } else if (m instanceof Gauge<?>) {
                bundle.gauges.put(entry.getKey(), readGauge((Gauge<?>) m));
            } else if (m instanceof Histogram) {
                bundle.histograms.put(entry.getKey(), readHistogram((Histogram) m));
            }
        }
        return bundle;
    }

    private HistogramMetric readHistogram(Histogram m) {
        HistogramMetric hm = new HistogramMetric();
        updateFields(hm.counter, m);
        updateFields(hm.histogram, m);
        return hm;
    }

    private static <T> GaugeMetric<T> readGauge(Gauge<T> m) {
        GaugeMetric<T> gm = new GaugeMetric<>();
        gm.value = m.getValue();
        return gm;
    }

    private TimerMetric readTimer(Timer m) {
        TimerMetric tm = new TimerMetric();
        updateFields(tm.counter, m);
        updateFields(tm.meter, m);
        updateFields(tm.histogram, m);
        return tm;
    }

    private MeterMetric readMeter(Meter m) {
        MeterMetric mm = new MeterMetric();
        updateFields(mm.counter, m);
        updateFields(mm.meter, m);
        return mm;
    }

    private CounterMetric readCounter(Counter m) {
        CounterMetric cm = new CounterMetric();
        updateFields(cm.counter, m);
        return cm;
    }

    private static void updateFields(CounterFields fields, Counting counting) {
        fields.value = counting.getCount();
    }

    private static void updateFields(MeterFields fields, Metered metered) {
        fields.meanRate = metered.getMeanRate();
        fields.oneMinRate = metered.getOneMinuteRate();
        fields.fiveMinRate = metered.getFiveMinuteRate();
        fields.fifteenMinRate = metered.getFifteenMinuteRate();
    }

    private static void updateFields(HistogramFields fields, Sampling sampled) {
        Snapshot samples = sampled.getSnapshot();
        fields.min = samples.getMin();
        fields.max = samples.getMax();

        fields.mean = samples.getMean();
        fields.stdDev = samples.getStdDev();
        fields.median = samples.getMedian();
        fields.p75th = samples.get75thPercentile();
        fields.p95th = samples.get95thPercentile();
        fields.p98th = samples.get98thPercentile();
        fields.p99th = samples.get99thPercentile();
        fields.p999th = samples.get999thPercentile();

        fields.size = samples.size();
        fields.values = samples.getValues();
    }
}
