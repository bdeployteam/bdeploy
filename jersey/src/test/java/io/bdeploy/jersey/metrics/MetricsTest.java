package io.bdeploy.jersey.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.codahale.metrics.Timer;

import io.bdeploy.common.metrics.Metrics;
import io.bdeploy.common.metrics.Metrics.MetricGroup;
import io.bdeploy.common.util.Threads;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.TestServer;
import io.bdeploy.jersey.resources.JerseyMetricsResource;
import io.bdeploy.jersey.resources.JerseyMetricsResource.MetricBundle;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

class MetricsTest {

    @RegisterExtension
    TestServer srv = new TestServer(ProducerImpl.class);

    @Path("/metrics-producer")
    @Consumes(MediaType.APPLICATION_JSON)
    public interface Producer {

        @POST
        public void update(String m);
    }

    public static class ProducerImpl implements Producer {

        @Override
        public void update(String m) {
            Metrics.getMetric(MetricGroup.HTTP).counter(m + "-counter").inc();
            Metrics.getMetric(MetricGroup.HTTP).gauge(m + "-gauge", () -> (() -> 42));
            Metrics.getMetric(MetricGroup.HTTP).histogram(m + "-histo").update(1);
            Metrics.getMetric(MetricGroup.HTTP).histogram(m + "-histo").update(2);
            Metrics.getMetric(MetricGroup.HTTP).histogram(m + "-histo").update(3);
            Metrics.getMetric(MetricGroup.HTTP).meter(m + "-meter").mark();
            try (Timer.Context t = Metrics.getMetric(MetricGroup.HTTP).timer(m + "-timer").time()) {
                Threads.sleep(15);
            }
        }
    }

    @Test
    void fetchMetrics(JerseyClientFactory f, Producer producer) {
        producer.update("X");

        // injection not supported for built-in resources.
        JerseyMetricsResource rs = f.getProxyClient(JerseyMetricsResource.class);
        Map<MetricGroup, MetricBundle> r = rs.getAllMetrics();

        assertNotNull(r);
        assertEquals(1, r.get(MetricGroup.HTTP).counters.get("X-counter").counter.value);
        assertEquals(42, r.get(MetricGroup.HTTP).gauges.get("X-gauge").value);
        assertEquals(3, r.get(MetricGroup.HTTP).histograms.get("X-histo").counter.value);
        assertEquals(3, r.get(MetricGroup.HTTP).histograms.get("X-histo").histogram.max);
        assertEquals(1, r.get(MetricGroup.HTTP).histograms.get("X-histo").histogram.min);
        assertEquals(1, r.get(MetricGroup.HTTP).meters.get("X-meter").counter.value);
        assertEquals(1, r.get(MetricGroup.HTTP).timers.get("X-timer").counter.value);
        assertTrue(r.get(MetricGroup.HTTP).timers.get("X-timer").histogram.min > TimeUnit.MILLISECONDS.toNanos(10));
        assertTrue(r.get(MetricGroup.HTTP).timers.get("X-timer").histogram.max > TimeUnit.MILLISECONDS.toNanos(10));
    }

}
